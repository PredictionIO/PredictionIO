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

ARG SPARK_ARCHIVE
ARG SPARK_DIR
ARG PGSQL_JAR
ARG BUILD_PROFILE
ARG PIO_SCALA_VERSION
ARG PIO_SPARK_VERSION

ENV BUILD_PROFILE=$BUILD_PROFILE
ENV PIO_SCALA_VERSION=$PIO_SCALA_VERSION
ENV PIO_SPARK_VERSION=$PIO_SPARK_VERSION

# WORKAROUND: es-hadoop stops on RDD#take(1)
ADD docker-files/${SPARK_ARCHIVE} /vendors
RUN echo "spark.locality.wait.node 0s" > /vendors/${SPARK_DIR}/conf/spark-defaults.conf
ENV SPARK_HOME /vendors/${SPARK_DIR}

COPY docker-files/${PGSQL_JAR} /drivers/${PGSQL_JAR}
COPY docker-files/init.sh init.sh
COPY docker-files/set_build_profile.sh set_build_profile.sh
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
