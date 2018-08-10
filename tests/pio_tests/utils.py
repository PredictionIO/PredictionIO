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

import re
import time
import os
import requests
import json
from shutil import copyfile
from subprocess import run, Popen, check_output
from os.path import join as pjoin
import pio_tests.globals as globals

def srun(command):
  """ Runs a shell command given as a `str`
  Raises: `subprocess.CalledProcessError` when exit code != 0
  """
  return run(command, shell=True, stdout=globals.std_out(),
      stderr=globals.std_err(), check=True)

def srun_out(command):
  """ Runs a shell command given as a `str`
  Returns: string with command's output
  Raises: `subprocess.CalledProcessError` when exit code != 0
  """
  return check_output(command, shell=True, universal_newlines=True,
      stderr=globals.std_err())

def srun_bg(command):
  """ Runs a shell command given as a `str` in the background
  Returns: (obj: `subprocess.Popen`) for executed process
  """
  return Popen(command, shell=True, stdout=globals.std_out(),
      stderr=globals.std_err())

def repository_dirname(template):
  """ Utility function getting repository name from the link
  Example: for "https://github.com/user/SomeRepo" should return "SomeRepo"
  """
  return template.split('/')[-1]

def obtain_template(engine_dir, template):
  """Given a directory with engines and a template downloads an engine
  if necessary
  Args:
    engine_dir (str): directory where engines are stored
    template (str): either the name of an engine from the engines directory
        or a link to repository with the engine
  Returns: str with the engine's path
  """
  if re.match('^https?:\/\/', template):
    dest_dir = pjoin(engine_dir, repository_dirname(template))
    if not os.path.exists(dest_dir):
      srun('git clone --depth=1 {0} {1}'.format(template, dest_dir))
    return dest_dir
  else:
    # check if exists
    dest_dir = pjoin(engine_dir, template)
    if not os.path.exists(dest_dir):
      raise ValueError('Engine {0} does not exist in {1}'
          .format(template, engine_dir))

    return dest_dir

def pio_app_list():
  """Returns: a list of dicts for every application with the following keys:
      `name`, `id`, `access_key`, `allowed_events`
  """
  output = srun_out('pio app list').rstrip()
  return [ { 'name': line[2], 'id': int(line[4]),
             'access_key': line[6], 'allowed_events': line[8] }
          for line in [x.split() for x in output.split('\n')[1:-1]] ]

def get_app_eventserver_url_json(test_context):
  return 'http://{}:{}/events.json'.format(
      test_context.es_ip, test_context.es_port)

def get_engine_url_json(engine_ip, engine_port):
  return 'http://{}:{}/queries.json'.format(
      engine_ip, engine_port)

def send_event(event, test_context, access_key, channel=None):
  """ Sends an event to the eventserver
  Args:
    event: json-like dictionary describing an event
    test_context (obj: `TestContext`):
    access_key: application's access key
    channel (str): custom channel for storing event
  Returns: `requests.Response`
  """
  url = get_app_eventserver_url_json(test_context)
  params = { 'accessKey': access_key }
  if channel: params['channel'] = channel
  return requests.post(
      url,
      params=params,
      json=event)

def send_events_batch(events, test_context, access_key, channel=None):
  """ Send events in batch via REST to the eventserver
  Args:
    events: a list of json-like dictionaries for events
    test_context (obj: `TestContext`):
    access_key: application's access key
    channel (str): custom channel for storing event
  Returns: `requests.Response`
  Requires: Events length must not exceed length of 50
    http://predictionio.apache.org/datacollection/eventmodel/#3.-batch-events-to-the-eventserver
  """
  url = 'http://{}:{}/batch/events.json'.format(
      test_context.es_ip, test_context.es_port)
  params = { 'accessKey': access_key }
  if channel: params['channel'] = channel
  return requests.post(
      url,
      params=params,
      json=events)


def import_events_batch(events, test_context, appid, channel=None):
  """ Imports events in batch from file with `pio import`
  Args:
    events: a list of json-like dictionaries for events
    test_context (obj: `TestContext`)
    appid (int): application's id
    channel (str): custom channel for storing event
  """
  # Writing events list to temporary file.
  # `pio import` requires each line of input file to be a JSON string
  # representing an event. Empty lines are not allowed.
  contents = ''
  for ev in events:
      contents += '{}\n'.format(json.dumps(ev))
  contents.rstrip('\n')

  file_path = pjoin(test_context.data_directory, 'events.json.tmp')
  try:
      with open(file_path, 'w') as f:
          f.write(contents)
      srun('pio import --appid {} --input {} {} -- {}'.format(
          appid,
          file_path,
          '--channel {}'.format(channel) if channel else '',
          '--conf spark.sql.warehouse.dir=file:///tmp/spark-warehouse'))
  finally:
      os.remove(file_path)

def get_events(test_context, access_key, params={}):
  """ Gets events for some application
  Args:
    test_context (obj: `TestContext`)
    access_key (str):
    params (dict): special parameters for eventserver's GET, e.g:
        'limit', 'reversed', 'event'. See the docs
  Returns: `requests.Response`
  """
  url = get_app_eventserver_url_json(test_context)
  return requests.get(url, params=dict({'accessKey': access_key}, **params))

def query_engine(data, engine_ip='localhost', engine_port=8000):
  """ Send a query to deployed engine
  Args:
    data (dict): json-like dictionary being an input to an engine
    access_key (str):
    engine_ip (str): ip of deployed engine
    engine_port (int): port of deployed engine
  Returns: `requests.Response`
  """
  url = get_engine_url_json(engine_ip, engine_port)
  return requests.post(url, json=data)

class AppEngine:
  """ This is a utility class simplifying all app related interactions.
  Basically it is just a wrapper on other utility functions and shell
  scripts.
  """

  def __init__(self, test_context, app_context, already_created=False):
    """ Args:
        test_context (obj: `TestContext`)
        app_context (obj: `AppContext`)
        already_created (bool): True if the given app has been already added
    """
    self.test_context = test_context
    self.app_context = app_context
    self.engine_path = obtain_template(
        self.test_context.engine_directory, app_context.template)
    self.deployed_process = None
    if already_created:
      self.__init_info()
    else:
      self.id = None
      self.access_key = None
      self.description = None

    if self.app_context.engine_json_path:
      self.__copy_engine_json()

  def __copy_engine_json(self):
    to_path = pjoin(self.engine_path, 'engine.json')
    copyfile(self.app_context.engine_json_path, to_path)

  def __init_info(self):
    info = self.show()
    self.id = info['id']
    self.access_key = info['access_key']
    self.description = info['description']

  def new(self, id=None, description=None, access_key=None):
    """ Creates a new application with given parameters """
    srun('pio app new {} {} {} {}'.format(
        '--id {}'.format(id) if id else '',
        '--description \"{}\"'.format(description) if description else '',
        '--access-key {}'.format(access_key) if access_key else '',
        self.app_context.name))

    self.__init_info()


  def show(self):
    """ Returns: application info in dictionary with the keys:
         `name`: str, `id`: int, `description`: str,
         `access_key`: str, `allowed_events`: str
    """
    output = srun_out('pio app show {}'.format(self.app_context.name)).rstrip()
    lines = [x.split() for x in output.split('\n')]
    return { 'name': lines[0][3],
             'id': int(lines[1][4]),
             'description': lines[2][3] if len(lines[2]) >= 4 else '',
             'access_key': lines[3][4],
             'allowed_events': lines[3][5] }


  # deletes this app from pio
  def delete(self):
    srun('pio app delete {0} --force'.format(self.app_context.name))

  def build(self, sbt_extra=None, clean=False, no_asm=True, engine_dir=None):
    srun('cd {0}; pio build {1} {2} {3} {4}'.format(
        self.engine_path,
        '--sbt-extra {}'.format(sbt_extra) if sbt_extra else '',
        '--clean' if clean else '',
        '--no-asm' if no_asm else '',
        '--engine-dir {}'.format(engine_dir) if engine_dir else ''))

  def train(self, batch=None, skip_sanity_check=False, stop_after_read=False,
          stop_after_prepare=False, engine_factory=None,
          engine_params_key=None, scratch_uri=None, engine_dir=None):

    srun('cd {0}; pio train {1} {2} {3} {4} {5} {6} {7} {8}'.format(
        self.engine_path,
        '--batch {}'.format(batch) if batch else '',
        '--skip-sanity-check' if skip_sanity_check else '',
        '--stop-after-read' if stop_after_read else '',
        '--stop-after-prepare' if stop_after_prepare else '',
        '--engine_factory {}'.format(engine_factory) if engine_factory else '',
        '--engine-params-key {}'.format(engine_params_key) if engine_params_key else '',
        '--scratch-uri {}'.format(scratch_uri) if scratch_uri else '',
        '--engine-dir {}'.format(engine_dir) if engine_dir else ''))

  def deploy(self, wait_time=0, ip=None, port=None, engine_instance_id=None,
          feedback=False, accesskey=None, event_server_ip=None, event_server_port=None,
          batch=None, scratch_uri=None, engine_dir=None):

    command = 'cd {0}; pio deploy {1} {2} {3} {4} {5} {6} {7} {8} {9} {10}'.format(
            self.engine_path,
            '--ip {}'.format(ip) if ip else '',
            '--port {}'.format(port) if port else '',
            '--engine-instance-id {}'.format(engine_instance_id) if engine_instance_id else '',
            '--feedback' if feedback else '',
            '--accesskey {}'.format(accesskey) if accesskey else '',
            '--event-server-ip {}'.format(event_server_ip) if event_server_ip else '',
            '--event-server-port {}'.format(event_server_port) if event_server_port else '',
            '--batch {}'.format(bach) if batch else '',
            '--scratch-uri {}'.format(scratch_uri) if scratch_uri else '',
            '--engine-dir {}'.format(engine_dir) if engine_dir else '')

    self.deployed_process = srun_bg(command)
    time.sleep(wait_time)
    if self.deployed_process.poll() is not None:
      raise Exception('Application engine terminated')
    self.ip = ip if ip else 'localhost'
    self.port = port if port else 8000

  def stop(self):
    """ Kills deployed engine """
    if self.deployed_process:
      self.deployed_process.kill()

  def new_channel(self, channel):
    srun('pio app channel-new {0}'.format(channel))

  def delete_channel(self, channel):
    srun('pio app channel-delete {0} --force'.format(channel))

  def send_event(self, event):
    return send_event(event, self.test_context, self.access_key)

  def send_events_batch(self, events):
    return send_events_batch(events, self.test_context, self.access_key)

  def import_events_batch(self, events):
    return import_events_batch(events, self.test_context, self.id)

  def get_events(self, params={}):
    return get_events(self.test_context, self.access_key, params)

  def delete_data(self, delete_all=True, channel=None):
    srun('pio app data-delete {0} {1} {2} --force'
        .format(
            self.app_context.name,
            '--all' if delete_all else '',
            '--channel ' + channel if channel is not None else ''))

  def query(self, data):
    return query_engine(data, self.ip, self.port)
