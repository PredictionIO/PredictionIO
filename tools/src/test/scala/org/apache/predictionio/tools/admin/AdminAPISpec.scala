package org.apache.predictionio.tools.admin

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe
import org.apache.predictionio.data.storage.Storage
import org.specs2.mutable.Specification
import spray.http._
import spray.httpx.RequestBuilding._
import spray.util._


class AdminAPISpec extends Specification{

  val system = ActorSystem(Utils.actorSystemNameFrom(getClass))
  val config = AdminServerConfig(
    ip = "localhost",
    port = 7071)

  val commandClient = new CommandClient(
    appClient = Storage.getMetaDataApps,
    accessKeyClient = Storage.getMetaDataAccessKeys,
    eventClient = Storage.getLEvents()
  )

  val adminActor= system.actorOf(Props(classOf[AdminServiceActor], commandClient))

  "GET / request" should {
    "properly produce OK HttpResponses" in {
      val probe = TestProbe()(system)
      probe.send(adminActor, Get("/"))

      probe.expectMsg(
        HttpResponse(
          200,
          HttpEntity(
            contentType = ContentTypes.`application/json`,
            string = """{"status":"alive"}"""
          )
        )
      )
      success
    }
  }

  "GET /cmd/app request" should {
    "properly produce OK HttpResponses" in {
      /*
      val probe = TestProbe()(system)
      probe.send(adminActor,Get("/cmd/app"))

      //TODO: Need to convert the response string to the corresponding case object to assert some properties on the object
      probe.expectMsg(
        HttpResponse(
          200,
          HttpEntity(
            contentType = ContentTypes.`application/json`,
            string = """{"status":1}"""
          )
        )
      )*/
      pending
    }
  }

  step(system.shutdown())
}
