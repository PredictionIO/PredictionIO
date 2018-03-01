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

import unittest
import requests
import json
import argparse
import dateutil.parser
import pytz
from subprocess import Popen
from utils import AppEngine, pjoin
from pio_tests.integration import BaseTestCase, AppContext

class EventserverTest(BaseTestCase):
  """ Integration test for PredictionIO Eventserver API
  Refer to below for further information:
    http://predictionio.apache.org/datacollection/eventmodel/
    http://predictionio.apache.org/datacollection/eventapi/
  """
  # Helper methods
  def eventserver_url(self, path=None):
    url = 'http://{}:{}'.format(
            self.test_context.es_ip, self.test_context.es_port)
    if path: url += '/{}'.format(path)
    return url

  def load_events(self, json_file):
    file_path = pjoin(self.test_context.data_directory,
        'eventserver_test/{}'.format(json_file))
    return json.loads(open(file_path).read())


  def setUp(self):
    template_path = pjoin(
        self.test_context.engine_directory, "recommendation-engine")
    app_context = AppContext(
        name="MyRecommender",
        template=template_path)
    self.app = AppEngine(self.test_context, app_context)

  def runTest(self):
    self.log.info("Check if Eventserver is alive and running")
    r = requests.get(self.eventserver_url())
    self.assertDictEqual(r.json(), {"status": "alive"})

    self.log.info("Cannot view events with empty accessKey")
    r = requests.get(self.eventserver_url(path='events.json'))
    self.assertDictEqual(r.json(), {"message": "Missing accessKey."})

    self.log.info("Cannot view events with invalid accessKey")
    r = requests.get(self.eventserver_url(path='events.json'),
        params={'accessKey': ''})
    self.assertDictEqual(r.json(), {"message": "Invalid accessKey."})

    self.log.info("Adding new pio application")
    self.app.new()

    self.log.info("No events have been sent yet")
    r = self.app.get_events()
    self.assertDictEqual(r.json(), {"message": "Not Found"})

    # Testing POST
    self.log.info("Sending single event")
    event1 = {
      'event' : 'test',
      'entityType' : 'test',
      'entityId' : 't1'
    }
    r = self.app.send_event(event1)
    self.assertEqual(201, r.status_code)

    self.log.info("Sending batch of events")
    r = self.app.send_events_batch(
        self.load_events("rate_events_25.json"))
    self.assertEqual(200, r.status_code)

    self.log.info("Cannot send more than 50 events per batch")
    r = self.app.send_events_batch(
        self.load_events("signup_events_51.json"))
    self.assertEqual(400, r.status_code)

    self.log.info("Importing events from file does not have batch size limit")
    self.app.import_events_batch(
        self.load_events("signup_events_51.json"))

    self.log.info("Individual events may fail when sending events as batch")
    r = self.app.send_events_batch(
        self.load_events("partially_malformed_events.json"))
    self.assertEqual(200, r.status_code)
    self.assertEqual(201, r.json()[0]['status'])
    self.assertEqual(400, r.json()[1]['status'])

    # Testing GET for different parameters
    params = {'event': 'rate'}
    r = self.app.get_events(params=params)
    self.assertEqual(20, len(r.json()))
    self.assertEqual('rate', r.json()[0]['event'])

    params = {
      'event': 'rate',
      'limit': -1 }
    r = self.app.get_events(params=params)
    self.assertEqual(25, len(r.json()))
    self.assertEqual('rate', r.json()[0]['event'])

    params = {
      'event': 'rate',
      'limit': 10 }
    r = self.app.get_events(params=params)
    self.assertEqual(10, len(r.json()))
    self.assertEqual('rate', r.json()[0]['event'])

    params = {
      'event': 'rate',
      'entityType': 'user',
      'entityId': '1' }
    r = self.app.get_events(params=params)
    self.assertEqual(5, len(r.json()))
    self.assertEqual('1', r.json()[0]['entityId'])

    params = {
      'event': 'rate',
      'targetEntityType': 'item',
      'targetEntityId': '1' }
    r = self.app.get_events(params=params)
    self.assertEqual(5, len(r.json()))
    self.assertEqual('1', r.json()[0]['targetEntityId'])

    params = {
      'event': 'rate',
      'entityType': 'user',
      'entityId': '1',
      'startTime': '2014-11-01T09:39:45.618-08:00',
      'untilTime': '2014-11-04T09:39:45.618-08:00' }
    r = self.app.get_events(params=params)
    self.assertEqual(3, len(r.json()))
    self.assertEqual('1', r.json()[0]['entityId'])

    params = {
      'event': 'rate',
      'entityType': 'user',
      'entityId': '1',
      'reversed': 'true' }
    r = self.app.get_events(params=params)
    self.assertEqual(5, len(r.json()))
    event_time = dateutil.parser.parse(r.json()[0]['eventTime']).astimezone(pytz.utc)
    self.assertEqual('2014-11-05 17:39:45.618000+00:00', str(event_time))

  def tearDown(self):
    self.log.info("Deleting all app data")
    self.app.delete_data()
    self.log.info("Deleting app")
    self.app.delete()


if __name__ == '__main__':
  suite = unittest.TestSuite([BasicEventserverTest])
  result = unittest.TextTestRunner(verbosity=2).run(suite)
  if not result.wasSuccessful():
    sys.exit(1)
