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
import time
from subprocess import CalledProcessError
from pio_tests.integration import BaseTestCase, AppContext
from utils import *

ITEMS_COUNT = 12

def get_buy_events(users, per_user=2):
  events = []
  for u in range(users):
    items = set([random.randint(0, ITEMS_COUNT) for i in range(per_user)])
    for item in items:
      events.append({
        "event": "buy",
        "entityType": "user",
        "entityId": u,
        "targetEntityType": "item",
        "targetEntityId": item })

  return events

def get_rate_events(users, per_user=2):
  events = []
  for u in range(users):
    items = set([random.randint(0, ITEMS_COUNT) for i in range(per_user)])
    for item in items:
      events.append( {
        "event": "rate",
        "entityType": "user",
        "entityId": u,
        "targetEntityType": "item",
        "targetEntityId": item,
        "properties": { "rating" : float(random.randint(1,5)) } })

  return events


class BasicAppUsecases(BaseTestCase):

  def setUp(self):
    random.seed(3)
    self.log.info("Setting up the engine")

    template_path = pjoin(
        self.test_context.engine_directory, "recommendation-engine")
    engine_json_path = pjoin(
        self.test_context.data_directory, "quickstart_test/engine.json")

    app_context = AppContext(
        name="MyRecommender",
        template=template_path,
        engine_json_path=engine_json_path)

    self.app = AppEngine(self.test_context, app_context)

  def runTest(self):
    self.app_creation()
    self.check_app_list()
    self.check_data()
    self.check_build()
    self.check_train_and_deploy()

  def app_creation(self):
    self.log.info("Adding a new application")
    description = "SomeDescription"
    self.app.new(description=description)
    self.assertEqual(description, self.app.description)

    self.log.info("Creating an app again - should fail")
    self.assertRaises(CalledProcessError, lambda : self.app.new())

  def check_app_list(self):
    self.log.info("Checking if app is on the list")
    apps = pio_app_list()
    self.assertEqual(1,
        len([a for a in apps if a['name'] == self.app.app_context.name]))

  def check_data(self):
    self.log.info("Importing events")
    buy_events = get_buy_events(20, 1)
    rate_events = get_rate_events(20, 1)

    for ev in buy_events + rate_events:
      self.assertEquals(201, self.app.send_event(ev).status_code)

    self.log.info("Checking imported events")
    r = self.app.get_events(params={'limit': -1})
    self.assertEqual(200, r.status_code)
    self.assertEqual(len(buy_events) + len(rate_events), len(r.json()))

    self.log.info("Deleting entire data")
    self.app.delete_data()
    self.log.info("Checking if there are no events at all")
    r = self.app.get_events(params={'limit': -1})
    self.assertEqual(404, r.status_code)

  def check_build(self):
    self.log.info("Clean build")
    self.app.build(clean=True)
    self.log.info("Second build")
    self.app.build()

  def check_train_and_deploy(self):
    self.log.info("import some data first")
    buy_events = get_buy_events(20, 5)
    rate_events = get_rate_events(20, 5)
    for ev in buy_events + rate_events:
      self.assertEquals(201, self.app.send_event(ev).status_code)

    self.log.info("Training")
    self.app.train()
    self.log.info("Deploying")
    self.app.deploy()
    self.assertFalse(self.app.deployed_process.poll())

    self.log.info("Importing more events")
    buy_events = get_buy_events(60, 5)
    rate_events = get_rate_events(60, 5)
    for ev in buy_events + rate_events:
      self.assertEquals(201, self.app.send_event(ev).status_code)

    self.log.info("Training again")
    self.app.train()

    time.sleep(7)

    self.log.info("Check serving")
    r = self.app.query({"user": 1, "num": 5})
    self.assertEqual(200, r.status_code)
    result = r.json()
    self.assertEqual(5, len(result['itemScores']))
    r = self.app.query({"user": 5, "num": 3})
    self.assertEqual(200, r.status_code)
    result = r.json()
    self.assertEqual(3, len(result['itemScores']))

    self.log.info("Remove data")
    self.app.delete_data()
    self.log.info("Retraining should fail")
    self.assertRaises(CalledProcessError, lambda: self.app.train())


  def tearDown(self):
    self.log.info("Stopping deployed engine")
    self.app.stop()
    self.log.info("Deleting all related data")
    self.app.delete_data()
    self.log.info("Removing an app")
    self.app.delete()

