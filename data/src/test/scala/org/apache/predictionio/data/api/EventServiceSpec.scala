/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.predictionio.data.api

import akka.event.Logging
import org.apache.predictionio.data.storage.Storage
import org.specs2.mutable.Specification
import akka.http.scaladsl.testkit.Specs2RouteTest


class EventServiceSpec extends Specification with Specs2RouteTest {
  val eventClient = Storage.getLEvents()
  val accessKeysClient = Storage.getMetaDataAccessKeys()
  val channelsClient = Storage.getMetaDataChannels()

  val statsActorRef = system.actorSelection("/user/StatsActor")
  val pluginsActorRef = system.actorSelection("/user/PluginsActor")

  val logger = Logging(system, getClass)
  val config = EventServerConfig(ip = "0.0.0.0", port = 7070)

  val route = EventServer.createRoute(
    eventClient,
    accessKeysClient,
    channelsClient,
    logger,
    statsActorRef,
    pluginsActorRef,
    config
  )

  "GET / request" should {
    "properly produce OK HttpResponses" in {
      Get() ~> route ~> check {
        status.intValue() shouldEqual 200
        responseAs[String] shouldEqual """{"status":"alive"}"""
      }
    }
  }
}
