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
 * and Serving classes as an Engine. This builder further simplify the process
 * by supplying default identity data preparator and first serving classes.
 *
 * @param <TD> Training Data
 * @param <DP> Data Parameters
 * @param <Q> Input Query
 * @param <P> Output Prediction
 * @param <A> Actual Value (for evaluation)
 */
public class JavaSimpleEngineBuilder<TD, DP, Q, P, A>
  extends JavaEngineBuilder<TD, DP, TD, Q, P, A> {

  /**
   * Set the Data Source class of this Engine.
   */
  @Override
  public JavaSimpleEngineBuilder<TD, DP, Q, P, A> dataSourceClass(
      Class<? extends LJavaDataSource<? extends Params, DP, TD, Q, A>> cls) {
    super.dataSourceClass = cls;
    return this;
  }

  /**
   * Set the Preparator class of this Engine.
   */
  @Override
  public JavaSimpleEngineBuilder<TD, DP, Q, P, A> preparatorClass(
      Class<? extends LJavaPreparator<? extends Params, TD, TD>> cls) {
    super.preparatorClass = cls;
    return this;
  }

  /**
   * Set the Preparator class of this Engine as IdentityPreparator
   */
  public JavaSimpleEngineBuilder<TD, DP, Q, P, A> preparatorClass() {
    super.preparatorClass = LJavaIdentityPreparator.apply(this);
    return this;
  }

  /**
   * Add an Algorithm class to this Engine. If the engine does not already have
   * a preparator, it will add an identity preparator to the engine.
   */
  @Override
  public JavaSimpleEngineBuilder<TD, DP, Q, P, A> addAlgorithmClass(
      String name, Class<? extends LJavaAlgorithm<? extends Params, TD, ?, Q, P>> cls) {
    super.algorithmClassMap.put(name, cls);
    return this;
  }

  /**
   * Set the Serving class of this Engine.
   */
  @Override
  public JavaSimpleEngineBuilder<TD, DP, Q, P, A> servingClass(
      Class<? extends LJavaServing<? extends Params, Q, P>> cls) {
    super.servingClass = cls;
    return this;
  }

  /**
   * Set the Serving class of this Engine as FirstServing.
   */
  public JavaSimpleEngineBuilder<TD, DP, Q, P, A> servingClass() {
    super.servingClass = LJavaFirstServing.apply(this);
    return this;
  }

  /**
   * Build and return an Engine instance.
   */
  @Override
  public JavaSimpleEngine<TD, DP, Q, P, A> build() {
    return new JavaSimpleEngine<TD, DP, Q, P, A> (
      super.dataSourceClass, super.preparatorClass, super.algorithmClassMap, super.servingClass);
  }

}
