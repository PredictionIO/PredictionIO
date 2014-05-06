package controllers

import play.api.Play.current
import io.prediction.commons.Config
import java.io.File
import play.api.mvc._
import com.mongodb.casbah.Imports._
import play.api.libs.json._
import play.api.libs.concurrent.Akka

object StatsCollector extends Controller {

  //MongoDB information
  val MongoHost = "localhost"
  val MongoPort = 27017
  val DBname = "stats"
  val CollName = "stats"
  val Refresh = 2000 //Number of milli-seconds to refresh stats in

  //MongoDB objects

  val mongoClient = MongoClient(MongoHost, MongoPort)
  val db = mongoClient(DBname)
  val coll = db(CollName)

  /*
  Stats collector format:
  {
    timestamp : 
    cpuUsage : 
    ramUsage :
    diskUsage : 
  }
  */
  def getStats(n: Int) = Action {
    val order = MongoDBObject("timestamp" -> -1)
    val stats = coll.find().sort(order).limit(n)
    val json = "[%s]".format(
      stats.toList.mkString(",")
    )

    Ok(json).as("application/json")
  }

}
