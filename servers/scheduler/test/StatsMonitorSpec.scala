package io.prediction.scheduler

import org.specs2.mock._
import org.specs2.mutable._
import play.api.libs.json._
import play.api.mvc._
import play.api.test._
import scala.collection.Seq
import scala.concurrent.Future
import scala.sys.process._
import org.hyperic.sigar._

object StatsMonitorSpec extends PlaySpecification with Results with Mockito {

  class TestStatsMonitor() extends Controller with StatsMonitor

  val mockPids = Array(0L, 1L, 2L)

  "StatsMonitor#getRamUsage" should {
    "should return" in {
      val controller = spy(new TestStatsMonitor())

      controller.getPIDs returns mockPids
      org.mockito.Mockito.doReturn(1048576L).when(controller).getRam(anyLong)

      val result: Future[SimpleResult] = controller.getRamUsage.apply(FakeRequest())
      val bodyJson = Json.parse(contentAsString(result))

      Json.stringify(bodyJson \ "ram") must be equalTo "3.0"
    }
  }

  "StatsMonitor#getCpuUsage" should {
    "should return" in {
      val controller = spy(new TestStatsMonitor())

      controller.getPIDs returns mockPids
      org.mockito.Mockito.doReturn(.123).when(controller).getCpu(anyLong)

      val result: Future[SimpleResult] = controller.getCpuUsage.apply(FakeRequest())
      val bodyJson = Json.parse(contentAsString(result))

      Json.stringify(bodyJson \ "cpu") must be equalTo "0.369"
    }
  }

  "StatsMonitor#getDiskUsage" should {
    "should return" in {
      val mockMongo0 = Seq("0")
      val mockMongo1 = Seq("1")
      val mockMongo2 = Seq("2")
      val mockMongos = Seq(mockMongo0, mockMongo1, mockMongo2)

      val controller = spy(new TestStatsMonitor())

      controller.getPIDs returns mockPids
      org.mockito.Mockito.doReturn(mockMongos).when(controller).getHostCmds
      org.mockito.Mockito.doReturn(12L * 1048576L).when(controller).getDisk(anyString)
      org.mockito.Mockito.doReturn(mockMongoResult(5 * 1048576)).when(controller).execute(mockMongo0)
      org.mockito.Mockito.doReturn(mockMongoResult(10 * 1048576)).when(controller).execute(mockMongo1)
      org.mockito.Mockito.doReturn(mockMongoResult(20 * 1048576)).when(controller).execute(mockMongo2)

      val result: Future[SimpleResult] = controller.getUsedDiskSpace.apply(FakeRequest())
      val bodyJson = Json.parse(contentAsString(result))

      Json.stringify(bodyJson \ "disk") must be equalTo "47.0"
    }
  }
  def mockMongoResult(size: Int): String = {
    return """THIS IS A TEST FOR REGEX "fileSize" : """ + size + "," + "TEST TEST TEST"
  }
}
