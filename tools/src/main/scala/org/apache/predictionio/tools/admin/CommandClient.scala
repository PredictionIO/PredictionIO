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

package org.apache.predictionio.tools.admin

import org.apache.predictionio.data.storage._

import scala.concurrent.{ExecutionContext, Future}

abstract class BaseResponse()

case class GeneralResponse(
  status: Int = 0,
  message: String = ""
) extends BaseResponse()

case class AppRequest(
  id: Int = 0,
  name: String = "",
  description: String = ""
)

case class TrainRequest(
  enginePath: String = ""
)
case class AppResponse(
  id: Int = 0,
  name: String = "",
  keys: Seq[AccessKey]
) extends BaseResponse()

case class AppNewResponse(
  status: Int = 0,
  message: String = "",
  id: Int = 0,
  name: String = "",
  key: String
) extends BaseResponse()

case class AppListResponse(
  status: Int = 0,
  message: String = "",
  apps: Seq[AppResponse]
) extends BaseResponse()

class CommandClient(
  val appClient: Apps,
  val accessKeyClient: AccessKeys,
  val eventClient: LEvents
) {

  def futureAppNew(req: AppRequest)(implicit ec: ExecutionContext): Future[BaseResponse] = Future {
    val response = appClient.getByName(req.name) map { app =>
      GeneralResponse(0, s"App ${req.name} already exists. Aborting.")
    } getOrElse {
      appClient.get(req.id) map {
        app2 =>
          GeneralResponse(0,
              s"App ID ${app2.id} already exists and maps to the app '${app2.name}'. " +
              "Aborting.")
      } getOrElse {
        val appid = appClient.insert(App(
          id = Option(req.id).getOrElse(0),
          name = req.name,
          description = Option(req.description)))
        appid map { id =>
          val dbInit = eventClient.init(id)
          val r = if (dbInit) {
            val accessKey = AccessKey(
              key = "",
              appid = id,
              events = Seq())
            val accessKey2 = accessKeyClient.insert(AccessKey(
              key = "",
              appid = id,
              events = Seq()))
            accessKey2 map { k =>
              new AppNewResponse(1,"App created successfully.",id, req.name, k)
            } getOrElse {
              GeneralResponse(0, s"Unable to create new access key.")
            }
          } else {
            GeneralResponse(0, s"Unable to initialize Event Store for this app ID: ${id}.")
          }
          r
        } getOrElse {
          GeneralResponse(0, s"Unable to create new app.")
        }
      }
    }
    response
  }

  def futureAppList()(implicit ec: ExecutionContext): Future[AppListResponse] = Future {
    val apps = appClient.getAll().sortBy(_.name)
    val appsRes = apps.map {
      app => {
        new AppResponse(app.id, app.name, accessKeyClient.getByAppid(app.id))
      }
    }
    new AppListResponse(1, "Successful retrieved app list.", appsRes)
  }

  def futureAppDataDelete(appName: String)
      (implicit ec: ExecutionContext): Future[GeneralResponse] = Future {
    val response = appClient.getByName(appName) map { app =>
      val data = if (eventClient.remove(app.id)) {
        GeneralResponse(1, s"Removed Event Store for this app ID: ${app.id}")
      } else {
        GeneralResponse(0, s"Error removing Event Store for this app.")
      }

      val dbInit = eventClient.init(app.id)
      val data2 = if (dbInit) {
        GeneralResponse(1, s"Initialized Event Store for this app ID: ${app.id}.")
      } else {
        GeneralResponse(0, s"Unable to initialize Event Store for this appId:" +
          s" ${app.id}.")
      }
      GeneralResponse(data.status * data2.status, data.message + data2.message)
    } getOrElse {
      GeneralResponse(0, s"App ${appName} does not exist.")
    }
    response
  }

  def futureAppDelete(appName: String)
      (implicit ec: ExecutionContext): Future[GeneralResponse] = Future {

    val response = appClient.getByName(appName) map { app =>
      val data = if (eventClient.remove(app.id)) {
        Storage.getMetaDataApps.delete(app.id)
        GeneralResponse(1, s"App successfully deleted")
      } else {
        GeneralResponse(0, s"Error removing Event Store for app ${app.name}.");
      }
      data
    } getOrElse {
      GeneralResponse(0, s"App ${appName} does not exist.")
    }
    response
  }

  def futureTrain(req: TrainRequest)
      (implicit ec: ExecutionContext): Future[GeneralResponse] = Future {
    null
  }
}
