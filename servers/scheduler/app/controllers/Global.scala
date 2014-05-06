import java.util.Date

import play.api._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future

object Global extends GlobalSettings {
  override def onStart(app: Application) = {

    Logger.info("Calling start StatsCollector on StartUP")

    future {
      Logger.info("Started Stats Collector Thread")
      io.prediction.scheduler.StatsCollector.storeStats()
    }

    Logger.info("Quartz scheduler is starting...")

    io.prediction.scheduler.Scheduler.scheduler.start()

    future { io.prediction.scheduler.Scheduler.syncAllUsers() }
  }

  override def onStop(app: Application) = {
    Logger.info("Quartz scheduler is shutting down...")

    io.prediction.scheduler.Scheduler.scheduler.shutdown()
  }
}