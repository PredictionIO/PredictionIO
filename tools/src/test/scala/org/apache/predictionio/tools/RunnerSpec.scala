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

package org.apache.predictionio.tools

import org.specs2.mutable.Specification

class RunnerSpec extends Specification {
  "groupByArgumentName" >> {
    "test1" >> {
      val test = Seq("--foo", "bar", "--flag", "--dead", "beef baz", "n00b", "--foo", "jeez")
      Runner.groupByArgumentName(test) must havePairs(
        "--foo" -> Seq("bar", "jeez"),
        "--dead" -> Seq("beef baz"))
    }

    "test2" >> {
      val test =
        Seq("--foo", "--bar", "flag", "--dead", "beef baz", "n00b", "--foo", "jeez", "--flag")
      Runner.groupByArgumentName(test) must havePairs(
        "--foo" -> Seq("jeez"),
        "--bar" -> Seq("flag"),
        "--dead" -> Seq("beef baz"))
    }
  }

  "removeArguments" >> {
    "test1" >> {
      val test = Seq("--foo", "bar", "--flag", "--dead", "beef baz", "n00b", "--foo", "jeez")
      val remove = Set("--flag", "--foo")
      Runner.removeArguments(test, remove) === Seq("--flag", "--dead", "beef baz", "n00b")
    }

    "test2" >> {
      val test =
        Seq("--foo", "--bar", "flag", "--dead", "beef baz", "n00b", "--foo", "jeez", "--flag")
      val remove = Set("--flag", "--foo")
      Runner.removeArguments(test, remove) ===
        Seq("--foo", "--bar", "flag", "--dead", "beef baz", "n00b", "--flag")
    }
  }

  "combineArguments" >> {
    "test1" >> {
      val test = Seq("--foo", "bar", "--flag", "--dead", "beef baz", "n00b", "--foo", "jeez")
      val combinators = Map("--foo" -> ((a: String, b: String) => s"$a $b"))
      Runner.combineArguments(test, combinators) ===
        Seq("--flag", "--dead", "beef baz", "n00b", "--foo", "bar jeez")
    }

    "test2" >> {
      val test =
        Seq("--foo", "--bar", "flag", "--dead", "beef baz", "n00b", "--foo", "jeez", "--flag")
      val combinators = Map("--foo" -> ((a: String, b: String) => s"$a $b"))
      Runner.combineArguments(test, combinators) ===
        Seq("--foo", "--bar", "flag", "--dead", "beef baz", "n00b", "--flag", "--foo", "jeez")
    }
  }
}
