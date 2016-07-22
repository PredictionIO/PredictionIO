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

package org.apache.predictionio.workflow;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class JavaQueryTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType().equals(JavaQuery.class)) {
            return (TypeAdapter<T>) new TypeAdapter<JavaQuery>() {
                public void write(JsonWriter out, JavaQuery value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                    } else {
                        out.beginObject();
                        out.name("q").value(value.getQ().toUpperCase());
                        out.endObject();
                    }
                }

                public JavaQuery read(JsonReader reader) throws IOException {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull();
                        return null;
                    } else {
                        reader.beginObject();
                        reader.nextName();
                        String q = reader.nextString();
                        reader.endObject();
                        return new JavaQuery(q.toUpperCase());
                    }
                }
            };
        } else {
            return null;
        }
    }
}
