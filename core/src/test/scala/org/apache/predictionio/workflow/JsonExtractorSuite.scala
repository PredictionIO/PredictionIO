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

package org.apache.predictionio.workflow

import org.apache.predictionio.controller.EngineParams
import org.apache.predictionio.controller.Params
import org.apache.predictionio.controller.Utils
import org.json4s.CustomSerializer
import org.json4s.JsonAST.JField
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.json4s.MappingException
import org.json4s.native.JsonMethods.compact
import org.json4s.native.JsonMethods.render
import org.scalatest.FunSuite
import org.scalatest.Matchers

class JsonExtractorSuite extends FunSuite with Matchers {

  test("Extract Scala object using option Json4sNative works with optional and default value " +
    "provided") {

    val json = """{"string": "query string", "optional": "optional string", "default": "d"}"""

    val query = JsonExtractor.extract(
      JsonExtractorOption.Json4sNative,
      json,
      classOf[ScalaQuery])

    query should be (ScalaQuery("query string", Some("optional string"), "d"))
  }

  test("Extract Scala object using option Json4sNative works with no optional and no default " +
    "value provided") {

    val json = """{"string": "query string"}"""

    val query = JsonExtractor.extract(
      JsonExtractorOption.Json4sNative,
      json,
      classOf[ScalaQuery])

    query should be (ScalaQuery("query string", None, "default"))
  }

  test("Extract Scala object using option Json4sNative works with null optional and null default" +
    " value") {

    val json = """{"string": "query string", "optional": null, "default": null}"""

    val query = JsonExtractor.extract(
      JsonExtractorOption.Json4sNative,
      json,
      classOf[ScalaQuery])

    query should be (ScalaQuery("query string", None, "default"))
  }

  test("Extract Scala object using option Both works with optional and default value provided") {

    val json = """{"string": "query string", "optional": "optional string", "default": "d"}"""

    val query = JsonExtractor.extract(
      JsonExtractorOption.Json4sNative,
      json,
      classOf[ScalaQuery])

    query should be (ScalaQuery("query string", Some("optional string"), "d"))
  }

  test("Extract Scala object using option Both works with no optional and no default value " +
    "provided") {

    val json = """{"string": "query string"}"""

    val query = JsonExtractor.extract(
      JsonExtractorOption.Json4sNative,
      json,
      classOf[ScalaQuery])

    query should be (ScalaQuery("query string", None, "default"))
  }

  test("Extract Scala object using option Both works with null optional and null default value") {

    val json = """{"string": "query string", "optional": null, "default": null}"""

    val query = JsonExtractor.extract(
      JsonExtractorOption.Json4sNative,
      json,
      classOf[ScalaQuery])

    query should be (ScalaQuery("query string", None, "default"))
  }

  test("Extract Scala object using option Gson should not get default value and optional none" +
    " value") {

    val json = """{"string": "query string"}"""
    val query = JsonExtractor.extract(
      JsonExtractorOption.Gson,
      json,
      classOf[ScalaQuery])

    query should be (ScalaQuery("query string", null, null))
  }

  test("Extract Scala object using option Gson should throw an exception with optional " +
    "value provided") {

    val json = """{"string": "query string", "optional": "o", "default": "d"}"""
    intercept[RuntimeException] {
      JsonExtractor.extract(
        JsonExtractorOption.Gson,
        json,
        classOf[ScalaQuery])
    }
  }

  test("Extract Java object using option Gson works") {

    val json = """{"q": "query string"}"""

    val query = JsonExtractor.extract(
      JsonExtractorOption.Gson,
      json,
      classOf[JavaQuery])

    query should be (new JavaQuery("query string"))
  }

  test("Extract Java object using option Both works") {

    val json = """{"q": "query string"}"""

    val query = JsonExtractor.extract(
      JsonExtractorOption.Both,
      json,
      classOf[JavaQuery])

    query should be (new JavaQuery("query string"))
  }

  test("Extract Java object using option Json4sNative should throw an exception") {

    val json = """{"q": "query string"}"""

    intercept[MappingException] {
      JsonExtractor.extract(
        JsonExtractorOption.Json4sNative,
        json,
        classOf[JavaQuery])
    }
  }

  test("Extract Scala object using option Json4sNative with custom deserializer") {
    val json = """{"string": "query string", "optional": "o", "default": "d"}"""

    val query = JsonExtractor.extract(
      JsonExtractorOption.Json4sNative,
      json,
      classOf[ScalaQuery],
      Utils.json4sDefaultFormats + new UpperCaseFormat
    )

    query should be(ScalaQuery("QUERY STRING", Some("O"), "D"))
  }

  test("Extract Java object usingoption Gson with custom deserializer") {
    val json = """{"q": "query string"}"""

    val query = JsonExtractor.extract(
      extractorOption = JsonExtractorOption.Gson,
      json = json,
      clazz = classOf[JavaQuery],
      gsonTypeAdapterFactories = Seq(new JavaQueryTypeAdapterFactory)
    )

    query should be(new JavaQuery("QUERY STRING"))
  }

  test("Java object to JValue using option Both works") {
    val query = new JavaQuery("query string")
    val jValue = JsonExtractor.toJValue(JsonExtractorOption.Both, query)

    compact(render(jValue)) should be ("""{"q":"query string"}""")
  }

  test("Java object to JValue using option Gson works") {
    val query = new JavaQuery("query string")
    val jValue = JsonExtractor.toJValue(JsonExtractorOption.Gson, query)

    compact(render(jValue)) should be ("""{"q":"query string"}""")
  }

  test("Java object to JValue using option Json4sNative results in empty Json") {
    val query = new JavaQuery("query string")
    val jValue = JsonExtractor.toJValue(JsonExtractorOption.Json4sNative, query)

    compact(render(jValue)) should be ("""{}""")
  }

  test("Scala object to JValue using option Both works") {
    val query = new ScalaQuery("query string", Some("option"))
    val jValue = JsonExtractor.toJValue(JsonExtractorOption.Both, query)

    compact(render(jValue)) should
      be ("""{"string":"query string","optional":"option","default":"default"}""")
  }

  test("Scala object to JValue using option Gson does not serialize optional") {
    val query = new ScalaQuery("query string", Some("option"))
    val jValue = JsonExtractor.toJValue(JsonExtractorOption.Gson, query)

    compact(render(jValue)) should
      be ("""{"string":"query string","optional":{},"default":"default"}""")
  }

  test("Scala object to JValue using option Json4sNative works") {
    val query = new ScalaQuery("query string", Some("option"))
    val jValue = JsonExtractor.toJValue(JsonExtractorOption.Json4sNative, query)

    compact(render(jValue)) should
      be ("""{"string":"query string","optional":"option","default":"default"}""")
  }

  test("Scala object to JValue using option Json4sNative with custom serializer") {
    val query = new ScalaQuery("query string", Some("option"))
    val jValue = JsonExtractor.toJValue(
      JsonExtractorOption.Json4sNative,
      query,
      Utils.json4sDefaultFormats + new UpperCaseFormat
    )

    compact(render(jValue)) should
      be ("""{"string":"QUERY STRING","optional":"OPTION","default":"DEFAULT"}""")
  }

  test("Java object to JValue using option Gson with custom serializer") {
    val query = new JavaQuery("query string")
    val jValue = JsonExtractor.toJValue(
      extractorOption = JsonExtractorOption.Gson,
      o = query,
      gsonTypeAdapterFactories = Seq(new JavaQueryTypeAdapterFactory)
    )

    compact(render(jValue)) should be ("""{"q":"QUERY STRING"}""")
  }

  test("Java Param to Json using option Both") {
    val param = ("algo", new JavaParams("parameter"))
    val json = JsonExtractor.paramToJson(JsonExtractorOption.Both, param)

    json should be ("""{"algo":{"p":"parameter"}}""")
  }

  test("Java Param to Json using option Gson") {
    val param = ("algo", new JavaParams("parameter"))
    val json = JsonExtractor.paramToJson(JsonExtractorOption.Gson, param)

    json should be ("""{"algo":{"p":"parameter"}}""")
  }

  test("Scala Param to Json using option Both") {
    val param = ("algo", AlgorithmParams("parameter"))
    val json = JsonExtractor.paramToJson(JsonExtractorOption.Both, param)

    json should be ("""{"algo":{"a":"parameter"}}""")
  }

  test("Scala Param to Json using option Json4sNative") {
    val param = ("algo", AlgorithmParams("parameter"))
    val json = JsonExtractor.paramToJson(JsonExtractorOption.Json4sNative, param)

    json should be ("""{"algo":{"a":"parameter"}}""")
  }

  test("Java Params to Json using option Both") {
    val params = Seq(("algo", new JavaParams("parameter")), ("algo2", new JavaParams("parameter2")))
    val json = JsonExtractor.paramsToJson(JsonExtractorOption.Both, params)

    json should be ("""[{"algo":{"p":"parameter"}},{"algo2":{"p":"parameter2"}}]""")
  }

  test("Java Params to Json using option Gson") {
    val params = Seq(("algo", new JavaParams("parameter")), ("algo2", new JavaParams("parameter2")))
    val json = JsonExtractor.paramsToJson(JsonExtractorOption.Gson, params)

    json should be ("""[{"algo":{"p":"parameter"}},{"algo2":{"p":"parameter2"}}]""")
  }

  test("Scala Params to Json using option Both") {
    val params =
      Seq(("algo", AlgorithmParams("parameter")), ("algo2", AlgorithmParams("parameter2")))
    val json = JsonExtractor.paramsToJson(JsonExtractorOption.Both, params)

    json should be (org.json4s.native.Serialization.write(params)(Utils.json4sDefaultFormats))
  }

  test("Scala Params to Json using option Json4sNative") {
    val params =
      Seq(("algo", AlgorithmParams("parameter")), ("algo2", AlgorithmParams("parameter2")))
    val json = JsonExtractor.paramsToJson(JsonExtractorOption.Json4sNative, params)

    json should be (org.json4s.native.Serialization.write(params)(Utils.json4sDefaultFormats))
  }

  test("Mixed Java and Scala Params to Json using option Both") {
    val params =
      Seq(("scala", AlgorithmParams("parameter")), ("java", new JavaParams("parameter2")))
    val json = JsonExtractor.paramsToJson(JsonExtractorOption.Both, params)

    json should be ("""[{"scala":{"a":"parameter"}},{"java":{"p":"parameter2"}}]""")
  }

  test("Serializing Scala EngineParams works using option Json4sNative") {
    val ep = new EngineParams(
      dataSourceParams = ("ds", DataSourceParams("dsp")),
      algorithmParamsList = Seq(("a0", AlgorithmParams("ap"))))

    val json = JsonExtractor.engineParamsToJson(JsonExtractorOption.Json4sNative, ep)

    json should be (
      """{"dataSourceParams":{"ds":{"a":"dsp"}},"preparatorParams":{"":{}},""" +
        """"algorithmParamsList":[{"a0":{"a":"ap"}}],"servingParams":{"":{}}}""")
  }

  test("Serializing Java EngineParams works using option Gson") {
    val ep = new EngineParams(
      dataSourceParams = ("ds", new JavaParams("dsp")),
      algorithmParamsList = Seq(("a0", new JavaParams("ap")), ("a1", new JavaParams("ap2"))))

    val json = JsonExtractor.engineParamsToJson(JsonExtractorOption.Gson, ep)

    json should be (
      """{"dataSourceParams":{"ds":{"p":"dsp"}},"preparatorParams":{"":{}},""" +
        """"algorithmParamsList":[{"a0":{"p":"ap"}},{"a1":{"p":"ap2"}}],"servingParams":{"":{}}}""")
  }

  test("Serializing Java EngineParams works using option Both") {
    val ep = new EngineParams(
      dataSourceParams = ("ds", new JavaParams("dsp")),
      algorithmParamsList = Seq(("a0", new JavaParams("ap")), ("a1", new JavaParams("ap2"))))

    val json = JsonExtractor.engineParamsToJson(JsonExtractorOption.Both, ep)

    json should be (
      """{"dataSourceParams":{"ds":{"p":"dsp"}},"preparatorParams":{"":{}},""" +
        """"algorithmParamsList":[{"a0":{"p":"ap"}},{"a1":{"p":"ap2"}}],"servingParams":{"":{}}}""")
  }
}

private case class AlgorithmParams(a: String) extends Params

private case class DataSourceParams(a: String) extends Params

private case class ScalaQuery(string: String, optional: Option[String], default: String = "default")

private class UpperCaseFormat extends CustomSerializer[ScalaQuery](format => ( {
  case JObject(JField("string", JString(string)) ::
    JField("optional", JString(optional)) ::
    JField("default", JString(default)) ::
    Nil) => ScalaQuery(string.toUpperCase, Some(optional.toUpperCase), default.toUpperCase)
}, {
  case x: ScalaQuery =>
    JObject(
      JField("string", JString(x.string.toUpperCase)),
      JField("optional", JString(x.optional.get.toUpperCase)),
      JField("default", JString(x.default.toUpperCase)))
}))