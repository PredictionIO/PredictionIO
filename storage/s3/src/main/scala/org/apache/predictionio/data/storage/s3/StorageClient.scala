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

package org.apache.predictionio.data.storage.s3

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import org.apache.predictionio.data.storage.BaseStorageClient
import org.apache.predictionio.data.storage.StorageClientConfig
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import grizzled.slf4j.Logging

class StorageClient(val config: StorageClientConfig) extends BaseStorageClient
    with Logging {
  override val prefix = "S3"
  val client: AmazonS3 = {
    val builder = AmazonS3ClientBuilder
                    .standard()
                    .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
    (config.properties.get("ENDPOINT"), config.properties.get("REGION")) match {
      case (Some(endpoint), Some(region)) =>
        builder.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
      case (None, Some(region)) => builder.withRegion(region)
      case _ =>
    }
    config.properties.get("DISABLE_CHUNKED_ENCODING") match {
      case Some(x) if x.equalsIgnoreCase("true") => builder.disableChunkedEncoding()
      case _ =>
    }
    builder.build()
  }
}
