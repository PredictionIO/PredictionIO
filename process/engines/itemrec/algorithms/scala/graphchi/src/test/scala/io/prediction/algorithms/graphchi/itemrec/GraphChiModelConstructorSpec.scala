package io.prediction.algorithms.graphchi.itemrec

import io.prediction.commons.Config
import io.prediction.commons.settings.{ App, Algo }
import io.prediction.commons.modeldata.{ ItemRecScore }

import org.specs2.mutable._
import com.github.nscala_time.time.Imports._
import scala.io.Source
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter

import com.mongodb.casbah.Imports._

class GraphChiModelConstructorSpec extends Specification {

  // note: should match the db name defined in the application.conf
  val mongoDbName = "predictionio_modeldata_graphchi_dataprep_test"
  def cleanUp() = {
    // remove the test database
    MongoConnection()(mongoDbName).dropDatabase()
  }

  val commonConfig = new Config
  val modeldataItemRecScores = commonConfig.getModeldataItemRecScores

  // NOTE: use HALF_UP mode to avoid error caused by rounding when compare data
  // (eg. 3.5 vs 3.499999999999).
  // (eg. 0.6666666666 vs 0.666666667)
  def roundUpScores(irec: ItemRecScore): ItemRecScore = {
    irec.copy(
      scores = irec.scores.map { x =>
        BigDecimal(x).setScale(9, BigDecimal.RoundingMode.HALF_UP).toDouble
      }
    )
  }

  def argMapToArray(args: Map[String, Any]): Array[String] = {
    args.toArray.flatMap {
      case (k, v) =>
        Array(s"--${k}", v.toString)
    }
  }

  "GraphChiModelConstructor" should {

    val inputDir = "/tmp/pio_test/"

    val inputDirFile = new File(inputDir)
    inputDirFile.mkdirs()

    val usersIndex = List(
      "1\tu1",
      "2\tu2",
      "3\tu3")

    val itemsIndex = List(
      "1\ti1\tt1,t2",
      "2\ti2\tt1",
      "3\ti3\tt2,t3",
      "4\ti4\tt3"
    )

    val ratingsMM = List(
      "%%MatrixMarket matrix coordinate real general",
      "3 4 9",
      "1 1 3",
      "1 2 4",
      "1 3 1",
      "2 1 2",
      "2 2 1",
      "2 4 3",
      "3 2 5",
      "3 3 1",
      "3 4 4"
    )

    /*
     * 1.2 2.4 1.1
     * 4.3 1.1 2.4
     */
    val ratingsUMM = List(
      "%%MatrixMarket matrix array real general",
      "%This file contains ALS output matrix U. In each row D factors of a single user node.",
      "3 2",
      "1.2",
      "4.3",
      "2.4",
      "1.1",
      "1.1",
      "2.4"
    )

    /*
     * 2.1 3.1 2.6 1.9
     * 1.5 1.2 1.3 2.0
     */
    val ratingsVMM = List(
      "%%MatrixMarket matrix array real general",
      "%This file contains ALS output matrix V. In each row D factors of a single item node.",
      "4 2",
      "2.1",
      "1.5",
      "3.1",
      "1.2",
      "2.6",
      "1.3",
      "1.9",
      "2.0"
    )

    /* Ut V:
     * 8.97 8.88  8.71  10.88
     * 6.69 8.76  7.67  6.76
     * 5.91 6.29  5.98  6.89
     */

    def writeToFile(lines: List[String], filePath: String) = {
      val writer = new BufferedWriter(new FileWriter(new File(filePath)))
      lines.foreach { line =>
        writer.write(s"${line}\n")
      }
      writer.close()
    }

    writeToFile(usersIndex, s"${inputDir}usersIndex.tsv")
    writeToFile(itemsIndex, s"${inputDir}itemsIndex.tsv")
    writeToFile(ratingsMM, s"${inputDir}ratings.mm")
    writeToFile(ratingsUMM, s"${inputDir}ratings.mm_U.mm")
    writeToFile(ratingsVMM, s"${inputDir}ratings.mm_V.mm")

    val appid = 24

    implicit val app = App(
      id = appid,
      userid = 0,
      appkey = "1234",
      display = "12345",
      url = None,
      cat = None,
      desc = None,
      timezone = "UTC"
    )

    "correctly writes ItemRecScores with larger numRecommendations" in {

      val algoid = 25
      val modelSet = false

      implicit val algo = Algo(
        id = algoid,
        engineid = 1234,
        name = "",
        infoid = "abc",
        command = "",
        params = Map(),
        settings = Map(),
        modelset = modelSet,
        createtime = DateTime.now,
        updatetime = DateTime.now,
        status = "deployed",
        offlineevalid = None,
        offlinetuneid = None,
        loop = None,
        paramset = None
      )

      val args = Map(
        "inputDir" -> inputDir,
        "appid" -> appid,
        "algoid" -> algoid,
        "modelSet" -> modelSet,
        "unseenOnly" -> false,
        "numRecommendations" -> 5
      )

      val u1Expected = ItemRecScore(
        uid = "u1",
        iids = Seq("i4", "i1", "i2", "i3"),
        scores = Seq(10.88, 8.97, 8.88, 8.71),
        itypes = Seq(Seq("t3"), Seq("t1", "t2"), Seq("t1"), Seq("t2", "t3")),
        appid = appid,
        algoid = algoid,
        modelset = modelSet)

      val u2Expected = ItemRecScore(
        uid = "u2",
        iids = Seq("i2", "i3", "i4", "i1"),
        scores = Seq(8.76, 7.67, 6.76, 6.69),
        itypes = Seq(Seq("t1"), Seq("t2", "t3"), Seq("t3"), Seq("t1", "t2")),
        appid = appid,
        algoid = algoid,
        modelset = modelSet)

      val u3Expected = ItemRecScore(
        uid = "u3",
        iids = Seq("i4", "i2", "i3", "i1"),
        scores = Seq(6.89, 6.29, 5.98, 5.91),
        itypes = Seq(Seq("t3"), Seq("t1"), Seq("t2", "t3"), Seq("t1", "t2")),
        appid = appid,
        algoid = algoid,
        modelset = modelSet)

      GraphChiModelConstructor.main(argMapToArray(args))

      val u1ItemRec = modeldataItemRecScores.getByUid("u1")
      val u2ItemRec = modeldataItemRecScores.getByUid("u2")
      val u3ItemRec = modeldataItemRecScores.getByUid("u3")

      // don't check id
      u1ItemRec.map(roundUpScores(_).copy(id = None)) must beSome(roundUpScores(u1Expected)) and
        (u2ItemRec.map(roundUpScores(_).copy(id = None)) must beSome(roundUpScores(u2Expected))) and
        (u3ItemRec.map(roundUpScores(_).copy(id = None)) must beSome(roundUpScores(u3Expected)))

    }

    "correctly writes ItemRecScores with smaller numRecommendations" in {

      val algoid = 26
      val modelSet = true

      implicit val algo = Algo(
        id = algoid,
        engineid = 1234,
        name = "",
        infoid = "abc",
        command = "",
        params = Map(),
        settings = Map(),
        modelset = modelSet,
        createtime = DateTime.now,
        updatetime = DateTime.now,
        status = "deployed",
        offlineevalid = None,
        offlinetuneid = None,
        loop = None,
        paramset = None
      )

      val args = Map(
        "inputDir" -> inputDir,
        "appid" -> appid,
        "algoid" -> algoid,
        "modelSet" -> modelSet,
        "unseenOnly" -> false,
        "numRecommendations" -> 2
      )

      val u1Expected = ItemRecScore(
        uid = "u1",
        iids = Seq("i4", "i1"),
        scores = Seq(10.88, 8.97),
        itypes = Seq(Seq("t3"), Seq("t1", "t2")),
        appid = appid,
        algoid = algoid,
        modelset = modelSet)

      val u2Expected = ItemRecScore(
        uid = "u2",
        iids = Seq("i2", "i3"),
        scores = Seq(8.76, 7.67),
        itypes = Seq(Seq("t1"), Seq("t2", "t3")),
        appid = appid,
        algoid = algoid,
        modelset = modelSet)

      val u3Expected = ItemRecScore(
        uid = "u3",
        iids = Seq("i4", "i2"),
        scores = Seq(6.89, 6.29),
        itypes = Seq(Seq("t3"), Seq("t1")),
        appid = appid,
        algoid = algoid,
        modelset = modelSet)

      GraphChiModelConstructor.main(argMapToArray(args))

      val u1ItemRec = modeldataItemRecScores.getByUid("u1")
      val u2ItemRec = modeldataItemRecScores.getByUid("u2")
      val u3ItemRec = modeldataItemRecScores.getByUid("u3")

      // don't check id
      u1ItemRec.map(roundUpScores(_).copy(id = None)) must beSome(roundUpScores(u1Expected)) and
        (u2ItemRec.map(roundUpScores(_).copy(id = None)) must beSome(roundUpScores(u2Expected))) and
        (u3ItemRec.map(roundUpScores(_).copy(id = None)) must beSome(roundUpScores(u3Expected)))

    }

    "correctly writes ItemRecScores with subset itemsIndex.tsv" in {

      val inputDir = "/tmp/pio_test/subset/"

      val inputDirFile = new File(inputDir)
      inputDirFile.mkdirs()

      val itemsIndex = List(
        "1\ti1\tt1,t2",
        "3\ti3\tt2,t3",
        "4\ti4\tt3"
      )

      writeToFile(usersIndex, s"${inputDir}usersIndex.tsv")
      writeToFile(itemsIndex, s"${inputDir}itemsIndex.tsv")
      writeToFile(ratingsMM, s"${inputDir}ratings.mm")
      writeToFile(ratingsUMM, s"${inputDir}ratings.mm_U.mm")
      writeToFile(ratingsVMM, s"${inputDir}ratings.mm_V.mm")

      val algoid = 27
      val modelSet = false

      implicit val algo = Algo(
        id = algoid,
        engineid = 1234,
        name = "",
        infoid = "abc",
        command = "",
        params = Map(),
        settings = Map(),
        modelset = modelSet,
        createtime = DateTime.now,
        updatetime = DateTime.now,
        status = "deployed",
        offlineevalid = None,
        offlinetuneid = None,
        loop = None,
        paramset = None
      )

      val args = Map(
        "inputDir" -> inputDir,
        "appid" -> appid,
        "algoid" -> algoid,
        "modelSet" -> modelSet,
        "unseenOnly" -> false,
        "numRecommendations" -> 5
      )

      val u1Expected = ItemRecScore(
        uid = "u1",
        iids = Seq("i4", "i1", "i3"),
        scores = Seq(10.88, 8.97, 8.71),
        itypes = Seq(Seq("t3"), Seq("t1", "t2"), Seq("t2", "t3")),
        appid = appid,
        algoid = algoid,
        modelset = modelSet)

      val u2Expected = ItemRecScore(
        uid = "u2",
        iids = Seq("i3", "i4", "i1"),
        scores = Seq(7.67, 6.76, 6.69),
        itypes = Seq(Seq("t2", "t3"), Seq("t3"), Seq("t1", "t2")),
        appid = appid,
        algoid = algoid,
        modelset = modelSet)

      val u3Expected = ItemRecScore(
        uid = "u3",
        iids = Seq("i4", "i3", "i1"),
        scores = Seq(6.89, 5.98, 5.91),
        itypes = Seq(Seq("t3"), Seq("t2", "t3"), Seq("t1", "t2")),
        appid = appid,
        algoid = algoid,
        modelset = modelSet)

      GraphChiModelConstructor.main(argMapToArray(args))

      val u1ItemRec = modeldataItemRecScores.getByUid("u1")
      val u2ItemRec = modeldataItemRecScores.getByUid("u2")
      val u3ItemRec = modeldataItemRecScores.getByUid("u3")

      // don't check id
      u1ItemRec.map(roundUpScores(_).copy(id = None)) must beSome(roundUpScores(u1Expected)) and
        (u2ItemRec.map(roundUpScores(_).copy(id = None)) must beSome(roundUpScores(u2Expected))) and
        (u3ItemRec.map(roundUpScores(_).copy(id = None)) must beSome(roundUpScores(u3Expected)))

    }

    "correctly writes ItemRecScores with unseenOnly=true" in {

      val algoid = 28
      val modelSet = true

      implicit val algo = Algo(
        id = algoid,
        engineid = 1234,
        name = "",
        infoid = "abc",
        command = "",
        params = Map(),
        settings = Map(),
        modelset = modelSet,
        createtime = DateTime.now,
        updatetime = DateTime.now,
        status = "deployed",
        offlineevalid = None,
        offlinetuneid = None,
        loop = None,
        paramset = None
      )

      val args = Map(
        "inputDir" -> inputDir,
        "appid" -> appid,
        "algoid" -> algoid,
        "modelSet" -> modelSet,
        "unseenOnly" -> true,
        "numRecommendations" -> 4
      )

      val u1Expected = ItemRecScore(
        uid = "u1",
        iids = Seq("i4"),
        scores = Seq(10.88),
        itypes = Seq(Seq("t3")),
        appid = appid,
        algoid = algoid,
        modelset = modelSet)

      val u2Expected = ItemRecScore(
        uid = "u2",
        iids = Seq("i3"),
        scores = Seq(7.67),
        itypes = Seq(Seq("t2", "t3")),
        appid = appid,
        algoid = algoid,
        modelset = modelSet)

      val u3Expected = ItemRecScore(
        uid = "u3",
        iids = Seq("i1"),
        scores = Seq(5.91),
        itypes = Seq(Seq("t1", "t2")),
        appid = appid,
        algoid = algoid,
        modelset = modelSet)

      GraphChiModelConstructor.main(argMapToArray(args))

      val u1ItemRec = modeldataItemRecScores.getByUid("u1")
      val u2ItemRec = modeldataItemRecScores.getByUid("u2")
      val u3ItemRec = modeldataItemRecScores.getByUid("u3")

      // don't check id
      u1ItemRec.map(roundUpScores(_).copy(id = None)) must beSome(roundUpScores(u1Expected)) and
        (u2ItemRec.map(roundUpScores(_).copy(id = None)) must beSome(roundUpScores(u2Expected))) and
        (u3ItemRec.map(roundUpScores(_).copy(id = None)) must beSome(roundUpScores(u3Expected)))

    }

  }

  // TODO: test evalid != None

  // clean up when finish test
  step(cleanUp())
}