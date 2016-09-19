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

ARG SPARK_VERSION=2.0.0
ARG HADOOP_VERSION=2.7.3
ARG SPARK_VERSION_OLD=1.6.2
ARG HADOOP_VERSION_OLD=2.6.4
ARG ELASTICSEARCH_VERSION=1.4.4
ARG HBASE_VERSION=1.0.0

ENV SPARK_VERSION ${SPARK_VERSION}
ENV HADOOP_VERSION ${HADOOP_VERSION}
ENV SPARK_VERSION_OLD ${SPARK_VERSION_OLD}
ENV HADOOP_VERSION_OLD ${HADOOP_VERSION_OLD}
ENV ELASTICSEARCH_VERSION ${ELASTICSEARCH_VERSION}
ENV HBASE_VERSION ${HBASE_VERSION}

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

RUN echo "== Installing vendors =="
RUN mkdir /vendors
RUN mkdir /utils
COPY docker-files/vendors.sh /utils/
COPY docker-files/install-vendors.sh /utils/
RUN /utils/install-vendors.sh /vendors /utils/vendors.sh

RUN echo "== Downloading database drivers =="
RUN mkdir drivers
RUN wget https://jdbc.postgresql.org/download/postgresql-9.4.1209.jar -P /drivers

RUN mkdir PredictionIO
ENV PIO_HOME /PredictionIO
ENV PATH ${PIO_HOME}/bin/:${PATH}
ENV HOST_PIO_HOME /pio_host

RUN echo "== Setting configs =="
COPY docker-files/init.sh init.sh
COPY docker-files/env-conf/spark-env.sh ${SPARK_HOME}/conf/spark-env.sh
COPY docker-files/env-conf/hbase-site.xml ${HBASE_HOME}/conf/hbase-site.xml
COPY docker-files/env-conf/pio-env.sh /pio-env.sh

COPY docker-files/BuildInfoPrinter.java /utils/BuildInfoPrinter.java

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
