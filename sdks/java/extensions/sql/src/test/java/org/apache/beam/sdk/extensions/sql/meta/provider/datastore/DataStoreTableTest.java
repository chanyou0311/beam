/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.extensions.sql.meta.provider.datastore;

import static com.google.datastore.v1.client.DatastoreHelper.makeKey;
import static com.google.datastore.v1.client.DatastoreHelper.makeValue;
import static org.apache.beam.sdk.extensions.sql.utils.DateTimeUtils.parseTimestampWithUTCTimeZone;
import static org.apache.beam.sdk.schemas.Schema.FieldType.BOOLEAN;
import static org.apache.beam.sdk.schemas.Schema.FieldType.BYTES;
import static org.apache.beam.sdk.schemas.Schema.FieldType.DATETIME;
import static org.apache.beam.sdk.schemas.Schema.FieldType.DOUBLE;
import static org.apache.beam.sdk.schemas.Schema.FieldType.INT64;
import static org.apache.beam.sdk.schemas.Schema.FieldType.STRING;
import static org.apache.beam.sdk.schemas.Schema.FieldType.array;

import com.google.datastore.v1.Entity;
import com.google.datastore.v1.Key;
import com.google.datastore.v1.Value;
import com.google.protobuf.ByteString;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.apache.beam.sdk.extensions.sql.impl.utils.CalciteUtils;
import org.apache.beam.sdk.extensions.sql.meta.provider.datastore.DataStoreV1Table.EntityToRowConverter;
import org.apache.beam.sdk.extensions.sql.meta.provider.datastore.DataStoreV1Table.RowToEntityConverter;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.schemas.Schema.FieldType;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.Row;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DataStoreTableTest {
  private static final String KIND = "kind";
  private static final String UUID_VALUE = UUID.randomUUID().toString();
  private static final Key.Builder KEY = makeKey(KIND, UUID_VALUE);
  private static final DateTime DATE_TIME = parseTimestampWithUTCTimeZone("2018-05-28 20:17:40");

  private static final Schema NESTED_ROW_SCHEMA =
      Schema.builder().addNullableField("nestedLong", INT64).build();
  private static final Schema KEY_ROW_SCHEMA =
      Schema.builder()
          .addNullableField("kind", STRING)
          .addNullableField("id", INT64)
          .addNullableField("name", STRING)
          .build();
  private static final Schema SCHEMA =
      Schema.builder()
          .addNullableField("__key__", array(FieldType.row(KEY_ROW_SCHEMA)))
          .addNullableField("long", INT64)
          .addNullableField("bool", BOOLEAN)
          .addNullableField("datetime", DATETIME)
          .addNullableField("array", array(STRING))
          .addNullableField("rowArray", array(FieldType.row(NESTED_ROW_SCHEMA)))
          .addNullableField("double", DOUBLE)
          .addNullableField("bytes", BYTES)
          .addNullableField("string", CalciteUtils.CHAR)
          .addNullableField("nullable", INT64)
          .build();
  private static final Entity NESTED_ENTITY =
      Entity.newBuilder().putProperties("nestedLong", makeValue(Long.MIN_VALUE).build()).build();
  private static final Entity ENTITY =
      Entity.newBuilder()
          .setKey(KEY)
          .putProperties("long", makeValue(Long.MAX_VALUE).build())
          .putProperties("bool", makeValue(true).build())
          .putProperties("datetime", makeValue(DATE_TIME.toDate()).build())
          .putProperties("array", makeValue(makeValue("string1"), makeValue("string2")).build())
          .putProperties(
              "rowArray",
              makeValue(Collections.singletonList(makeValue(NESTED_ENTITY).build())).build())
          .putProperties("double", makeValue(Double.MAX_VALUE).build())
          .putProperties(
              "bytes", makeValue(ByteString.copyFrom("hello", Charset.defaultCharset())).build())
          .putProperties("string", makeValue("string").build())
          .putProperties("nullable", Value.newBuilder().build())
          .build();
  private static final Row ROW =
      row(
          SCHEMA,
          Collections.singletonList(row(KEY_ROW_SCHEMA, KIND, 0L, UUID_VALUE)),
          Long.MAX_VALUE,
          true,
          DATE_TIME,
          Arrays.asList("string1", "string2"),
          Collections.singletonList(row(NESTED_ROW_SCHEMA, Long.MIN_VALUE)),
          Double.MAX_VALUE,
          "hello".getBytes(Charset.defaultCharset()),
          "string",
          null);

  @Rule public transient TestPipeline pipeline = TestPipeline.create();

  @Test
  public void testEntityToRowConverter() {
    PCollection<Row> result =
        pipeline.apply(Create.of(ENTITY)).apply(ParDo.of(EntityToRowConverter.create(SCHEMA)));
    PAssert.that(result).containsInAnyOrder(ROW);

    pipeline.run().waitUntilFinish();
  }

  @Test
  public void testRowToEntityConverter() {
    PCollection<Entity> result =
        pipeline
            .apply(Create.of(ROW))
            .apply(ParDo.of(RowToEntityConverter.createTest(UUID_VALUE, SCHEMA, KIND)));
    PAssert.that(result).containsInAnyOrder(ENTITY);

    pipeline.run().waitUntilFinish();
  }

  private static Row row(Schema schema, Object... values) {
    return Row.withSchema(schema).addValues(values).build();
  }
}
