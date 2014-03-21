package io.prediction.scheduler

import io.prediction.commons.filepath.BaseDir
import io.prediction.commons.Config

import scala.sys.process._
import java.io.File

import org.apache.commons.io.FileUtils
import play.api._
import play.api.libs.json._
import play.api.mvc._

object Operations extends Controller {
  val config = new Config

  def hadoopRequired(request: Request[_]) = request.queryString.contains("hadoop")

  def deleteApp(appid: Int) = Action { implicit request =>
    val localPath = BaseDir.appDir(config.settingsLocalTempRoot, appid)
    val localFile = new File(localPath)
    localFile.mkdirs()
    val localCode = FileUtils.deleteQuietly(localFile)
    val hadoopPath = BaseDir.appDir(config.settingsHdfsRoot, appid)
    // mkdir again to make sure that rmr failure is not due to non existing dir.
    // mkdir error can be ignored.
    val hadoopCode = if (hadoopRequired(request)) {
      val mkdir = s"${Scheduler.hadoopCommand} fs -mkdir $hadoopPath".!
      val code = s"${Scheduler.hadoopCommand} fs -rmr $hadoopPath".!
      if (code == 0) true else false
    } else true
    if (localCode && hadoopCode)
      Ok(Json.obj("message" -> s"Deleted local (and HDFS, if applicable) storage for App ID: $appid (local: $localPath; HDFS: $hadoopPath)"))
    else if (localCode)
      InternalServerError(Json.obj("message" -> s"Unable to delete HDFS storage for App ID: $appid ($hadoopPath). Please check logs/scheduler.log, logs/scheduler.err and Hadoop log files."))
    else
      InternalServerError(Json.obj("message" -> s"Unable to delete local temporary storage for App ID: $appid ($localPath). Please check logs/scheduler.log, logs/scheduler.err and Hadoop log files."))
  }

  def deleteEngine(appid: Int, engineid: Int) = Action { implicit request =>
    val localPath = BaseDir.engineDir(config.settingsLocalTempRoot, appid, engineid)
    val localFile = new File(localPath)
    localFile.mkdirs()
    val localCode = FileUtils.deleteQuietly(localFile)
    val hadoopPath = BaseDir.engineDir(config.settingsHdfsRoot, appid, engineid)
    // mkdir again to make sure that rmr failure is not due to non existing dir.
    // mkdir error can be ignored.
    val hadoopCode = if (hadoopRequired(request)) {
      val mkdir = s"${Scheduler.hadoopCommand} fs -mkdir $hadoopPath".!
      val code = s"${Scheduler.hadoopCommand} fs -rmr $hadoopPath".!
      if (code == 0) true else false
    } else true
    if (localCode && hadoopCode)
      Ok(Json.obj("message" -> s"Deleted local (and HDFS, if applicable) storage for App ID: $appid, Engine ID: $engineid (local: $localPath; HDFS: $hadoopPath)"))
    else if (localCode)
      InternalServerError(Json.obj("message" -> s"Unable to delete HDFS storage for App ID: $appid, Engine ID: $engineid ($hadoopPath). Please check logs/scheduler.log, logs/scheduler.err and Hadoop log files."))
    else
      InternalServerError(Json.obj("message" -> s"Unable to delete local temporary storage for App ID: $appid, Engine ID: $engineid ($localPath). Please check logs/scheduler.log, logs/scheduler.err and Hadoop log files."))
  }

  def deleteAlgo(appid: Int, engineid: Int, algoid: Int) = Action { implicit request =>
    val localPath = BaseDir.algoDir(config.settingsLocalTempRoot, appid, engineid, algoid, None)
    val localFile = new File(localPath)
    localFile.mkdirs()
    val localCode = FileUtils.deleteQuietly(localFile)
    val hadoopPath = BaseDir.algoDir(config.settingsHdfsRoot, appid, engineid, algoid, None)
    val hadoopCode = if (hadoopRequired(request)) {
      val mkdir = s"${Scheduler.hadoopCommand} fs -mkdir $hadoopPath".!
      val code = s"${Scheduler.hadoopCommand} fs -rmr $hadoopPath".!
      if (code == 0) true else false
    } else true
    if (localCode && hadoopCode)
      Ok(Json.obj("message" -> s"Deleted HDFS storage for App ID: $appid, Engine ID: $engineid, Algo ID: $algoid (local: $localPath; HDFS: $hadoopPath)"))
    else if (localCode)
      InternalServerError(Json.obj("message" -> s"Unable to delete HDFS storage for App ID: $appid, Engine ID: $engineid, Algo ID: $algoid ($hadoopPath). Please check logs/scheduler.log, logs/scheduler.err and Hadoop log files."))
    else
      InternalServerError(Json.obj("message" -> s"Unable to delete local temporary storage for App ID: $appid, Engine ID: $engineid, Algo ID: $algoid ($localPath). Please check logs/scheduler.log, logs/scheduler.err and Hadoop log files."))
  }

  def deleteOfflineEval(appid: Int, engineid: Int, offlineevalid: Int) = Action { implicit request =>
    val localPath = BaseDir.offlineEvalDir(config.settingsLocalTempRoot, appid, engineid, offlineevalid)
    val localFile = new File(localPath)
    localFile.mkdirs()
    val localCode = FileUtils.deleteQuietly(localFile)
    val hadoopPath = BaseDir.offlineEvalDir(config.settingsHdfsRoot, appid, engineid, offlineevalid)
    // mkdir again to make sure that rmr failure is not due to non existing dir.
    // mkdir error can be ignored.
    val hadoopCode = if (hadoopRequired(request)) {
      val mkdir = s"${Scheduler.hadoopCommand} fs -mkdir $hadoopPath".!
      val code = s"${Scheduler.hadoopCommand} fs -rmr $hadoopPath".!
      if (code == 0) true else false
    } else true
    if (localCode && hadoopCode)
      Ok(Json.obj("message" -> s"Deleted local (and HDFS, if applicable) storage for App ID: $appid, Engine ID: $engineid, OfflineEval ID: $offlineevalid (local: $localPath; HDFS: $hadoopPath)"))
    else if (localCode)
      InternalServerError(Json.obj("message" -> s"Unable to delete HDFS storage for App ID: $appid, Engine ID: $engineid, OfflineEval ID: $offlineevalid ($hadoopPath). Please check logs/scheduler.log, logs/scheduler.err and Hadoop log files."))
    else
      InternalServerError(Json.obj("message" -> s"Unable to delete local temporary storage for App ID: $appid, Engine ID: $engineid, OfflineEval ID: $offlineevalid ($localPath). Please check logs/scheduler.log, logs/scheduler.err and Hadoop log files."))
  }
}
