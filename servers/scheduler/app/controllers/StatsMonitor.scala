package io.prediction.scheduler

import io.prediction.commons.Config
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.JsNull
import play.api.libs.json.Json
import play.api.Logger
import scala.collection.JavaConverters._
import scala.collection.Seq
import scala.sys.process._
import org.hyperic.sigar._
import org.hyperic.sigar.ptql._

trait StatsMonitor {
  this: Controller =>

  val sigar = new Sigar
  val mem = new ProcMem
  val cpu = new ProcCpu
  val dir = new DirUsage

  var config = new Config()

  val hosts = Seq(
    config.appdataDbHost + ":" + config.appdataDbPort + "/" + config.appdataDbName,
    config.appdataTrainingDbHost + ":" + config.appdataTrainingDbPort + "/" + config.appdataTrainingDbName,
    config.appdataValidationDbHost + ":" + config.appdataValidationDbPort + "/" + config.appdataValidationDbName,
    config.modeldataDbHost + ":" + config.modeldataDbPort + "/" + config.modeldataDbName,
    config.modeldataTrainingDbHost + ":" + config.modeldataTrainingDbPort + "/" + config.modeldataTrainingDbName,
    config.settingsDbHost + ":" + config.settingsDbPort + "/" + config.settingsDbName
  ) distinct

  val hostCmds = hosts map {
    host => Seq("mongo", host, "--eval", "printjson(db.stats())")
  }

  def getHostCmds: Seq[Seq[String]] = {
    return hostCmds
  }

  def getPIDs: Array[Long] = {
    if (config == null) {
      config = new Config()
    }

    val procFinder = new ProcessFinder(sigar)
    var pids = Array[Long]()

    //Get Java PIDs
    pids = pids ++ procFinder.find("State.Name.ct=java")

    //Get MongoDB PIDs
    if (config.settingsDbType == "mongodb") {
      pids = pids ++ procFinder.find("State.Name.ct=mongo")
    }

    return pids
  }

  // Wrapper function to get RAM Usage for a given PID
  def getRam(pid: Long): Long = {
    mem.gather(sigar, pid)
    return mem.getResident;
  }

  // Returns the total RAM used by the different processes
  // in MegaBytes
  def getRam: Double = {
    var total = 0.0

    for (pid <- getPIDs) {
      total += getRam(pid)
    }

    return total / 1048576
  }

  // Wrapper function to get CPU Usage for a given PID
  def getCpu(pid: Long): Double = {
    cpu.gather(sigar, pid)
    return cpu.getPercent()
  }

  // Returns the total CPU Usage for the different processes
  def getCpu: Double = {
    var total = 0.0

    for (pid <- getPIDs) {
      total += getCpu(pid)
    }

    return total
  }

  // Wrapper function to Execute command
  def execute(cmd: Seq[String]): String = {
    return cmd.!!
  }

  // Accesses the MongoDB Command line to retrieve disk usage
  def getMongoDisk: Double = {
    def getFileSize(hostCmd: Seq[String]) = {
      val output = execute(hostCmd)

      val reg = """"fileSize\" : (\d+),""".r
      val size: String = reg findFirstIn output match {
        case Some(reg(s)) => s
        case None => "0"
      }

      size.toDouble
    }
    val fileSizes = getHostCmds map getFileSize

    return fileSizes reduce (_ + _)
  }

  // Wrapper function to get disk usage of a given path
  def getDisk(path: String): Long = {
    dir.gather(sigar, path)
    return dir.getDiskUsage()
  }

  /**
   * Get total Disk Space used in MegaBytes
   */
  def getDisk: Double = {
    if (config == null) {
      config = new Config()
    }
    var total = 0.0

    //HDFS Disk Usage
    var hdfsDir = config.settingsHdfsRoot
    try {
      total += getDisk(hdfsDir)
    } catch {
      case e: Exception => Logger.warn("Could not get HDFS Directory at " + hdfsDir)
    }

    //Disk usage for MongoDB
    if (config.settingsDbType == "mongodb") {
      total += getMongoDisk
    }

    return total / 1048576
  }

  /**
   * getHadoopJobs accesses the command line for hadoop
   * and retrieved the job completion of the different map/reduce tasks
   */
  def getHadoopJobs: JsArray = {
    val hadoop = (config.settingsHadoopHome).getOrElse("") + "/bin/hadoop"

    if (hadoop == "/bin/hadoop") {
      return Json.arr()
    }

    val listCmd = Seq(hadoop, "job", "-list")

    var outputLines = (execute(listCmd)).split('\n')

    Logger.info(outputLines.toString())

    //Get Jobs
    def getJobs(output: String): String = {
      var reg = """(job\S+)""".r

      val job: String = reg findFirstIn output match {
        case Some(reg(s)) => s
        case None => ""
      }

      if (job == "jobs") {
        return ""
      }

      return job
    }

    val jobs = (outputLines map getJobs).filter(x => x != "")

    val statusCmd = jobs map {
      job => (job, Seq(hadoop, "job", "-status", job))
    }

    def getJobStatus(cmd: Tuple2[String, Seq[String]]): JsArray = {
      Logger.info(cmd._2.toString())
      var out = execute(cmd._2)
      Logger.info(out)

      val mapreg = """map() completion: ([0-9]*\.[0-9]+)""".r
      val redreg = """reduce() completion: ([0-9]*\.[0-9]+)""".r

      val mapComp: String = mapreg findFirstIn out match {
        case Some(mapreg(s)) => s
        case None => "0.000"
      }
      val redComp: String = redreg findFirstIn out match {
        case Some(redreg(s)) => s
        case None => "0.000"
      }

      if (mapComp == "0.000" && redComp == "0.000") {
        return Json.arr()
      }

      val mapName: String = cmd._1 + "_map"
      val redName: String = cmd._1 + "_reduce"

      return Json.arr(
        Json.obj("name" -> mapName, "value" -> mapComp.toDouble),
        Json.obj("name" -> redName, "value" -> redComp.toDouble))
    }

    val status = statusCmd map getJobStatus

    if (status.isEmpty) {
      return Json.arr()
    }

    return status reduce (_ ++ _)
  }

  /**
   * Get The different Job statuses currently running
   * Structure:
   *   [
   *     {name: "jobName1", value: 0.1234},
   *     {name: "jobName2", value: 0.5678},
   *     ...
   *   ]
   * value should be [0, 1] and represent the percentage of the task done
   */
  def getJobs: JsArray = {
    var jobs = Json.arr()

    if ((config.settingsHadoopHome).getOrElse("") != "") { // If not equal to default value
      jobs = jobs ++ getHadoopJobs
    }

    return jobs
  }

  /**
   * Get RAM usage in percent
   */
  def getRamUsage = Action {
    val total = getRam

    Ok(Json.obj("ram" -> (total)))
  }

  /**
   * Get CPU usage in percent
   */
  def getCpuUsage = Action {
    val total = getCpu

    Ok(Json.obj("cpu" -> (total)))
  }

  /**
   * Get total Disk Space used
   */
  def getUsedDiskSpace() = Action {
    val total = getDisk

    Ok(Json.obj("disk" -> (total)))
  }

  /**
   * Get the statuses of different jobs running
   */
  def getJobUsage() = Action {
    val jobs = getJobs

    Ok(Json.obj("jobs" -> jobs))
  }

  /**
   * Get an aggragate Json object of the different stats
   */
  def getStats() = Action {
    val ram = getRam
    val cpu = getCpu
    val disk = getDisk
    val jobs = getJobs

    Ok(Json.obj("ram" -> ram, "cpu" -> cpu, "disk" -> disk, "jobs" -> jobs))
  }

}

object StatsMonitor extends Controller with StatsMonitor
