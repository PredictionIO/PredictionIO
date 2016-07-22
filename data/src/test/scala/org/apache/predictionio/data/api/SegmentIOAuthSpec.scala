package org.apache.predictionio.data.api

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe
import org.apache.predictionio.data.storage._
import org.joda.time.DateTime
import org.specs2.mutable.Specification
import spray.http.HttpHeaders.RawHeader
import spray.http.{ContentTypes, HttpEntity, HttpResponse}
import spray.httpx.RequestBuilding._
import sun.misc.BASE64Encoder

import scala.concurrent.{Future, ExecutionContext}

class SegmentIOAuthSpec extends Specification {

  val system = ActorSystem("EventServiceSpecSystem")
  sequential
  isolated
  val eventClient = new LEvents {
    override def init(appId: Int, channelId: Option[Int]): Boolean = true

    override def futureInsert(event: Event, appId: Int, channelId: Option[Int])
        (implicit ec: ExecutionContext): Future[String] =
      Future successful "event_id"

    override def futureFind(
      appId: Int, channelId: Option[Int], startTime: Option[DateTime],
      untilTime: Option[DateTime], entityType: Option[String],
      entityId: Option[String], eventNames: Option[Seq[String]],
      targetEntityType: Option[Option[String]],
      targetEntityId: Option[Option[String]], limit: Option[Int],
      reversed: Option[Boolean])
        (implicit ec: ExecutionContext): Future[Iterator[Event]] =
      Future successful List.empty[Event].iterator

    override def futureGet(eventId: String, appId: Int, channelId: Option[Int])
        (implicit ec: ExecutionContext): Future[Option[Event]] =
      Future successful None

    override def remove(appId: Int, channelId: Option[Int]): Boolean = true

    override def futureDelete(eventId: String, appId: Int, channelId: Option[Int])
        (implicit ec: ExecutionContext): Future[Boolean] =
      Future successful true

    override def close(): Unit = {}
  }
  val appId = 0
  val accessKeysClient = new AccessKeys {
    override def insert(k: AccessKey): Option[String] = null
    override def getByAppid(appid: Int): Seq[AccessKey] = null
    override def update(k: AccessKey): Unit = {}
    override def delete(k: String): Unit = {}
    override def getAll(): Seq[AccessKey] = null

    override def get(k: String): Option[AccessKey] =
      k match {
        case "abc" ⇒ Some(AccessKey(k, appId, Seq.empty))
        case _ ⇒ None
      }
  }

  val channelsClient = Storage.getMetaDataChannels()
  val eventServiceActor = system.actorOf(
    Props(
      new EventServiceActor(
        eventClient,
        accessKeysClient,
        channelsClient,
        EventServerConfig()
      )
    )
  )

  val base64Encoder = new BASE64Encoder

  "Event Service" should {

    "reject with CredentialsRejected with invalid credentials" in {
      val accessKey = "abc123:"
      val probe = TestProbe()(system)
      probe.send(
        eventServiceActor,
        Post("/webhooks/segmentio.json")
          .withHeaders(
            List(
              RawHeader("Authorization", s"Basic $accessKey")
            )
          )
      )
      probe.expectMsg(
        HttpResponse(
          401,
          HttpEntity(
            contentType = ContentTypes.`application/json`,
            string = """{"message":"Invalid accessKey."}"""
          )
        )
      )
      success
    }

    "reject with CredentialsMissed without credentials" in {
      val probe = TestProbe()(system)
      probe.send(
        eventServiceActor,
        Post("/webhooks/segmentio.json")
      )
      probe.expectMsg(
        HttpResponse(
          401,
          HttpEntity(
            contentType = ContentTypes.`application/json`,
            string = """{"message":"Missing accessKey."}"""
          )
        )
      )
      success
    }

    "process SegmentIO identity request properly" in {
      val jsonReq =
        """
          |{
          |  "anonymous_id": "507f191e810c19729de860ea",
          |  "channel": "browser",
          |  "context": {
          |    "ip": "8.8.8.8",
          |    "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_5)"
          |  },
          |  "message_id": "022bb90c-bbac-11e4-8dfc-aa07a5b093db",
          |  "timestamp": "2015-02-23T22:28:55.387Z",
          |  "sent_at": "2015-02-23T22:28:55.111Z",
          |  "traits": {
          |    "name": "Peter Gibbons",
          |    "email": "peter@initech.com",
          |    "plan": "premium",
          |    "logins": 5
          |  },
          |  "type": "identify",
          |  "user_id": "97980cfea0067",
          |  "version": "2"
          |}
        """.stripMargin

      val accessKey = "abc:"
      val accessKeyEncoded = base64Encoder.encodeBuffer(accessKey.getBytes)
      val probe = TestProbe()(system)
      probe.send(
        eventServiceActor,
        Post(
          "/webhooks/segmentio.json",
          HttpEntity(ContentTypes.`application/json`, jsonReq.getBytes)
        ).withHeaders(
            List(
              RawHeader("Authorization", s"Basic $accessKeyEncoded")
            )
          )
      )
      probe.expectMsg(
        HttpResponse(
          201,
          HttpEntity(
            contentType = ContentTypes.`application/json`,
            string = """{"eventId":"event_id"}"""
          )
        )
      )
      success
    }
  }

  step(system.shutdown())
}
