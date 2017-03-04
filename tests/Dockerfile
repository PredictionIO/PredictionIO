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

FROM predictionio/pio

ENV SPARK_VERSION 1.6.3
ENV ELASTICSEARCH_VERSION 5.2.1
ENV HBASE_VERSION 1.0.0

ADD docker-files/spark-${SPARK_VERSION}-bin-hadoop2.6.tgz /vendors
# WORKAROUND: es-hadoop stops on RDD#take(1)
RUN echo "spark.locality.wait.node 0s" > /vendors/spark-${SPARK_VERSION}-bin-hadoop2.6/conf/spark-defaults.conf
ENV SPARK_HOME /vendors/spark-${SPARK_VERSION}-bin-hadoop2.6

COPY docker-files/postgresql-9.4-1204.jdbc41.jar /drivers/postgresql-9.4-1204.jdbc41.jar
COPY docker-files/init.sh init.sh
COPY docker-files/env-conf/hbase-site.xml ${PIO_HOME}/conf/hbase-site.xml
COPY docker-files/env-conf/pio-env.sh ${PIO_HOME}/conf/pio-env.sh
COPY docker-files/pgpass /root/.pgpass
RUN chmod 600 /root/.pgpass

# Python
RUN pip install python-dateutil
RUN pip install pytz

# Default repositories setup
ENV PIO_STORAGE_REPOSITORIES_METADATA_SOURCE PGSQL
ENV PIO_STORAGE_REPOSITORIES_EVENTDATA_SOURCE PGSQL
ENV PIO_STORAGE_REPOSITORIES_MODELDATA_SOURCE PGSQL

# JVM settings
ENV JVM_OPTS '-Dfile.encoding=UTF8 -Xms2048M -Xmx2048M -Xss8M -XX:MaxPermSize=512M -XX:ReservedCodeCacheSize=256M'

# Expose relevant ports
# pio engine
EXPOSE 8000
# eventserver
EXPOSE 7070

ENV SLEEP_TIME 30

ENTRYPOINT ["/init.sh"]
CMD 'bash'
