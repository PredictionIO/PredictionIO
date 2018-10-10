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

package org.apache.predictionio.configuration

import java.io.FileInputStream
import java.security.KeyStore

import com.typesafe.config.ConfigFactory
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

trait SSLConfiguration {

  private val serverConfig = ConfigFactory.load("server.conf")

  private val keyStoreResource =
    serverConfig.getString("org.apache.predictionio.server.ssl-keystore-resource")
  private val password = serverConfig.getString("org.apache.predictionio.server.ssl-keystore-pass")
  private val keyAlias = serverConfig.getString("org.apache.predictionio.server.ssl-key-alias")

  private val keyStore = {
    // Loading keystore from specified file
    val clientStore = KeyStore.getInstance("JKS")
    val inputStream = new FileInputStream(
      getClass().getClassLoader().getResource(keyStoreResource).getFile())
    clientStore.load(inputStream, password.toCharArray)
    inputStream.close()
    clientStore
  }

  // Creating SSL context
  def sslContext: SSLContext = {
    val context = SSLContext.getInstance("TLS")
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(keyStore, password.toCharArray)
    tmf.init(keyStore)
    context.init(kmf.getKeyManagers, tmf.getTrustManagers, null)
    context
  }

}
