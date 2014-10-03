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

package io.prediction.engines.java.olditemrec.data

//import java.io.Serializable
import java.util.{ List => JList }
import java.lang.{ Integer => JInteger }
import java.lang.{ Float => JFloat }

class Prediction(val iids: JList[JInteger], val scores: JList[JFloat])
    extends Serializable {
  override def toString(): String =
    s"Prediction: ${iids.toString()} ; ${scores.toString()}"
}


/*
public class Prediction implements Serializable {
  public List<Integer> iids;
  public List<Float> scores;

  public Prediction(List<Integer> iids, List<Float> scores) {
    this.iids = iids;
    this.scores = scores;
  }

  @Override
  public String toString() {
    return "Prediction: " + iids.toString() + ";" + scores.toString();
  }
}
*/
