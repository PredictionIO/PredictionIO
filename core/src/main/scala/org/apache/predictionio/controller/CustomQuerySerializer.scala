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

package org.apache.predictionio.controller

import org.apache.predictionio.core.BaseQuerySerializer

/** If your query class cannot be automatically serialized/deserialized to/from
  * JSON, implement a trait by extending this trait, and overriding the
  * `querySerializer` member with your
  * [[https://github.com/json4s/json4s#serializing-non-supported-types custom JSON4S serializer]].
  * Algorithm and serving classes using your query class would only need to mix
  * in the trait to enable the custom serializer.
  *
  * @group Helper
  */
trait CustomQuerySerializer extends BaseQuerySerializer

/** DEPRECATED. Use [[CustomQuerySerializer]] instead.
  *
  * @group Helper
  */
@deprecated("Use CustomQuerySerializer instead.", "0.9.2")
trait WithQuerySerializer extends CustomQuerySerializer

