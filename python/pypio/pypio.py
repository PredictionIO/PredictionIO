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

from __future__ import absolute_import

import sys

from pypio.data import PEventStore
from pyspark.sql import SparkSession
from pyspark.sql import utils


def init():
    global spark
    spark = SparkSession.builder.getOrCreate()
    global sc
    sc = spark.sparkContext
    global sqlContext
    sqlContext = spark._wrapped
    global p_event_store
    p_event_store = PEventStore(spark._jsparkSession, sqlContext)
    print("Initialized pypio")


def find_events(app_name):
    return p_event_store.find(app_name)


def save_model(model):
    engine = sc._jvm.org.apache.predictionio.e2.engine.PythonEngine
    engine.model().set(model._to_java())
    main_args = utils.toJArray(sc._gateway, sc._gateway.jvm.String, sys.argv)
    create_workflow = sc._jvm.org.apache.predictionio.workflow.CreateWorkflow
    spark.stop()
    create_workflow.main(main_args)
