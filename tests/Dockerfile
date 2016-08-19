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

from ubuntu

ENV SPARK_VERSION 1.4.0
ENV ELASTICSEARCH_VERSION 1.4.4
ENV HBASE_VERSION 1.0.0

RUN echo "== Updating system =="
RUN apt-get update -y
RUN echo "== Downloading packages =="
RUN apt-get install -y \
    wget curl \
    python-pip \
    python3-pip \
    postgresql postgresql-contrib \
    openjdk-8-jdk \
    openssh-client openssh-server

RUN pip install predictionio
RUN pip3 install --upgrade pip
RUN pip3 install xmlrunner
RUN pip3 install --upgrade requests
RUN pip3 install --upgrade urllib3

ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64/jre

RUN echo "== Installing Spark =="
RUN mkdir vendors
RUN wget http://d3kbcqa49mib13.cloudfront.net/spark-${SPARK_VERSION}-bin-hadoop2.6.tgz
RUN tar zxvfC spark-${SPARK_VERSION}-bin-hadoop2.6.tgz /vendors
RUN rm spark-${SPARK_VERSION}-bin-hadoop2.6.tgz
ENV SPARK_HOME /vendors/spark-${SPARK_VERSION}-bin-hadoop2.6

RUN echo "== Installing Elasticsearch =="
RUN wget https://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-${ELASTICSEARCH_VERSION}.tar.gz
RUN tar zxvfC elasticsearch-${ELASTICSEARCH_VERSION}.tar.gz /vendors
RUN rm elasticsearch-${ELASTICSEARCH_VERSION}.tar.gz
ENV ELASTICSEARCH_HOME /vendors/elasticsearch-${ELASTICSEARCH_VERSION}

RUN echo "== Installing HBase =="
RUN wget http://archive.apache.org/dist/hbase/hbase-${HBASE_VERSION}/hbase-${HBASE_VERSION}-bin.tar.gz
RUN tar zxvfC hbase-${HBASE_VERSION}-bin.tar.gz /vendors
RUN rm hbase-${HBASE_VERSION}-bin.tar.gz
ENV HBASE_HOME /vendors/hbase-${HBASE_VERSION}

RUN echo "== Downloading database drivers =="
RUN mkdir drivers
RUN wget https://jdbc.postgresql.org/download/postgresql-9.4-1204.jdbc41.jar -P /drivers

RUN mkdir PredictionIO
ENV PIO_HOME /PredictionIO
ENV PATH ${PIO_HOME}/bin/:${PATH}
ENV HOST_PIO_HOME /pio_host

RUN echo "== Setting configs =="
COPY docker-files/init.sh init.sh
COPY docker-files/env-conf/spark-env.sh ${SPARK_HOME}/conf/spark-env.sh
COPY docker-files/env-conf/hbase-site.xml ${HBASE_HOME}/conf/hbase-site.xml
COPY docker-files/env-conf/pio-env.sh /pio-env.sh

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
# spark master UI
EXPOSE 8080
# spark worker UI
EXPOSE 8081
# spark context UI
EXPOSE 4040
# HMaster
EXPOSE 60000
# HMaster Info Web UI
EXPOSE 60010
# Region Server
Expose 60020
# Region Server Http
EXPOSE 60030

ENTRYPOINT ["/init.sh"]
CMD 'bash'
