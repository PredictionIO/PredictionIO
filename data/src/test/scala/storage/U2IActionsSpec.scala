/** Copyright 2014 TappingStone, Inc.
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

package io.prediction.data.storage

import com.github.nscala_time.time.Imports._
import org.specs2._
import org.specs2.specification.Step

class U2IActionsSpec extends Specification {
  def is = s2"""

  PredictionIO Storage User-to-item Actions Specification

    U2IActions can be implemented by:
    - ESU2IActions ${esU2IActions}
    - MongoU2IActions ${mongoU2IActions}

  """

  def esU2IActions = s2"""

    ESU2IActions should" ^
    - behave like any U2IActions implementation ${u2iActions(esDO)}
    - (index cleanup) ${Step(StorageTestUtils.dropElasticsearchIndex(dbName))}

  """

  def mongoU2IActions = s2"""

    MongoU2IActions should" ^
    - behave like any U2IActions implementation ${u2iActions(mongoDO)}
    - (database cleanup) ${Step(StorageTestUtils.dropMongoDatabase(dbName))}

  """

  def u2iActions(u2iActions: U2IActions) = s2"""

    inserting and getting 3 U2IAction's ${insert(u2iActions)}
    getting U2IActions by App ID, User ID, and Item IDs ${getAllByAppidAndUidAndIids(u2iActions)}
    getting U2IActions by App ID, Item ${getAllByAppidAndIid(u2iActions)}
    delete U2IActions by appid ${deleteByAppid(u2iActions)}
    count U2IActions by appid ${countByAppid(u2iActions)}

  """

  val dbName = "test_pio_storage_u2iactions_" + hashCode
  val esDO = Storage.getDataObject[U2IActions](
    StorageTestUtils.elasticsearchSourceName,
    dbName)
  val mongoDO = Storage.getDataObject[U2IActions](
    StorageTestUtils.mongodbSourceName,
    dbName)

  def insert(u2iActions: U2IActions) = {
    val appid = 0
    val actions = List(U2IAction(
      appid = appid,
      action = "rate",
      uid = "dead",
      iid = "meat",
      t = DateTime.now,
      latlng = None,
      v = Some(3),
      price = None
    ), U2IAction(
      appid = appid,
      action = "view",
      uid = "avatar",
      iid = "creeper",
      t = DateTime.now,
      latlng = Some((94.3904, -29.4839)),
      v = None,
      price = None
    ), U2IAction(
      appid = appid,
      action = "like",
      uid = "pub",
      iid = "sub",
      t = DateTime.now,
      latlng = None,
      v = Some(1),
      price = Some(49.40)
    ))
    actions foreach { u2iActions.insert(_) }
    Thread.sleep(1000)
    val results = u2iActions.getAllByAppid(appid)
    val r1 = results.next
    val r2 = results.next
    val r3 = results.next
    val r = Seq(r1, r2, r3)
    results.hasNext must beFalse and
      (r must containTheSameElementsAs(actions))
  }

  def getAllByAppidAndUidAndIids(u2iActions: U2IActions) = {
    val appid = 1
    val actions = List(U2IAction(
      appid = appid,
      action = "rate",
      uid = "dead",
      iid = "meat",
      t = DateTime.now,
      latlng = None,
      v = Some(3),
      price = None
    ), U2IAction(
      appid = appid,
      action = "view",
      uid = "dead",
      iid = "creeper",
      t = DateTime.now,
      latlng = Some((94.3904, -29.4839)),
      v = None,
      price = None
    ), U2IAction(
      appid = appid,
      action = "like",
      uid = "dead",
      iid = "sub",
      t = DateTime.now,
      latlng = None,
      v = Some(1),
      price = Some(49.40)
    ))
    actions foreach { u2iActions.insert(_) }
    Thread.sleep(1000)
    val results = u2iActions.getAllByAppidAndUidAndIids(
      appid, "dead", List("sub", "meat")).toList.sortWith((s, t) =>
        s.iid < t.iid)
    results.size must beEqualTo(2) and
      (results must containTheSameElementsAs(Seq(actions(0), actions(2))))
  }

  def getAllByAppidAndIid(u2iActions: U2IActions) = {
    val appid = 109
    val actions = List(U2IAction(
      appid = appid,
      action = "rate",
      uid = "dead",
      iid = "meat",
      t = DateTime.now,
      latlng = None,
      v = Some(3),
      price = None
    ), U2IAction(
      appid = appid,
      action = "view",
      uid = "dead",
      iid = "creeper",
      t = DateTime.now,
      latlng = Some((94.3904, -29.4839)),
      v = None,
      price = None
    ), U2IAction(
      appid = appid,
      action = "like",
      uid = "dead",
      iid = "sub",
      t = DateTime.now,
      latlng = None,
      v = Some(1),
      price = Some(49.40)
    ), U2IAction(
      appid = appid,
      action = "rate",
      uid = "dead2",
      iid = "meat",
      t = DateTime.now,
      latlng = None,
      v = Some(2),
      price = None
    ), U2IAction(
      appid = appid,
      action = "rate",
      uid = "dead3",
      iid = "meat",
      t = DateTime.now,
      latlng = None,
      v = Some(5),
      price = None
    ), U2IAction(
      appid = appid,
      action = "rate",
      uid = "dead4",
      iid = "meat",
      t = DateTime.now,
      latlng = None,
      v = Some(1),
      price = None
    ))
    actions foreach { u2iActions.insert(_) }
    Thread.sleep(1000)
    val results = u2iActions.getAllByAppidAndIid(
      appid, "meat", sortedByUid = true).toList
    val resultsNoSort = u2iActions.getAllByAppidAndIid(
      appid, "meat", sortedByUid = false).toList.sortWith((s, t) =>
        s.uid < t.uid)

    results.size must beEqualTo(4) and
      (results(0) must beEqualTo(actions(0))) and
      (results(1) must beEqualTo(actions(3))) and
      (results(2) must beEqualTo(actions(4))) and
      (results(3) must beEqualTo(actions(5))) and
      (resultsNoSort(0) must beEqualTo(actions(0))) and
      (resultsNoSort(1) must beEqualTo(actions(3))) and
      (resultsNoSort(2) must beEqualTo(actions(4))) and
      (resultsNoSort(3) must beEqualTo(actions(5)))
  }

  def deleteByAppid(u2iActions: U2IActions) = {
    // insert a few u2iActions with appid1 and a few u2iActions with appid2.
    // delete all u2iActions of appid1.
    // u2iActions of appid1 should be deleted and u2iActions of appid2 should still exist.
    // delete all u2iActions of appid2
    // u2iActions of appid2 should be deleted

    val appid1 = 10
    val appid2 = 11

    val ida = "deleteByAppid-ida"
    val idb = "deleteByAppid-idb"
    val idc = "deleteByAppid-idc"

    val u2iAction1a = U2IAction(
      appid = appid1,
      action = "rate",
      uid = "dead",
      iid = "meat",
      t = DateTime.now,
      latlng = None,
      v = Some(3),
      price = None
    )
    val u2iActionsApp1 = List(
      u2iAction1a,
      u2iAction1a.copy(
        v = Some(1)
      ),
      u2iAction1a.copy(
        v = Some(2)
      ))

    val u2iActionsApp2 = u2iActionsApp1 map (x => x.copy(appid = appid2))

    u2iActionsApp1 foreach { u2iActions.insert(_) }
    u2iActionsApp2 foreach { u2iActions.insert(_) }
    Thread.sleep(1000)
    // NOTE: Call toList to retrieve all results first.
    // If call toList after delete, the data is gone because getAllByAppid returns
    // iterator which doesn't actually retrieve the result yet.
    val g1_App1 = u2iActions.getAllByAppid(appid1).toList
    val g1_App2 = u2iActions.getAllByAppid(appid2).toList

    u2iActions.deleteByAppid(appid1)
    Thread.sleep(1000)

    val g2_App1 = u2iActions.getAllByAppid(appid1).toList
    val g2_App2 = u2iActions.getAllByAppid(appid2).toList

    u2iActions.deleteByAppid(appid2)
    Thread.sleep(1000)

    val g3_App2 = u2iActions.getAllByAppid(appid2).toList

    g1_App1 must containTheSameElementsAs(u2iActionsApp1) and
      (g1_App2 must containTheSameElementsAs(u2iActionsApp2)) and
      (g2_App1 must be empty) and
      (g2_App2 must containTheSameElementsAs(u2iActionsApp2)) and
      (g3_App2 must be empty)

  }

  def countByAppid(u2iActions: U2IActions) = {
    val appid1 = 20
    val appid2 = 21
    val appid3 = 22

    val u2iAction1a = U2IAction(
      appid = appid1,
      action = "rate",
      uid = "dead",
      iid = "meat",
      t = DateTime.now,
      latlng = None,
      v = Some(3),
      price = None
    )
    val u2iAction1b = u2iAction1a.copy(
      appid = appid1
    )
    val u2iAction2a = u2iAction1a.copy(
      appid = appid2
    )

    u2iActions.insert(u2iAction1a)
    u2iActions.insert(u2iAction1b)
    u2iActions.insert(u2iAction2a)
    Thread.sleep(1000)
    u2iActions.countByAppid(appid1) must be_==(2) and
      (u2iActions.countByAppid(appid2) must be_==(1)) and
      (u2iActions.countByAppid(appid3) must be_==(0))

  }

}
