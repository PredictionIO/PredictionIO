/** Copyright 2015 TappingStone, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package io.prediction.configuration

/**
  * Created by ykhodorkovsky on 2/26/16.
  */

import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import com.typesafe.config.ConfigFactory
import spray.io.ServerSSLEngineProvider

trait SSLConfiguration {

  private val serverConfig = ConfigFactory.load("server.conf")

  private val keyStoreResource =
    serverConfig.getString("io.prediction.server.ssl-keystore-resource")
  private val password = serverConfig.getString("io.prediction.server.ssl-keystore-pass")
  private val keyAlias = serverConfig.getString("io.prediction.server.ssl-key-alias")

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
  implicit def sslContext: SSLContext = {
    val context = SSLContext.getInstance("TLS")
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(keyStore, password.toCharArray)
    tmf.init(keyStore)
    context.init(kmf.getKeyManagers, tmf.getTrustManagers, null)
    context
  }

  // provide implicit SSLEngine with some protocols
  implicit def sslEngineProvider: ServerSSLEngineProvider = {
    ServerSSLEngineProvider { engine =>
      engine.setEnabledCipherSuites(Array(
        "TLS_RSA_WITH_AES_256_CBC_SHA",
        "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
        "TLS_RSA_WITH_AES_128_CBC_SHA"))
      engine.setEnabledProtocols(Array("TLSv1", "TLSv1.2", "TLSv1.1"))
      engine
    }
  }
}
