/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.predictionio.core.BuildInfo;
import java.util.Map;
import java.lang.Iterable;

class BuildInfoPrinter {
  public static void main(String[] args) {
    System.out.println("PIO_VERSION=" + BuildInfo.version());
    System.out.println("PIO_SCALA_VERSION=" + BuildInfo.scalaVersion());
    System.out.println("PIO_SBT_VERSION=" + BuildInfo.sbtVersion());
    System.out.println("PIO_SPARK_VERSION=" + BuildInfo.sparkVersion());
    System.out.println("PIO_HADOOP_VERSION=" + BuildInfo.hadoopVersion());
    System.out.println("PIO_ELASTICSEARCH_VERSION=" + BuildInfo.elasticsearchVersion());
  }
}
