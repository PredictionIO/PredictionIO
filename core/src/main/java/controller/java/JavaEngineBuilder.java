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

import java.util.Map;
import java.util.HashMap;

/**
 * A convenient builder class for linking up Data Source, Preparator, Algorithm,
 * and Serving classes as an Engine.
 *
 * @param <TD> Training Data
 * @param <DP> Data Parameters
 * @param <PD> Prepared Data
 * @param <Q> Input Query
 * @param <P> Output Prediction
 * @param <A> Actual Value
 */
public class JavaEngineBuilder<TD, DP, PD, Q, P, A> {
  /** Data Source class. Default to null. */
  protected Class<? extends LJavaDataSource<? extends Params, DP, TD, Q, A>> dataSourceClass = null;
  /** Preparator class. Default to null. */
  protected Class<? extends LJavaPreparator<? extends Params, TD, PD>> preparatorClass = null;
  /** Map of Algorithm names to respective classes. Default to empty Map. */
  protected Map<String, Class<? extends LJavaAlgorithm<? extends Params, PD, ?, Q, P>>>
    algorithmClassMap = new HashMap <> ();
  /** Serving class. Default to null. */
  protected Class<? extends LJavaServing<? extends Params, Q, P>> servingClass = null;

  /**
   * Instantiate an empty Java-based Engine builder.
   */
  public JavaEngineBuilder() {}

  /**
   * Set the Data Source class of this Engine.
   */
  public JavaEngineBuilder<TD, DP, PD, Q, P, A> dataSourceClass(
      Class<? extends LJavaDataSource<? extends Params, DP, TD, Q, A>> cls) {
    dataSourceClass = cls;
    return this;
  }

  /**
   * Set the Preparator class of this Engine.
   */
  public JavaEngineBuilder<TD, DP, PD, Q, P, A> preparatorClass(
      Class<? extends LJavaPreparator<? extends Params, TD, PD>> cls) {
    preparatorClass = cls;
    return this;
  }

  /**
   * Add an Algorithm class to this Engine.
   */
  public JavaEngineBuilder<TD, DP, PD, Q, P, A> addAlgorithmClass(
      String name, Class<? extends LJavaAlgorithm<? extends Params, PD, ?, Q, P>> cls) {
    algorithmClassMap.put(name, cls);
    return this;
  }

  /**
   * Set the Serving class of this Engine.
   */
  public JavaEngineBuilder<TD, DP, PD, Q, P, A> servingClass(
      Class<? extends LJavaServing<? extends Params, Q, P>> cls) {
    servingClass = cls;
    return this;
  }

  /**
   * Build and return an Engine instance.
   */
  public JavaEngine<TD, DP, PD, Q, P, A> build() {
    return new JavaEngine<> (dataSourceClass, preparatorClass, algorithmClassMap, servingClass);
  }

  @Override public String toString() {
    return "JavaEngineBuilder ds=" + dataSourceClass + " p=" + preparatorClass + " algo=" +
      algorithmClassMap + " s=" + servingClass;
  }

}
