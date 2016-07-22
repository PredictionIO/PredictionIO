package org.template.similarproduct

import org.apache.predictionio.controller.PAlgorithm
import org.apache.predictionio.controller.Params
import org.apache.predictionio.controller.IPersistentModel
import org.apache.predictionio.controller.IPersistentModelLoader
import org.apache.predictionio.data.storage.BiMap

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.mllib.linalg.SparseVector
import org.apache.spark.mllib.linalg.distributed.MatrixEntry
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.mllib.linalg.distributed.CoordinateMatrix

import grizzled.slf4j.Logger

import scala.collection.mutable.PriorityQueue

case class DIMSUMAlgorithmParams(threshold: Double) extends Params

class DIMSUMModel(
    val similarities: RDD[(Int, SparseVector)],
    val itemStringIntMap: BiMap[String, Int],
    val items: Map[Int, Item]
  ) extends IPersistentModel[DIMSUMAlgorithmParams] {

  @transient lazy val itemIntStringMap = itemStringIntMap.inverse

  def save(id: String, params: DIMSUMAlgorithmParams,
    sc: SparkContext): Boolean = {

    similarities.saveAsObjectFile(s"/tmp/${id}/similarities")
    sc.parallelize(Seq(itemStringIntMap))
      .saveAsObjectFile(s"/tmp/${id}/itemStringIntMap")
    sc.parallelize(Seq(items))
      .saveAsObjectFile(s"/tmp/${id}/items")
    true
  }

  override def toString = {
    s"similarities: [${similarities.count()}]" +
    s"(${similarities.take(2).toList}...)" +
    s" itemStringIntMap: [${itemStringIntMap.size}]" +
    s"(${itemStringIntMap.take(2).toString}...)]" +
    s" items: [${items.size}]" +
    s"(${items.take(2).toString}...)]"
  }
}

object DIMSUMModel
  extends IPersistentModelLoader[DIMSUMAlgorithmParams, DIMSUMModel] {
  def apply(id: String, params: DIMSUMAlgorithmParams,
    sc: Option[SparkContext]) = {
    new DIMSUMModel(
      similarities = sc.get.objectFile(s"/tmp/${id}/similarities"),
      itemStringIntMap = sc.get
        .objectFile[BiMap[String, Int]](s"/tmp/${id}/itemStringIntMap").first,
      items = sc.get
        .objectFile[Map[Int, Item]](s"/tmp/${id}/items").first)
  }
}

class DIMSUMAlgorithm(val ap: DIMSUMAlgorithmParams)
  extends PAlgorithm[PreparedData, DIMSUMModel, Query, PredictedResult] {

  @transient lazy val logger = Logger[this.type]

  def train(data: PreparedData): DIMSUMModel = {

    // create User and item's String ID to integer index BiMap
    val userStringIntMap = BiMap.stringInt(data.users.keys)
    val itemStringIntMap = BiMap.stringInt(data.items.keys)

    // collect Item as Map and convert ID to Int index
    val items: Map[Int, Item] = data.items.map { case (id, item) =>
      (itemStringIntMap(id), item)
    }.collectAsMap.toMap
    val itemCount = items.size

    // each row is a sparse vector of rated items by this user
    val rows: RDD[Vector] = data.viewEvents
      .map { r =>
        // Convert user and item String IDs to Int index for MLlib
        val uindex = userStringIntMap.getOrElse(r.user, -1)
        val iindex = itemStringIntMap.getOrElse(r.item, -1)

        if (uindex == -1)
          logger.info(s"Couldn't convert nonexistent user ID ${r.user}"
            + " to Int index.")

        if (iindex == -1)
          logger.info(s"Couldn't convert nonexistent item ID ${r.item}"
            + " to Int index.")

        (uindex, (iindex, 1.0))
      }.filter { case (uindex, (iindex, v)) =>
        // keep events with valid user and item index
        (uindex != -1) && (iindex != -1)
      }.groupByKey().map { case (u, ir) =>
        // de-duplicate if user has multiple events on same item
        val irDedup: Map[Int, Double] = ir.groupBy(_._1) // group By item index
          .map { case (i, irGroup) =>
            // same item index group of (item index, rating value) tuple
            val r = irGroup.reduce { (a, b) =>
              // Simply keep one copy.
              a
              // You may modify here to reduce same item tuple differently,
              // such as summing all values:
              //(a._1, (a._2 + b._2))
            }
            (i, r._2)
          }

        // NOTE: index array must be strictly increasing for Sparse Vector
        val irSorted = irDedup.toArray.sortBy(_._1)
        val indexes = irSorted.map(_._1)
        val values = irSorted.map(_._2)
        Vectors.sparse(itemCount, indexes, values)
      }

    val mat = new RowMatrix(rows)
    val scores = mat.columnSimilarities(ap.threshold)
    val reversedEntries: RDD[MatrixEntry] = scores.entries
      .map(e => new MatrixEntry(e.j, e.i, e.value))
    val combined = new CoordinateMatrix(scores.entries.union(reversedEntries))
    val similarities = combined.toIndexedRowMatrix.rows
      .map( row => (row.index.toInt, row.vector.asInstanceOf[SparseVector]))

    new DIMSUMModel(
      similarities = similarities,
      itemStringIntMap = itemStringIntMap,
      items = items
    )
  }

  def predict(model: DIMSUMModel, query: Query): PredictedResult = {
    // convert the white and black list items to Int index
    val whiteList: Option[Set[Int]] = query.whiteList.map( set =>
      set.map(model.itemStringIntMap.get(_)).flatten
    )
    val blackList: Option[Set[Int]] = query.blackList.map ( set =>
      set.map(model.itemStringIntMap.get(_)).flatten
    )

    val queryList: Set[Int] = query.items.map(model.itemStringIntMap.get(_))
      .flatten.toSet

    val indexScores = query.items.flatMap { iid =>
      model.itemStringIntMap.get(iid).map { itemInt =>
        val simsSeq = model.similarities.lookup(itemInt)
        if (simsSeq.isEmpty) {
          logger.info(s"No similar items found for ${iid}.")
          Array.empty[(Int, Double)]
        } else {
          val sims = simsSeq.head
          sims.indices.zip(sims.values).filter { case (i, v) =>
            whiteList.map(_.contains(i)).getOrElse(true) &&
            blackList.map(!_.contains(i)).getOrElse(true) &&
            // discard items in query as well
            (!queryList.contains(i)) &&
            // filter categories
            query.categories.map { cat =>
              model.items(i).categories.map { itemCat =>
                // keep this item if has ovelap categories with the query
                !(itemCat.toSet.intersect(cat).isEmpty)
              }.getOrElse(false) // discard this item if it has no categories
            }.getOrElse(true)
          }
        }
      }.getOrElse {
        logger.info(s"No similar items for unknown item ${iid}.")
        Array.empty[(Int, Double)]
      }
    }

    val aggregatedScores = indexScores.groupBy(_._1)
      .mapValues(_.foldLeft[Double](0)( (b,a) => b + a._2))
      .toList

    val ord = Ordering.by[(Int, Double), Double](_._2).reverse
    val itemScores = getTopN(aggregatedScores, query.num)(ord)
      .map{ case (i, s) =>
        new ItemScore(
          item = model.itemIntStringMap(i),
          score = s
        )
      }.toArray

    new PredictedResult(itemScores)
  }

  private
  def getTopN[T](s: Seq[T], n: Int)(implicit ord: Ordering[T]): Seq[T] = {

    val q = PriorityQueue()

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
}
