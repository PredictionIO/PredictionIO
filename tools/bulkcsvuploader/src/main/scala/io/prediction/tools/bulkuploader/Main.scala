package io.prediction.tools.bulkuploader

/**
 * Run from root installation directory (eg.: $ ~/PredictionIO)
 *   % sbt "project toolsBulkCsvUploader" "run <app_id> <path/to/data.csv> <import_type>"
 *
 *   <app_id> is the application ID created in PredictionIO
 *   <path/to/data.csv> is the path to data file
 *   <import_type> is the data type to import and can be item, user or u2iaction
 *
 * Examples:
 *   % sbt "project toolsBulkCsvUploader" "run 1 /home/ryu/item.csv item" # import items from file /home/ryu/item.csv to app with ID 1
 *
 * Item file format:
 *   itemID, itype1, itype2, itype3, ..., itypeN
 *
 * User file format:
 *   userID
 *
 * U2IAction file format:
 *   userID,itemID,action[,value]
 *
 *   Note: value is used in combination with "rate" action and need to be a integer with values between 1 and 5
 *   Note 2: the action valid values are rate, like, dislike, view, viewDetails, conversion
 *
 */

import io.prediction.commons.Config
import io.prediction.commons.appdata.Item
import io.prediction.commons.appdata.User
import io.prediction.commons.appdata.U2IAction
import scala.util.Random

import java.io._
import scala.io.Source

import com.github.nscala_time.time.Imports.DateTime

object BulkCsvUploader {
  val importTypes = Array("item", "user", "u2iaction")
  var importErrors = 0
  def main(args: Array[String]) {
    println("Initializing BulkCvsUploader")
    println("Validating initializer parameters...")

    if (checkArgs(args)) {
      println("All parameters ok! Fasten your seatbelts ...")
      val appId = args(0).toInt
      val data = args(1)
      val importType = args(2)

      if (importType == "item") {
        println("Importing items ...")
        importItem(appId, data)
      }

      if (importType == "user") {
        println("Importing users ...")
        importUser(appId, data)
      }

      if (importType == "u2iaction") {
        println("Importing u2iaction ...")
        importU2Iaction(appId, data)
      }
      if (importErrors == 0) {
        println("Finished.")
      } else {
        println("Finished with " + importErrors + " errors. Check the log for more information.")
      }

    } else {
      println("Sorry. Ending the program.")
    }

  }

  def checkArgs(args: Array[String]): Boolean = {
    // check argments length
    if (args.length != 3) {
      println("Invalid parameters size. Read " + args.length + " from 3 expected")
      return false
    }
    // check path from file at args(1)
    var check = new java.io.File(args(1)).exists
    if (check == false) {
      println("Invalid parameter at index 1. File '" + args(1) + "' do not exists or cannot be readed.")
      return false
    }
    // check 
    check = importTypes contains (args(2))
    if (check == false) {
      println("Invalid parameter at index 2. Invalid import_type '" + args(2) + "''. Valid values are item, user or u2iaction.")
      return false
    }
    return true
  }

  def importItem(appid: Int, data: String): Boolean = {
    val config = new Config()
    val items = config.getAppdataItems()
    def makeItem(iid: String, itypes: Seq[String]): Item =
      Item(
        id = iid,
        appid = appid,
        ct = DateTime.now,
        itypes = itypes,
        starttime = Some(DateTime.now),
        endtime = None,
        price = None,
        profit = None,
        latlng = None,
        inactive = None,
        attributes = None
      );
    val src = Source.fromFile(data).getLines
    for (l <- src) {
      if (checkItemLine(l)) {
        val fields = l.split(",")
        items.insert(makeItem(fields(0), fields.takeRight(fields.length - 1).toList))
      } else {
        importErrors += 1
      }
    }
    return true
  }

  def checkItemLine(l: String): Boolean = {
    // nothing to do because accept any number of fields
    return true
  }

  def importUser(appid: Int, data: String): Boolean = {
    val config = new Config()
    val users = config.getAppdataUsers()
    def makeUser(idd: String): User = User(
      id = idd,
      appid = appid,
      ct = DateTime.now,
      latlng = None,
      inactive = None,
      attributes = None);
    val src = Source.fromFile(data).getLines
    for (l <- src) {
      if (checkUserLine(l)) {
        val fields = l.split(",")
        users.insert(makeUser(fields(0)))
      } else {
        importErrors += 1
      }
    }
    return true
  }

  def checkUserLine(l: String): Boolean = {
    val fields = l.split(",")
    // check number of fields
    if (fields.length > 1) {
      println("Line " + l + " cannot be processed: user have only one field for each line")
      return false
    }
    return true
  }

  def importU2Iaction(appid: Int, data: String): Boolean = {
    val config = new Config()
    val u2i = config.getAppdataU2IActions()
    def makeU2IAction(uid: String, iid: String, action: String, v: Option[Int] = None): U2IAction = U2IAction(
      appid = appid,
      action = action,
      uid = uid,
      iid = iid,
      t = DateTime.now,
      latlng = None,
      v = v,
      price = None);
    val src = Source.fromFile(data).getLines
    for (l <- src) {
      // println(l)
      if (checkU2IactionLine(l)) {
        val fields = l.split(",")
        if (fields(2) != "rate") {
          u2i.insert(makeU2IAction(fields(0), fields(1), fields(2)))
        } else {
          val ratio = Option(fields(3).toInt)
          u2i.insert(makeU2IAction(fields(0), fields(1), fields(2), ratio))
        }
      } else {
        importErrors += 1
      }
    }
    return true

  }

  def checkU2IactionLine(l: String): Boolean = {
    val fields = l.split(",")
    // check number of fields
    if (fields.length < 3 || fields.length > 4) {
      println("Line " + l + " cannot be processed: number of fields is invalid. Need to be 3 or 4.")
      return false
    }
    // check valid action
    val validU2Iactions = Array("rate", "like", "dislike", "view", "viewDetails", "conversion")
    if (importTypes contains (fields(2))) {
      println("Line '" + l + "' cannot be processed: action " + fields(2) + " is invalid. Valid values are rate, like, dislike, view, viewDetails or conversion.")
      return false
    }
    // if action is rate, the fourth fields is needed and should be a number
    if (fields(1) == "rate") {
      if (fields.length != 4) {
        println("Line '" + l + "' cannot be processed: to rate action, the fourth field is needed")
        return false
      }
      if (!(fields(3) forall Character.isDigit)) {
        println("Line '" + l + "' cannot be processed: the fourth field should be a integer number")
        return false
      }
      val ratio_number = fields(3).toInt
      if (ratio_number < 1 || ratio_number > 5) {
        println("Line '" + l + "' cannot be processed: the fourth field should greater than 1 and less than 5")
        return false
      }
    }
    return true
  }

}
