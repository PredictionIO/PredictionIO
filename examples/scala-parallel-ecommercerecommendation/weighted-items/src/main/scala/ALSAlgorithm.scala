package org.template.ecommercerecommendation

import org.apache.predictionio.controller.P2LAlgorithm
import org.apache.predictionio.controller.Params
import org.apache.predictionio.data.storage.BiMap
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.Storage

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.mllib.recommendation.ALS
import org.apache.spark.mllib.recommendation.{Rating => MLlibRating}

import grizzled.slf4j.Logger

import scala.collection.mutable.PriorityQueue
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

case class ALSAlgorithmParams(
  appId: Int,
  unseenOnly: Boolean,
  seenEvents: List[String],
  rank: Int,
  numIterations: Int,
  lambda: Double,
  seed: Option[Long]
) extends Params

class ALSModel(
  val rank: Int,
  val userFeatures: Map[Int, Array[Double]],
  val productFeatures: Map[Int, (Item, Option[Array[Double]])],
  val userStringIntMap: BiMap[String, Int],
  val itemStringIntMap: BiMap[String, Int]
) extends Serializable {

  @transient lazy val itemIntStringMap = itemStringIntMap.inverse

  override def toString = {
    s" rank: ${rank}" +
    s" userFeatures: [${userFeatures.size}]" +
    s"(${userFeatures.take(2).toList}...)" +
    s" productFeatures: [${productFeatures.size}]" +
    s"(${productFeatures.take(2).toList}...)" +
    s" userStringIntMap: [${userStringIntMap.size}]" +
    s"(${userStringIntMap.take(2).toString}...)]" +
    s" itemStringIntMap: [${itemStringIntMap.size}]" +
    s"(${itemStringIntMap.take(2).toString}...)]"
  }
}

// Item weights are defined according to this structure so that groups of items can be easily changed together
case class WeightsGroup(
  items: Set[String],
  weight: Double
)

/**
  * Use ALS to build item x feature matrix
  */
class ALSAlgorithm(val ap: ALSAlgorithmParams)
  extends P2LAlgorithm[PreparedData, ALSModel, Query, PredictedResult] {

  @transient lazy val logger = Logger[this.type]
  // NOTE: use getLEvents() for local access
  @transient lazy val lEventsDb = Storage.getLEvents()

  def train(sc: SparkContext, data: PreparedData): ALSModel = {
    require(!data.viewEvents.take(1).isEmpty,
      s"viewEvents in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")
    require(!data.users.take(1).isEmpty,
      s"users in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")
    require(!data.items.take(1).isEmpty,
      s"items in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")
    // create User and item's String ID to integer index BiMap
    val userStringIntMap = BiMap.stringInt(data.users.keys)
    val itemStringIntMap = BiMap.stringInt(data.items.keys)

    val mllibRatings = data.viewEvents
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

        ((uindex, iindex), 1)
      }.filter { case ((u, i), v) =>
        // keep events with valid user and item index
        (u != -1) && (i != -1)
      }
      .reduceByKey(_ + _) // aggregate all view events of same user-item pair
      .map { case ((u, i), v) =>
        // MLlibRating requires integer index for user and item
        MLlibRating(u, i, v)
      }.cache()

    // MLLib ALS cannot handle empty training data.
    require(!mllibRatings.take(1).isEmpty,
      s"mllibRatings cannot be empty." +
      " Please check if your events contain valid user and item ID.")

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

    val userFeatures = m.userFeatures.collectAsMap.toMap

    // convert ID to Int index
    val items = data.items.map { case (id, item) =>
      (itemStringIntMap(id), item)
    }

    // join item with the trained productFeatures
    val productFeatures = items.leftOuterJoin(m.productFeatures)
      .collectAsMap.toMap

    new ALSModel(
      rank = m.rank,
      userFeatures = userFeatures,
      productFeatures = productFeatures,
      userStringIntMap = userStringIntMap,
      itemStringIntMap = itemStringIntMap
    )
  }

  def predict(model: ALSModel, query: Query): PredictedResult = {

    val userFeatures = model.userFeatures
    val productFeatures = model.productFeatures

    // convert whiteList's string ID to integer index
    val whiteList: Option[Set[Int]] = query.whiteList.map( set =>
      set.map(model.itemStringIntMap.get).flatten
    )

    val blackList: Set[String] = query.blackList.getOrElse(Set[String]())

    // if unseenOnly is True, get all seen items
    val seenItems: Set[String] = if (ap.unseenOnly) {

      // get all user item events which are considered as "seen" events
      val seenEvents: Iterator[Event] = lEventsDb.findSingleEntity(
        appId = ap.appId,
        entityType = "user",
        entityId = query.user,
        eventNames = Some(ap.seenEvents),
        targetEntityType = Some(Some("item")),
        // set time limit to avoid super long DB access
        timeout = 200.millis
      ) match {
        case Right(x) => x
        case Left(e) => {
          logger.error(s"Error when read seen events: ${e}")
          Iterator[Event]()
        }
      }

      seenEvents.map { event =>
        try {
          event.targetEntityId.get
        } catch {
          case e => {
            logger.error(s"Can't get targetEntityId of event ${event}.")
            throw e
          }
        }
      }.toSet
    } else {
      Set[String]()
    }

    // get the latest constraint unavailableItems $set event
    val unavailableItems: Set[String] = lEventsDb.findSingleEntity(
      appId = ap.appId,
      entityType = "constraint",
      entityId = "unavailableItems",
      eventNames = Some(Seq("$set")),
      limit = Some(1),
      latest = true,
      timeout = 200.millis
    ) match {
      case Right(x) => {
        if (x.hasNext) {
          x.next.properties.get[Set[String]]("items")
        } else {
          Set[String]()
        }
      }
      case Left(e) => {
        logger.error(s"Error when read set unavailableItems event: ${e}")
        Set[String]()
      }
    }

    // Get the latest constraint weightedItems. This comes in the form of a sequence of WeightsGroup
    val groupedWeights = lEventsDb.findSingleEntity(
      appId = ap.appId,
      entityType = "constraint",
      entityId = "weightedItems",
      eventNames = Some(Seq("$set")),
      limit = Some(1),
      latest = true,
      timeout = 200.millis
    ) match {
      case Right(x) =>
        if (x.hasNext)
          x.next().properties.get[Seq[WeightsGroup]]("weights")
        else
          Seq.empty
      case Left(e) =>
        logger.error(s"Error when reading set weightedItems event: ${e}")
        Seq.empty
    }

    // Transform groupedWeights into a map of index -> weight that we can easily query
    val weights: Map[Int, Double] = (for {
      group <- groupedWeights
      item <- group.items
      index <- model.itemStringIntMap.get(item)
    } yield (index, group.weight))
      .toMap
      .withDefaultValue(1.0)

    // combine query's blackList,seenItems and unavailableItems
    // into final blackList.
    // convert seen Items list from String ID to integer Index
    val finalBlackList: Set[Int] = (blackList ++ seenItems ++ unavailableItems)
      .map( x => model.itemStringIntMap.get(x)).flatten

    val userFeature =
      model.userStringIntMap.get(query.user).map { userIndex =>
        userFeatures.get(userIndex)
      }
      // flatten Option[Option[Array[Double]]] to Option[Array[Double]]
      .flatten

    val topScores = if (userFeature.isDefined) {
      // the user has feature vector
      val uf = userFeature.get
      val indexScores: Map[Int, Double] =
        productFeatures.par // convert to parallel collection
          .filter { case (i, (item, feature)) =>
            feature.isDefined &&
            isCandidateItem(
              i = i,
              item = item,
              categories = query.categories,
              whiteList = whiteList,
              blackList = finalBlackList
            )
          }
          .map { case (i, (item, feature)) =>
            // NOTE: feature must be defined, so can call .get
            val originalScore = dotProduct(uf, feature.get)
            // Adjusting score according to given item weights
            val adjustedScore = originalScore * weights(i)
            (i, adjustedScore)
          }
          .filter(_._2 > 0) // only keep items with score > 0
          .seq // convert back to sequential collection

      val ord = Ordering.by[(Int, Double), Double](_._2).reverse
      val topScores = getTopN(indexScores, query.num)(ord).toArray

      topScores

    } else {
      // the user doesn't have feature vector.
      // For example, new user is created after model is trained.
      logger.info(s"No userFeature found for user ${query.user}.")
      predictNewUser(
        model = model,
        query = query,
        whiteList = whiteList,
        blackList = finalBlackList,
        weights = weights
      )
    }

    val itemScores = topScores.map { case (i, s) =>
      new ItemScore(
        // convert item int index back to string ID
        item = model.itemIntStringMap(i),
        score = s
      )
    }

    new PredictedResult(itemScores)
  }

  /** Get recently viewed item of the user and return top similar items */
  private
  def predictNewUser(
    model: ALSModel,
    query: Query,
    whiteList: Option[Set[Int]],
    blackList: Set[Int],
    weights: Map[Int, Double]): Array[(Int, Double)] = {

    val productFeatures = model.productFeatures

    // get latest 10 user view item events
    val recentEvents = lEventsDb.findSingleEntity(
      appId = ap.appId,
      // entityType and entityId is specified for fast lookup
      entityType = "user",
      entityId = query.user,
      eventNames = Some(Seq("view")),
      targetEntityType = Some(Some("item")),
      limit = Some(10),
      latest = true,
      // set time limit to avoid super long DB access
      timeout = Duration(200, "millis")
    ) match {
      case Right(x) => x
      case Left(e) => {
        logger.error(s"Error when read recent events: ${e}")
        Iterator[Event]()
      }
    }

    val recentItems: Set[String] = recentEvents.map { event =>
      try {
        event.targetEntityId.get
      } catch {
        case e => {
          logger.error("Can't get targetEntityId of event ${event}.")
          throw e
        }
      }
    }.toSet

    val recentList: Set[Int] = recentItems.map (x =>
      model.itemStringIntMap.get(x)).flatten

    val recentFeatures: Vector[Array[Double]] = recentList.toVector
      // productFeatures may not contain the requested item
      .map { i =>
        productFeatures.get(i).map { case (item, f) => f }.flatten
      }.flatten

    val indexScores: Map[Int, Double] = if (recentFeatures.isEmpty) {
      logger.info(s"No productFeatures vector for recent items ${recentItems}.")
      Map[Int, Double]()
    } else {
      productFeatures.par // convert to parallel collection
        .filter { case (i, (item, feature)) =>
          feature.isDefined &&
          isCandidateItem(
            i = i,
            item = item,
            categories = query.categories,
            whiteList = whiteList,
            blackList = blackList
          )
        }
        .map { case (i, (item, feature)) =>
          val originalScore = recentFeatures.map { rf =>
            cosine(rf, feature.get) // feature is defined
          }.sum
          // Adjusting score according to given item weights
          val adjustedScore = originalScore * weights(i)
          (i, adjustedScore)
        }
        .filter(_._2 > 0) // keep items with score > 0
        .seq // convert back to sequential collection
    }

    val ord = Ordering.by[(Int, Double), Double](_._2).reverse
    val topScores = getTopN(indexScores, query.num)(ord).toArray

    topScores
  }

  private
  def getTopN[T](s: Iterable[T], n: Int)(implicit ord: Ordering[T]): Seq[T] = {

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

  private
  def dotProduct(v1: Array[Double], v2: Array[Double]): Double = {
    val size = v1.length
    var i = 0
    var d: Double = 0
    while (i < size) {
      d += v1(i) * v2(i)
      i += 1
    }
    d
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
  def isCandidateItem(
    i: Int,
    item: Item,
    categories: Option[Set[String]],
    whiteList: Option[Set[Int]],
    blackList: Set[Int]
  ): Boolean = {
    // can add other custom filtering here
    whiteList.map(_.contains(i)).getOrElse(true) &&
    !blackList.contains(i) &&
    // filter categories
    categories.map { cat =>
      item.categories.exists { itemCat =>
        // keep this item if its categories overlap with the query categories
        itemCat.toSet.intersect(cat).nonEmpty
      } // discard this item if it has no categories
    }.getOrElse(true)
  }
}
