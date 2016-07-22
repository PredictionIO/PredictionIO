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

package org.apache.predictionio.data.storage

import java.lang.reflect.InvocationTargetException

import grizzled.slf4j.Logging
import org.apache.predictionio.annotation.DeveloperApi

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.existentials
import scala.reflect.runtime.universe._

/** :: DeveloperApi ::
  * Any storage backend drivers will need to implement this trait with exactly
  * '''StorageClient''' as the class name. PredictionIO storage layer will look
  * for this class when it instantiates the actual backend for use by higher
  * level storage access APIs.
  *
  * @group Storage System
  */
@DeveloperApi
trait BaseStorageClient {
  /** Configuration of the '''StorageClient''' */
  val config: StorageClientConfig

  /** The actual client object. This could be a database connection or any kind
    * of database access object.
    */
  val client: AnyRef

  /** Set a prefix for storage class discovery. As an example, if this prefix
    * is set as ''JDBC'', when the storage layer instantiates an implementation
    * of [[Apps]], it will try to look for a class named ''JDBCApps''.
    */
  val prefix: String = ""
}

/** :: DeveloperApi ::
  * A wrapper of storage client configuration that will be populated by
  * PredictionIO automatically, and passed to the StorageClient during
  * instantiation.
  *
  * @param parallel This is set to true by PredictionIO when the storage client
  *                 is instantiated in a parallel data source.
  * @param test This is set to true by PredictionIO when tests are being run.
  * @param properties This is populated by PredictionIO automatically from
  *                   environmental configuration variables. If you have these
  *                   variables,
  *                   - PIO_STORAGE_SOURCES_PGSQL_TYPE=jdbc
  *                   - PIO_STORAGE_SOURCES_PGSQL_USERNAME=abc
  *                   - PIO_STOARGE_SOURCES_PGSQL_PASSWORD=xyz
  *
  *                   this field will be filled as a map of string to string:
  *                   - TYPE -> jdbc
  *                   - USERNAME -> abc
  *                   - PASSWORD -> xyz
  *
  * @group Storage System
  */
@DeveloperApi
case class StorageClientConfig(
  parallel: Boolean = false, // parallelized access (RDD)?
  test: Boolean = false, // test mode config
  properties: Map[String, String] = Map())

/** :: DeveloperApi ::
  * Thrown when a StorageClient runs into an exceptional condition
  *
  * @param message Exception error message
  * @param cause The underlying exception that caused the exception
  * @group Storage System
  */
@DeveloperApi
class StorageClientException(message: String, cause: Throwable)
  extends RuntimeException(message, cause)

@deprecated("Use StorageException", "0.9.2")
private[predictionio] case class StorageError(message: String)

/** :: DeveloperApi ::
  * Thrown by data access objects when they run into exceptional conditions
  *
  * @param message Exception error message
  * @param cause The underlying exception that caused the exception
  *
  * @group Storage System
  */
@DeveloperApi
class StorageException(message: String, cause: Throwable)
  extends Exception(message, cause) {

  def this(message: String) = this(message, null)
}

/** Backend-agnostic data storage layer with lazy initialization. Use this
  * object when you need to interface with Event Store in your engine.
  *
  * @group Storage System
  */
object Storage extends Logging {
  private case class ClientMeta(
    sourceType: String,
    client: BaseStorageClient,
    config: StorageClientConfig)

  private case class DataObjectMeta(sourceName: String, namespace: String)

  private var errors = 0

  private val sourcesPrefix = "PIO_STORAGE_SOURCES"

  private val sourceTypesRegex = """PIO_STORAGE_SOURCES_([^_]+)_TYPE""".r

  private val sourceKeys: Seq[String] = sys.env.keys.toSeq.flatMap { k =>
    sourceTypesRegex findFirstIn k match {
      case Some(sourceTypesRegex(sourceType)) => Seq(sourceType)
      case None => Nil
    }
  }

  if (sourceKeys.size == 0) warn("There is no properly configured data source.")

  private val s2cm = scala.collection.mutable.Map[String, Option[ClientMeta]]()

  /** Reference to the app data repository. */
  private val EventDataRepository = "EVENTDATA"
  private val ModelDataRepository = "MODELDATA"
  private val MetaDataRepository = "METADATA"

  private val repositoriesPrefix = "PIO_STORAGE_REPOSITORIES"

  private val repositoryNamesRegex =
    """PIO_STORAGE_REPOSITORIES_([^_]+)_NAME""".r

  private val repositoryKeys: Seq[String] = sys.env.keys.toSeq.flatMap { k =>
    repositoryNamesRegex findFirstIn k match {
      case Some(repositoryNamesRegex(repositoryName)) => Seq(repositoryName)
      case None => Nil
    }
  }

  if (repositoryKeys.size == 0) {
    warn("There is no properly configured repository.")
  }

  private val requiredRepositories = Seq(MetaDataRepository)

  requiredRepositories foreach { r =>
    if (!repositoryKeys.contains(r)) {
      error(s"Required repository (${r}) configuration is missing.")
      errors += 1
    }
  }
  private val repositoriesToDataObjectMeta: Map[String, DataObjectMeta] =
    repositoryKeys.map(r =>
      try {
        val keyedPath = repositoriesPrefixPath(r)
        val name = sys.env(prefixPath(keyedPath, "NAME"))
        val sourceName = sys.env(prefixPath(keyedPath, "SOURCE"))
        if (sourceKeys.contains(sourceName)) {
          r -> DataObjectMeta(
            sourceName = sourceName,
            namespace = name)
        } else {
          error(s"$sourceName is not a configured storage source.")
          r -> DataObjectMeta("", "")
        }
      } catch {
        case e: Throwable =>
          error(e.getMessage)
          errors += 1
          r -> DataObjectMeta("", "")
      }
    ).toMap

  if (errors > 0) {
    error(s"There were $errors configuration errors. Exiting.")
    sys.exit(errors)
  }

  // End of constructor and field definitions and begin method definitions

  private def prefixPath(prefix: String, body: String) = s"${prefix}_$body"

  private def sourcesPrefixPath(body: String) = prefixPath(sourcesPrefix, body)

  private def repositoriesPrefixPath(body: String) =
    prefixPath(repositoriesPrefix, body)

  private def sourcesToClientMeta(
      source: String,
      parallel: Boolean,
      test: Boolean): Option[ClientMeta] = {
    val sourceName = if (parallel) s"parallel-$source" else source
    s2cm.getOrElseUpdate(sourceName, updateS2CM(source, parallel, test))
  }

  private def getClient(
    clientConfig: StorageClientConfig,
    pkg: String): BaseStorageClient = {
    val className = "org.apache.predictionio.data.storage." + pkg + ".StorageClient"
    try {
      Class.forName(className).getConstructors()(0).newInstance(clientConfig).
        asInstanceOf[BaseStorageClient]
    } catch {
      case e: ClassNotFoundException =>
        val originalClassName = pkg + ".StorageClient"
        Class.forName(originalClassName).getConstructors()(0).
          newInstance(clientConfig).asInstanceOf[BaseStorageClient]
      case e: java.lang.reflect.InvocationTargetException =>
        throw e.getCause
    }
  }

  /** Get the StorageClient config data from PIO Framework's environment variables */
  def getConfig(sourceName: String): Option[StorageClientConfig] = {
    if (s2cm.contains(sourceName) && s2cm.get(sourceName).nonEmpty
      && s2cm.get(sourceName).get.nonEmpty) {
      Some(s2cm.get(sourceName).get.get.config)
    } else None
  }

  private def updateS2CM(k: String, parallel: Boolean, test: Boolean):
  Option[ClientMeta] = {
    try {
      val keyedPath = sourcesPrefixPath(k)
      val sourceType = sys.env(prefixPath(keyedPath, "TYPE"))
      val props = sys.env.filter(t => t._1.startsWith(keyedPath)).map(
        t => t._1.replace(s"${keyedPath}_", "") -> t._2)
      val clientConfig = StorageClientConfig(
        properties = props,
        parallel = parallel,
        test = test)
      val client = getClient(clientConfig, sourceType)
      Some(ClientMeta(sourceType, client, clientConfig))
    } catch {
      case e: Throwable =>
        error(s"Error initializing storage client for source ${k}", e)
        errors += 1
        None
    }
  }

  private[predictionio]
  def getDataObjectFromRepo[T](repo: String, test: Boolean = false)
    (implicit tag: TypeTag[T]): T = {
    val repoDOMeta = repositoriesToDataObjectMeta(repo)
    val repoDOSourceName = repoDOMeta.sourceName
    getDataObject[T](repoDOSourceName, repoDOMeta.namespace, test = test)
  }

  private[predictionio]
  def getPDataObject[T](repo: String)(implicit tag: TypeTag[T]): T = {
    val repoDOMeta = repositoriesToDataObjectMeta(repo)
    val repoDOSourceName = repoDOMeta.sourceName
    getPDataObject[T](repoDOSourceName, repoDOMeta.namespace)
  }

  private[predictionio] def getDataObject[T](
      sourceName: String,
      namespace: String,
      parallel: Boolean = false,
      test: Boolean = false)(implicit tag: TypeTag[T]): T = {
    val clientMeta = sourcesToClientMeta(sourceName, parallel, test) getOrElse {
      throw new StorageClientException(
        s"Data source $sourceName was not properly initialized.", null)
    }
    val sourceType = clientMeta.sourceType
    val ctorArgs = dataObjectCtorArgs(clientMeta.client, namespace)
    val classPrefix = clientMeta.client.prefix
    val originalClassName = tag.tpe.toString.split('.')
    val rawClassName = sourceType + "." + classPrefix + originalClassName.last
    val className = "org.apache.predictionio.data.storage." + rawClassName
    val clazz = try {
      Class.forName(className)
    } catch {
      case e: ClassNotFoundException =>
        try {
          Class.forName(rawClassName)
        } catch {
          case e: ClassNotFoundException =>
            throw new StorageClientException("No storage backend " +
              "implementation can be found (tried both " +
              s"$className and $rawClassName)", e)
        }
    }
    val constructor = clazz.getConstructors()(0)
    try {
      constructor.newInstance(ctorArgs: _*).
        asInstanceOf[T]
    } catch {
      case e: IllegalArgumentException =>
        error(
          "Unable to instantiate data object with class '" +
          constructor.getDeclaringClass.getName + " because its constructor" +
          " does not have the right number of arguments." +
          " Number of required constructor arguments: " +
          ctorArgs.size + "." +
          " Number of existing constructor arguments: " +
          constructor.getParameterTypes.size + "." +
          s" Storage source name: ${sourceName}." +
          s" Exception message: ${e.getMessage}).", e)
        errors += 1
        throw e
      case e: java.lang.reflect.InvocationTargetException =>
        throw e.getCause
    }
  }

  private def getPDataObject[T](
      sourceName: String,
      databaseName: String)(implicit tag: TypeTag[T]): T =
    getDataObject[T](sourceName, databaseName, true)

  private def dataObjectCtorArgs(
      client: BaseStorageClient,
      namespace: String): Seq[AnyRef] = {
    Seq(client.client, client.config, namespace)
  }

  private[predictionio] def verifyAllDataObjects(): Unit = {
    info("Verifying Meta Data Backend (Source: " +
      s"${repositoriesToDataObjectMeta(MetaDataRepository).sourceName})...")
    getMetaDataEngineManifests()
    getMetaDataEngineInstances()
    getMetaDataEvaluationInstances()
    getMetaDataApps()
    getMetaDataAccessKeys()
    info("Verifying Model Data Backend (Source: " +
      s"${repositoriesToDataObjectMeta(ModelDataRepository).sourceName})...")
    getModelDataModels()
    info("Verifying Event Data Backend (Source: " +
      s"${repositoriesToDataObjectMeta(EventDataRepository).sourceName})...")
    val eventsDb = getLEvents(test = true)
    info("Test writing to Event Store (App Id 0)...")
    // use appId=0 for testing purpose
    eventsDb.init(0)
    eventsDb.insert(Event(
      event = "test",
      entityType = "test",
      entityId = "test"), 0)
    eventsDb.remove(0)
    eventsDb.close()
  }

  private[predictionio] def getMetaDataEngineManifests(): EngineManifests =
    getDataObjectFromRepo[EngineManifests](MetaDataRepository)

  private[predictionio] def getMetaDataEngineInstances(): EngineInstances =
    getDataObjectFromRepo[EngineInstances](MetaDataRepository)

  private[predictionio] def getMetaDataEvaluationInstances(): EvaluationInstances =
    getDataObjectFromRepo[EvaluationInstances](MetaDataRepository)

  private[predictionio] def getMetaDataApps(): Apps =
    getDataObjectFromRepo[Apps](MetaDataRepository)

  private[predictionio] def getMetaDataAccessKeys(): AccessKeys =
    getDataObjectFromRepo[AccessKeys](MetaDataRepository)

  private[predictionio] def getMetaDataChannels(): Channels =
    getDataObjectFromRepo[Channels](MetaDataRepository)

  private[predictionio] def getModelDataModels(): Models =
    getDataObjectFromRepo[Models](ModelDataRepository)

  /** Obtains a data access object that returns [[Event]] related local data
    * structure.
    */
  def getLEvents(test: Boolean = false): LEvents =
    getDataObjectFromRepo[LEvents](EventDataRepository, test = test)

  /** Obtains a data access object that returns [[Event]] related RDD data
    * structure.
    */
  def getPEvents(): PEvents =
    getPDataObject[PEvents](EventDataRepository)

  def config: Map[String, Map[String, Map[String, String]]] = Map(
    "sources" -> s2cm.toMap.map { case (source, clientMeta) =>
      source -> clientMeta.map { cm =>
        Map(
          "type" -> cm.sourceType,
          "config" -> cm.config.properties.map(t => s"${t._1} -> ${t._2}").mkString(", ")
        )
      }.getOrElse(Map.empty)
    }
  )
}
