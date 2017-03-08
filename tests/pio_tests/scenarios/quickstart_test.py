#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import os
import unittest
import random
import logging
from pio_tests.integration import BaseTestCase, AppContext
from utils import AppEngine, srun, pjoin

def read_events(file_path):
  RATE_ACTIONS_DELIMITER = "::"
  with open(file_path, 'r') as f:
    events = []
    for line in f:
      data = line.rstrip('\r\n').split(RATE_ACTIONS_DELIMITER)
      if random.randint(0, 1) == 1:
        events.append( {
          "event": "rate",
          "entityType": "user",
          "entityId": data[0],
          "targetEntityType": "item",
          "targetEntityId": data[1],
          "properties": { "rating" : float(data[2]) } })
      else:
        events.append({
          "event": "buy",
          "entityType": "user",
          "entityId": data[0],
          "targetEntityType": "item",
          "targetEntityId": data[1] })

    return events


class QuickStartTest(BaseTestCase):

  def setUp(self):
    self.log.info("Setting up the engine")

    template_path = pjoin(
        self.test_context.engine_directory, "recommendation-engine")
    engine_json_path = pjoin(
        self.test_context.data_directory, "quickstart_test/engine.json")

    self.training_data_path = pjoin(
        self.test_context.data_directory,
        "quickstart_test/training_data.txt")

    # downloading training data
    srun('curl https://raw.githubusercontent.com/apache/spark/master/' \
            'data/mllib/sample_movielens_data.txt --create-dirs -o {}'
            .format(self.training_data_path))

    app_context = AppContext(
        name="MyRecommender",
        template=template_path,
        engine_json_path=engine_json_path)

    self.app = AppEngine(self.test_context, app_context)

  def engine_dir_test(self):
    self.log.info("Stopping deployed engine")
    self.app.stop()

    self.log.info("Creating dummy directory")
    engine_path = self.app.engine_path
    dummy_path = "{}/dummy".format(engine_path)
    srun("mkdir -p {}".format(dummy_path))

    self.log.info("Testing pio commands in dummy directory with " +
      "--engine-dir argument")
    self.app.engine_path = dummy_path
    self.log.info("Building an engine...")
    self.app.build(engine_dir=engine_path)
    self.log.info("Training...")
    self.app.train(engine_dir=engine_path)
    self.log.info("Deploying and waiting 30s for it to start...")
    self.app.deploy(wait_time=30, engine_dir=engine_path)

    self.log.info("Sending a single query and checking results")
    user_query = { "user": 1, "num": 4 }
    r = self.app.query(user_query)
    self.assertEqual(200, r.status_code)
    result = r.json()
    self.assertEqual(4, len(result['itemScores']))

    self.log.info("Deleting dummy directory")
    srun("rm -rf {}".format(dummy_path))
    self.app.engine_path = engine_path

  def runTest(self):
    self.log.info("Adding a new application")
    self.app.new()

    event1 = {
      "event" : "rate",
      "entityType" : "user",
      "entityId" : "u0",
      "targetEntityType" : "item",
      "targetEntityId" : "i0",
      "properties" : {
        "rating" : 5
      },
      "eventTime" : "2014-11-02T09:39:45.618-08:00" }

    event2 = {
      "event" : "buy",
      "entityType" : "user",
      "entityId" : "u1",
      "targetEntityType" : "item",
      "targetEntityId" : "i2",
      "eventTime" : "2014-11-10T12:34:56.123-08:00" }

    self.log.info("Sending two test events")
    self.assertListEqual(
        [201, 201],
        [self.app.send_event(e).status_code for e in [event1, event2]])

    self.log.info("Checking the number of events stored on the server")
    r = self.app.get_events()
    self.assertEquals(200, r.status_code)
    stored_events = r.json()
    self.assertEqual(2, len(stored_events))

    self.log.info("Importing many events")
    new_events = read_events(self.training_data_path)
    for ev in new_events:
      r = self.app.send_event(ev)
      self.assertEqual(201, r.status_code)

    self.log.info("Checking the number of events stored on eventserver")
    r = self.app.get_events(params={'limit': -1})
    self.assertEquals(200, r.status_code)
    stored_events = r.json()
    self.assertEquals(len(new_events) + 2, len(stored_events))

    self.log.info("Building an engine...")
    self.app.build()
    self.log.info("Training...")
    self.app.train()
    self.log.info("Deploying and waiting 35s for it to start...")
    self.app.deploy(wait_time=35)

    self.log.info("Testing pio commands outside of engine directory")
    self.engine_dir_test()

    self.log.info("Sending a single query and checking results")
    user_query = { "user": 1, "num": 4 }
    r = self.app.query(user_query)
    self.assertEqual(200, r.status_code)
    result = r.json()
    self.assertEqual(4, len(result['itemScores']))

  def tearDown(self):
    self.log.info("Stopping deployed engine")
    self.app.stop()
    self.log.info("Deleting all related data")
    self.app.delete_data()
    self.log.info("Removing an app")
    self.app.delete()
