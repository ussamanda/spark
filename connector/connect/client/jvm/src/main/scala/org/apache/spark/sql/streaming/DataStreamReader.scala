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

package org.apache.spark.sql.streaming

import scala.collection.JavaConverters._

import org.apache.spark.annotation.Evolving
import org.apache.spark.connect.proto.Read.DataSource
import org.apache.spark.internal.Logging
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.encoders.AgnosticEncoders.StringEncoder
import org.apache.spark.sql.types.StructType

/**
 * Interface used to load a streaming `Dataset` from external storage systems (e.g. file systems,
 * key-value stores, etc). Use `SparkSession.readStream` to access this.
 *
 * @since 3.5.0
 */
@Evolving
final class DataStreamReader private[sql] (sparkSession: SparkSession) extends Logging {

  /**
   * Specifies the input data source format.
   *
   * @since 3.5.0
   */
  def format(source: String): DataStreamReader = {
    sourceBuilder.setFormat(source)
    this
  }

  /**
   * Specifies the input schema. Some data sources (e.g. JSON) can infer the input schema
   * automatically from data. By specifying the schema here, the underlying data source can skip
   * the schema inference step, and thus speed up data loading.
   *
   * @since 3.5.0
   */
  def schema(schema: StructType): DataStreamReader = {
    if (schema != null) {
      sourceBuilder.setSchema(schema.json) // Use json. DDL does not retail all the attributes.
    }
    this
  }

  /**
   * Specifies the schema by using the input DDL-formatted string. Some data sources (e.g. JSON)
   * can infer the input schema automatically from data. By specifying the schema here, the
   * underlying data source can skip the schema inference step, and thus speed up data loading.
   *
   * @since 3.5.0
   */
  def schema(schemaString: String): DataStreamReader = {
    sourceBuilder.setSchema(schemaString)
    this
  }

  /**
   * Adds an input option for the underlying data source.
   *
   * @since 3.5.0
   */
  def option(key: String, value: String): DataStreamReader = {
    sourceBuilder.putOptions(key, value)
    this
  }

  /**
   * Adds an input option for the underlying data source.
   *
   * @since 3.5.0
   */
  def option(key: String, value: Boolean): DataStreamReader = option(key, value.toString)

  /**
   * Adds an input option for the underlying data source.
   *
   * @since 3.5.0
   */
  def option(key: String, value: Long): DataStreamReader = option(key, value.toString)

  /**
   * Adds an input option for the underlying data source.
   *
   * @since 3.5.0
   */
  def option(key: String, value: Double): DataStreamReader = option(key, value.toString)

  /**
   * (Scala-specific) Adds input options for the underlying data source.
   *
   * @since 3.5.0
   */
  def options(options: scala.collection.Map[String, String]): DataStreamReader = {
    this.options(options.asJava)
    this
  }

  /**
   * (Java-specific) Adds input options for the underlying data source.
   *
   * @since 3.5.0
   */
  def options(options: java.util.Map[String, String]): DataStreamReader = {
    sourceBuilder.putAllOptions(options)
    this
  }

  /**
   * Loads input data stream in as a `DataFrame`, for data streams that don't require a path (e.g.
   * external key-value stores).
   *
   * @since 3.5.0
   */
  def load(): DataFrame = {
    sparkSession.newDataFrame { relationBuilder =>
      relationBuilder.getReadBuilder
        .setIsStreaming(true)
        .setDataSource(sourceBuilder.build())
    }
  }

  /**
   * Loads input in as a `DataFrame`, for data streams that read from some path.
   *
   * @since 3.5.0
   */
  def load(path: String): DataFrame = {
    sourceBuilder.clearPaths()
    sourceBuilder.addPaths(path)
    load()
  }

  /**
   * Loads a JSON file stream and returns the results as a `DataFrame`.
   *
   * <a href="http://jsonlines.org/">JSON Lines</a> (newline-delimited JSON) is supported by
   * default. For JSON (one record per file), set the `multiLine` option to true.
   *
   * This function goes through the input once to determine the input schema. If you know the
   * schema in advance, use the version that specifies the schema to avoid the extra scan.
   *
   * You can set the following option(s): <ul> <li>`maxFilesPerTrigger` (default: no max limit):
   * sets the maximum number of new files to be considered in every trigger.</li> </ul>
   *
   * You can find the JSON-specific options for reading JSON file stream in <a
   * href="https://spark.apache.org/docs/latest/sql-data-sources-json.html#data-source-option">
   * Data Source Option</a> in the version you use.
   *
   * @since 3.5.0
   */
  def json(path: String): DataFrame = {
    format("json").load(path)
  }

  /**
   * Loads a CSV file stream and returns the result as a `DataFrame`.
   *
   * This function will go through the input once to determine the input schema if `inferSchema`
   * is enabled. To avoid going through the entire data once, disable `inferSchema` option or
   * specify the schema explicitly using `schema`.
   *
   * You can set the following option(s): <ul> <li>`maxFilesPerTrigger` (default: no max limit):
   * sets the maximum number of new files to be considered in every trigger.</li> </ul>
   *
   * You can find the CSV-specific options for reading CSV file stream in <a
   * href="https://spark.apache.org/docs/latest/sql-data-sources-csv.html#data-source-option">
   * Data Source Option</a> in the version you use.
   *
   * @since 3.5.0
   */
  def csv(path: String): DataFrame = format("csv").load(path)

  /**
   * Loads a ORC file stream, returning the result as a `DataFrame`.
   *
   * You can set the following option(s): <ul> <li>`maxFilesPerTrigger` (default: no max limit):
   * sets the maximum number of new files to be considered in every trigger.</li> </ul>
   *
   * ORC-specific option(s) for reading ORC file stream can be found in <a href=
   * "https://spark.apache.org/docs/latest/sql-data-sources-orc.html#data-source-option"> Data
   * Source Option</a> in the version you use.
   *
   * @since 3.5.0
   */
  def orc(path: String): DataFrame = format("orc").load(path)

  /**
   * Loads a Parquet file stream, returning the result as a `DataFrame`.
   *
   * You can set the following option(s): <ul> <li>`maxFilesPerTrigger` (default: no max limit):
   * sets the maximum number of new files to be considered in every trigger.</li> </ul>
   *
   * Parquet-specific option(s) for reading Parquet file stream can be found in <a href=
   * "https://spark.apache.org/docs/latest/sql-data-sources-parquet.html#data-source-option"> Data
   * Source Option</a> in the version you use.
   *
   * @since 3.5.0
   */
  def parquet(path: String): DataFrame = format("parquet").load(path)

  /**
   * Loads text files and returns a `DataFrame` whose schema starts with a string column named
   * "value", and followed by partitioned columns if there are any. The text files must be encoded
   * as UTF-8.
   *
   * By default, each line in the text files is a new row in the resulting DataFrame. For example:
   * {{{
   *   // Scala:
   *   spark.readStream.text("/path/to/directory/")
   *
   *   // Java:
   *   spark.readStream().text("/path/to/directory/")
   * }}}
   *
   * You can set the following option(s): <ul> <li>`maxFilesPerTrigger` (default: no max limit):
   * sets the maximum number of new files to be considered in every trigger.</li> </ul>
   *
   * You can find the text-specific options for reading text files in <a
   * href="https://spark.apache.org/docs/latest/sql-data-sources-text.html#data-source-option">
   * Data Source Option</a> in the version you use.
   *
   * @since 3.5.0
   */
  def text(path: String): DataFrame = format("text").load(path)

  /**
   * Loads text file(s) and returns a `Dataset` of String. The underlying schema of the Dataset
   * contains a single string column named "value". The text files must be encoded as UTF-8.
   *
   * If the directory structure of the text files contains partitioning information, those are
   * ignored in the resulting Dataset. To include partitioning information as columns, use `text`.
   *
   * By default, each line in the text file is a new element in the resulting Dataset. For
   * example:
   * {{{
   *   // Scala:
   *   spark.readStream.textFile("/path/to/spark/README.md")
   *
   *   // Java:
   *   spark.readStream().textFile("/path/to/spark/README.md")
   * }}}
   *
   * You can set the text-specific options as specified in `DataStreamReader.text`.
   *
   * @param path
   *   input path
   * @since 3.5.0
   */
  def textFile(path: String): Dataset[String] = {
    text(path).select("value").as[String](StringEncoder)
  }

  private val sourceBuilder = DataSource.newBuilder()
}
