/** Copyright 2015 TappingStone, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package org.apache.predictionio.data.api

import org.apache.predictionio.data.storage.Storage

import akka.testkit.TestProbe
import akka.actor.ActorSystem
import akka.actor.Props

import spray.http.HttpEntity
import spray.http.HttpResponse
import spray.http.ContentTypes
import spray.httpx.RequestBuilding.Get

import org.specs2.mutable.Specification

class EventServiceSpec extends Specification {

  val system = ActorSystem("EventServiceSpecSystem")

  val eventClient = Storage.getLEvents()
  val accessKeysClient = Storage.getMetaDataAccessKeys()
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

  "GET / request" should {
    "properly produce OK HttpResponses" in {
      val probe = TestProbe()(system)
      probe.send(eventServiceActor, Get("/"))
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

  step(system.shutdown())
}
