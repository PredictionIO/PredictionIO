/**
 * Copyright 2014 TappingStone, Inc.
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

package io.prediction.controller.java;

import io.prediction.controller.Params;

import java.util.ArrayList;
import java.util.List;

import scala.Tuple2;

/**
 * Convenient builder class for assembling different types of parameters into a
 * single container of EngineParams.
 */
public class JavaEngineParamsBuilder {
  private Params dataSourceParams = new EmptyParams();
  private Params preparatorParams = new EmptyParams();
  private List<Tuple2<String, Params>> algorithmParamsList =
    new ArrayList<Tuple2<String, Params>>();
  private Params servingParams = new EmptyParams();

  /**
   * Instantiates an empty EngineParams builder.
   */
  public JavaEngineParamsBuilder() {}

  /**
   * Set the Data Source parameters of the Engine.
   */
  public JavaEngineParamsBuilder dataSourceParams(Params p) {
    dataSourceParams = p;
    return this;
  }

  /**
   * Set the Preparator parameters of the Engine.
   */
  public JavaEngineParamsBuilder preparatorParams(Params p) {
    preparatorParams = p;
    return this;
  }

  /**
   * Add an Algorithm parameter to the Engine.
   */
  public JavaEngineParamsBuilder addAlgorithmParams(String algorithmName, Params p) {
    algorithmParamsList.add(new Tuple2<String, Params>(algorithmName, p));
    return this;
  }

  /**
   * Set the Serving parameters of the Engine.
   */
  public JavaEngineParamsBuilder servingParams(Params p) {
    servingParams = p;
    return this;
  }

  /**
   * Build and return an instance of EngineParams.
   */
  public JavaEngineParams build() {
    return new JavaEngineParams(
        dataSourceParams, preparatorParams, algorithmParamsList, servingParams);
  }
}
