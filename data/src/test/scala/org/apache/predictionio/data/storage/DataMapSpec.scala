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

import org.specs2.mutable._

class DataMapSpec extends Specification {

  "DataMap" should {

    val properties = DataMap("""
      {
        "prop1" : 1,
        "prop2" : "value2",
        "prop3" : [1, 2, 3],
        "prop4" : true,
        "prop5" : ["a", "b", "c", "c"],
        "prop6" : 4.56
      }
      """)

    "get Int data" in {
      properties.get[Int]("prop1") must beEqualTo(1)
      properties.getOpt[Int]("prop1") must beEqualTo(Some(1))
    }

    "get String data" in {
      properties.get[String]("prop2") must beEqualTo("value2")
      properties.getOpt[String]("prop2") must beEqualTo(Some("value2"))
    }

    "get List of Int data" in {
      properties.get[List[Int]]("prop3") must beEqualTo(List(1,2,3))
      properties.getOpt[List[Int]]("prop3") must beEqualTo(Some(List(1,2,3)))
    }

    "get Boolean data" in {
      properties.get[Boolean]("prop4") must beEqualTo(true)
      properties.getOpt[Boolean]("prop4") must beEqualTo(Some(true))
    }

    "get List of String data" in {
      properties.get[List[String]]("prop5") must beEqualTo(List("a", "b", "c", "c"))
      properties.getOpt[List[String]]("prop5") must beEqualTo(Some(List("a", "b", "c", "c")))
    }

    "get Set of String data" in {
      properties.get[Set[String]]("prop5") must beEqualTo(Set("a", "b", "c"))
      properties.getOpt[Set[String]]("prop5") must beEqualTo(Some(Set("a", "b", "c")))
    }

    "get Double data" in {
      properties.get[Double]("prop6") must beEqualTo(4.56)
      properties.getOpt[Double]("prop6") must beEqualTo(Some(4.56))
    }

    "get empty optional Int data" in {
      properties.getOpt[Int]("prop9999") must beEqualTo(None)
    }

  }

  "DataMap with multi-level data" should {
    val properties = DataMap("""
      {
        "context": {
          "ip": "1.23.4.56",
          "prop1": 2.345
          "prop2": "value1",
          "prop4": [1, 2, 3]
        },
        "anotherPropertyA": 4.567,
        "anotherPropertyB": false
      }
      """)

    "get case class data" in {
      val expected = DataMapSpec.Context(
        ip = "1.23.4.56",
        prop1 = Some(2.345),
        prop2 = Some("value1"),
        prop3 = None,
        prop4 = List(1,2,3)
      )

      properties.get[DataMapSpec.Context]("context") must beEqualTo(expected)
    }

    "get empty optional case class data" in {
      properties.getOpt[DataMapSpec.Context]("context999") must beEqualTo(None)
    }

    "get double data" in {
      properties.get[Double]("anotherPropertyA") must beEqualTo(4.567)
    }

    "get boolean data" in {
      properties.get[Boolean]("anotherPropertyB") must beEqualTo(false)
    }
  }

  "DataMap extract" should {

    "extract to case class object" in {
      val properties = DataMap("""
        {
          "prop1" : 1,
          "prop2" : "value2",
          "prop3" : [1, 2, 3],
          "prop4" : true,
          "prop5" : ["a", "b", "c", "c"],
          "prop6" : 4.56
        }
        """)

      val result = properties.extract[DataMapSpec.BasicProperty]
      val expected = DataMapSpec.BasicProperty(
        prop1 = 1,
        prop2 = "value2",
        prop3 = List(1,2,3),
        prop4 = true,
        prop5 = List("a", "b", "c", "c"),
        prop6 = 4.56
      )

      result must beEqualTo(expected)
    }

    "extract with optional fields" in {
      val propertiesEmpty = DataMap("""{}""")
      val propertiesSome = DataMap("""
        {
          "prop1" : 1,
          "prop5" : ["a", "b", "c", "c"],
          "prop6" : 4.56
        }
        """)

      val resultEmpty = propertiesEmpty.extract[DataMapSpec.OptionProperty]
      val expectedEmpty = DataMapSpec.OptionProperty(
        prop1 = None,
        prop2 = None,
        prop3 = None,
        prop4 = None,
        prop5 = None,
        prop6 = None
      )

      val resultSome = propertiesSome.extract[DataMapSpec.OptionProperty]
      val expectedSome = DataMapSpec.OptionProperty(
        prop1 = Some(1),
        prop2 = None,
        prop3 = None,
        prop4 = None,
        prop5 = Some(List("a", "b", "c", "c")),
        prop6 = Some(4.56)
      )

      resultEmpty must beEqualTo(expectedEmpty)
      resultSome must beEqualTo(expectedSome)
    }

    "extract to multi-level object" in {
      val properties = DataMap("""
        {
          "context": {
            "ip": "1.23.4.56",
            "prop1": 2.345
            "prop2": "value1",
            "prop4": [1, 2, 3]
          },
          "anotherPropertyA": 4.567,
          "anotherPropertyB": false
        }
        """)

      val result = properties.extract[DataMapSpec.MultiLevelProperty]
      val expected = DataMapSpec.MultiLevelProperty(
        context = DataMapSpec.Context(
          ip = "1.23.4.56",
          prop1 = Some(2.345),
          prop2 = Some("value1"),
          prop3 = None,
          prop4 = List(1,2,3)
        ),
        anotherPropertyA = 4.567,
        anotherPropertyB = false
      )

      result must beEqualTo(expected)
    }

  }
}

object DataMapSpec {

  // define this case class inside object to avoid case class name conflict with other tests
  case class Context(
    ip: String,
    prop1: Option[Double],
    prop2: Option[String],
    prop3: Option[Int],
    prop4: List[Int]
  )

  case class BasicProperty(
    prop1: Int,
    prop2: String,
    prop3: List[Int],
    prop4: Boolean,
    prop5: List[String],
    prop6: Double
  )

  case class OptionProperty(
    prop1: Option[Int],
    prop2: Option[String],
    prop3: Option[List[Int]],
    prop4: Option[Boolean],
    prop5: Option[List[String]],
    prop6: Option[Double]
  )

  case class MultiLevelProperty(
    context: Context,
    anotherPropertyA: Double,
    anotherPropertyB: Boolean
  )
}
