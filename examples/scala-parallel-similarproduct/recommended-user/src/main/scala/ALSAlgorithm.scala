package org.template.recommendeduser

import grizzled.slf4j.Logger
import org.apache.predictionio.controller.{P2LAlgorithm, Params}
import org.apache.predictionio.data.storage.BiMap
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.mllib.recommendation.{ALS, Rating => MLlibRating}

import scala.collection.mutable

case class ALSAlgorithmParams(
  rank: Int,
  numIterations: Int,
  lambda: Double,
  seed: Option[Long]) extends Params

class ALSModel(
  val similarUserFeatures: Map[Int, Array[Double]],
  val similarUserStringIntMap: BiMap[String, Int],
  val similarUsers: Map[Int, User]
) extends Serializable {

  @transient lazy val similarUserIntStringMap = similarUserStringIntMap.inverse

  override def toString = {
    s" similarUserFeatures: [${similarUserFeatures.size}]" +
    s"(${similarUserFeatures.take(2).toList}...)" +
    s" similarUserStringIntMap: [${similarUserStringIntMap.size}]" +
    s"(${similarUserStringIntMap.take(2).toString()}...)]" +
    s" users: [${similarUsers.size}]" +
    s"(${similarUsers.take(2).toString()}...)]"
  }
}

/**
  * Use ALS to build user x feature matrix
  */
class ALSAlgorithm(val ap: ALSAlgorithmParams)
  extends P2LAlgorithm[PreparedData, ALSModel, Query, PredictedResult] {

  @transient lazy val logger = Logger[this.type]

  def train(sc: SparkContext, data: PreparedData): ALSModel = {
    require(data.followEvents.take(1).nonEmpty,
      s"followEvents in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")
    require(data.users.take(1).nonEmpty,
      s"users in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")
    // create User String ID to integer index BiMap
    val userStringIntMap = BiMap.stringInt(data.users.keys)
    val similarUserStringIntMap = userStringIntMap

    // collect SimilarUser as Map and convert ID to Int index
    val similarUsers: Map[Int, User] = data.users.map { case (id, similarUser) =>
      (similarUserStringIntMap(id), similarUser)
    }.collectAsMap().toMap

    val mllibRatings = data.followEvents
      .map { r =>
        // Convert user and user String IDs to Int index for MLlib
        val uindex = userStringIntMap.getOrElse(r.user, -1)
        val iindex = similarUserStringIntMap.getOrElse(r.followedUser, -1)

        if (uindex == -1)
          logger.info(s"Couldn't convert nonexistent user ID ${r.user}"
            + " to Int index.")

        if (iindex == -1)
          logger.info(s"Couldn't convert nonexistent followedUser ID ${r.followedUser}"
            + " to Int index.")

        ((uindex, iindex), 1)
      }.filter { case ((u, i), v) =>
        // keep events with valid user and user index
        (u != -1) && (i != -1)
      }
      .map { case ((u, i), v) =>
        // MLlibRating requires integer index for user and user
        MLlibRating(u, i, v)
      }
      .cache()

    // MLLib ALS cannot handle empty training data.
    require(mllibRatings.take(1).nonEmpty,
      s"mllibRatings cannot be empty." +
      " Please check if your events contain valid user and followedUser ID.")

    // seed for MLlib ALS
    val seed = ap.seed.getOrElse(System.nanoTime)

    val m = ALS.trainImplicit(
      ratings = mllibRatings,
      rank = ap.rank,
      iterations = ap.numIterations,
      lambda = ap.lambda,
      blocks = -1,
      alpha = 1.0,
      seed = seed)

    new ALSModel(
      similarUserFeatures = m.productFeatures.collectAsMap().toMap,
      similarUserStringIntMap = similarUserStringIntMap,
      similarUsers = similarUsers
    )
  }

  def predict(model: ALSModel, query: Query): PredictedResult = {

    val similarUserFeatures = model.similarUserFeatures

    // convert similarUsers to Int index
    val queryList: Set[Int] = query.users.map(model.similarUserStringIntMap.get)
      .flatten.toSet

    val queryFeatures: Vector[Array[Double]] = queryList.toVector
      // similarUserFeatures may not contain the requested user
      .map { similarUser => similarUserFeatures.get(similarUser) }
      .flatten

    val whiteList: Option[Set[Int]] = query.whiteList.map( set =>
      set.map(model.similarUserStringIntMap.get).flatten
    )
    val blackList: Option[Set[Int]] = query.blackList.map ( set =>
      set.map(model.similarUserStringIntMap.get).flatten
    )

    val ord = Ordering.by[(Int, Double), Double](_._2).reverse

    val indexScores: Array[(Int, Double)] = if (queryFeatures.isEmpty) {
      logger.info(s"No similarUserFeatures vector for query users ${query.users}.")
      Array[(Int, Double)]()
    } else {
      similarUserFeatures.par // convert to parallel collection
        .mapValues { f =>
          queryFeatures.map { qf =>
            cosine(qf, f)
          }.sum
        }
        .filter(_._2 > 0) // keep similarUsers with score > 0
        .seq // convert back to sequential collection
        .toArray
    }

    val filteredScore = indexScores.view.filter { case (i, v) =>
      isCandidateSimilarUser(
        i = i,
        similarUsers = model.similarUsers,
        queryList = queryList,
        whiteList = whiteList,
        blackList = blackList
      )
    }

    val topScores = getTopN(filteredScore, query.num)(ord).toArray

    val similarUserScores = topScores.map { case (i, s) =>
      new similarUserScore(
        user = model.similarUserIntStringMap(i),
        score = s
      )
    }

    new PredictedResult(similarUserScores)
  }

  private
  def getTopN[T](s: Seq[T], n: Int)(implicit ord: Ordering[T]): Seq[T] = {
    val q = mutable.PriorityQueue()

    for (x <- s) {
      if (q.size < n)
        q.enqueue(x)
      else {
        // q is full
        if (ord.compare(x, q.head) < 0) {
          q.dequeue()
          q.enqueue(x)
        }
      }
    }

    q.dequeueAll.toSeq.reverse
  }

  private
  def cosine(v1: Array[Double], v2: Array[Double]): Double = {
    val size = v1.length
    var i = 0
    var n1: Double = 0
    var n2: Double = 0
    var d: Double = 0
    while (i < size) {
      n1 += v1(i) * v1(i)
      n2 += v2(i) * v2(i)
      d += v1(i) * v2(i)
      i += 1
    }
    val n1n2 = math.sqrt(n1) * math.sqrt(n2)
    if (n1n2 == 0) 0 else d / n1n2
  }

  private
  def isCandidateSimilarUser(
    i: Int,
    similarUsers: Map[Int, User],
    queryList: Set[Int],
    whiteList: Option[Set[Int]],
    blackList: Option[Set[Int]]
  ): Boolean = {
    whiteList.map(_.contains(i)).getOrElse(true) &&
    blackList.map(!_.contains(i)).getOrElse(true) &&
    // discard similarUsers in query as well
    (!queryList.contains(i))
  }

}
