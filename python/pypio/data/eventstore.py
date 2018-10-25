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

from pyspark.sql.dataframe import DataFrame
from pyspark.sql import utils

__all__ = ["PEventStore"]


class PEventStore(object):

    def __init__(self, jss, sql_ctx):
        self._jss = jss
        self.sql_ctx = sql_ctx
        self._sc = sql_ctx and sql_ctx._sc

    def find(self, app_name, channel_name=None, start_time=None, until_time=None,
             entity_type=None, entity_id=None, event_names=None, target_entity_type=None,
             target_entity_id=None):
        pes = self._sc._jvm.org.apache.predictionio.data.store.python.PPythonEventStore
        jdf = pes.find(app_name, channel_name, start_time, until_time, entity_type, entity_id,
                       event_names, target_entity_type, target_entity_id, self._jss)
        return DataFrame(jdf, self.sql_ctx)

    def aggregate_properties(self, app_name, entity_type, channel_name=None,
                             start_time=None, until_time=None, required=None):
        pes = self._sc._jvm.org.apache.predictionio.data.store.python.PPythonEventStore
        jdf = pes.aggregateProperties(app_name, entity_type, channel_name,
                                      start_time, until_time,
                                      utils.toJArray(self._sc._gateway, self._sc._gateway.jvm.String, required),
                                      self._jss)
        return DataFrame(jdf, self.sql_ctx)


