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

FROM openjdk:8

ARG PIO_GIT_URL=https://github.com/apache/predictionio.git
ARG PIO_TAG=v0.13.0
ENV SCALA_VERSION=2.11.12
ENV SPARK_VERSION=2.2.3
ENV HADOOP_VERSION=2.7.7
ENV ELASTICSEARCH_VERSION=5.5.3
ENV PGSQL_VERSION=42.2.4
ENV MYSQL_VERSION=8.0.12
ENV PIO_HOME=/usr/share/predictionio

RUN apt-get update && \
    apt-get install -y dpkg-dev fakeroot && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /opt/src
RUN git clone -b $PIO_TAG $PIO_GIT_URL
WORKDIR /opt/src/predictionio
RUN bash ./make-distribution.sh \
      -Dscala.version=$SCALA_VERSION \
      -Dspark.version=$SPARK_VERSION \
      -Dhadoop.version=$HADOOP_VERSION \
      -Delasticsearch.version=$ELASTICSEARCH_VERSION \
      --with-deb && \
    dpkg -i ./assembly/target/predictionio_*.deb && \
    cp -r ./python /usr/share/predictionio && \
    mkdir /var/log/predictionio && \
    rm -rf /opt/src/predictionio/*


RUN cp /etc/predictionio/pio-env.sh /etc/predictionio/pio-env.sh.orig && \
    echo "#!/usr/bin/env bash" > /etc/predictionio/pio-env.sh
RUN curl -o $PIO_HOME/lib/postgresql-$PGSQL_VERSION.jar \
    http://central.maven.org/maven2/org/postgresql/postgresql/$PGSQL_VERSION/postgresql-$PGSQL_VERSION.jar && \
    echo "POSTGRES_JDBC_DRIVER=$PIO_HOME/lib/postgresql-$PGSQL_VERSION.jar" >> /etc/predictionio/pio-env.sh && \
    echo "MYSQL_JDBC_DRIVER=$PIO_HOME/lib/mysql-connector-java-$MYSQL_VERSION.jar" >> /etc/predictionio/pio-env.sh

WORKDIR /usr/share
RUN curl -o /opt/src/spark-$SPARK_VERSION.tgz \
    http://archive.apache.org/dist/spark/spark-$SPARK_VERSION/spark-$SPARK_VERSION-bin-hadoop2.7.tgz && \
    tar zxvf /opt/src/spark-$SPARK_VERSION.tgz && \
    echo "SPARK_HOME="`pwd`/`ls -d spark*` >> /etc/predictionio/pio-env.sh && \
    rm -rf /opt/src

WORKDIR /templates
ADD pio_run /usr/bin/pio_run

EXPOSE 7070
EXPOSE 8000

CMD ["sh", "/usr/bin/pio_run"]

