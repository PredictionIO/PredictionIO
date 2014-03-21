package io.prediction.commons.scalding.appdata.mongodb

import com.twitter.scalding._

import cascading.pipe.Pipe
import cascading.flow.FlowDef

import java.util.ArrayList
import java.util.HashMap

//import org.scala_tools.time.Imports._
//import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime

import com.mongodb.BasicDBList
import com.mongodb.casbah.Imports._
//import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers

import io.prediction.commons.scalding.MongoSource
import io.prediction.commons.scalding.appdata.U2iActionsSource
import io.prediction.commons.scalding.appdata.U2iActionsSource.FIELD_SYMBOLS

class MongoU2iActionsSource(db: String, host: String, port: Int, appId: Int) extends MongoSource(
  db = db,
  coll = "u2iActions",
  cols = {
    val u2iCols = new ArrayList[String]()
    u2iCols.add("action") // 0
    u2iCols.add("uid") // 1
    u2iCols.add("iid") // 2
    u2iCols.add("t") // 3
    u2iCols.add("v") // 4 optional
    u2iCols.add("appid")

    u2iCols
  },
  mappings = {
    val u2iMappings = new HashMap[String, String]()

    u2iMappings.put("action", FIELD_SYMBOLS("action").name)
    u2iMappings.put("uid", FIELD_SYMBOLS("uid").name)
    u2iMappings.put("iid", FIELD_SYMBOLS("iid").name)
    u2iMappings.put("t", FIELD_SYMBOLS("t").name)
    u2iMappings.put("v", FIELD_SYMBOLS("v").name)
    u2iMappings.put("appid", FIELD_SYMBOLS("appid").name)

    u2iMappings
  },
  query = { // read query
    /*
      val builder = MongoDBObject.newBuilder
      
      queryData foreach {
        case (field, value) => 
          builder += field -> value
      }
      
      builder.result
      */

    val u2iQuery = MongoDBObject("appid" -> appId)
    //++
    //  (evalId.map(x => MongoDBObject("evalid" -> x)).getOrElse(MongoDBObject()))

    u2iQuery
  },
  host = host, // String
  port = port // Int
) with U2iActionsSource {

  import com.twitter.scalding.Dsl._ // get all the fancy implicit conversions that define the DSL

  //RegisterJodaTimeConversionHelpers()

  override def getSource: Source = this

  /**
   * NOTE:
   * for optional field vField, due to the current limitation/issue of mongo-hadoop/cascading-mongo Tap,
   * the value will be the same as previous read record if this record has this field missing while
   * None/Null should be expected.
   * Since the meaning of v field depends on action field while action field is a required field,
   * can still work around this issue because can decode meaning of v field based on action field.
   */
  override def readData(actionField: Symbol, uidField: Symbol, iidField: Symbol, tField: Symbol, vField: Symbol)(implicit fd: FlowDef): Pipe = {
    val u2iactions = this.read
      .mapTo((0, 1, 2, 3, 4) -> (actionField, uidField, iidField, tField, vField)) {
        fields: (String, String, String, java.util.Date, String) =>
          val (action, uid, iid, t, v) = fields

          //val dt = new DateTime(t)
          val vOpt: Option[String] = Option(v)

          (action, uid, iid, t.getTime().toString, vOpt)
      }

    u2iactions
  }

  override def writeData(actionField: Symbol, uidField: Symbol, iidField: Symbol, tField: Symbol, vField: Symbol, appid: Int)(p: Pipe)(implicit fd: FlowDef): Pipe = {
    val dbData = p.mapTo((actionField, uidField, iidField, tField, vField) ->
      (FIELD_SYMBOLS("action"), FIELD_SYMBOLS("uid"), FIELD_SYMBOLS("iid"), FIELD_SYMBOLS("t"), FIELD_SYMBOLS("v"), FIELD_SYMBOLS("appid"))) {
      fields: (String, String, String, String, Option[String]) =>
        val (action, uid, iid, t, v) = fields

        // u2iAction v field type is Int
        val vData: Any = v.map(_.toInt).getOrElse(null) // use null if no such field for this record

        (action, uid, iid, new java.util.Date(t.toLong), vData, appid)
    }.write(this)

    dbData
  }

}
