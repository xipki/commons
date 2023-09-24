// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.security.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;

/**
 * JSON util class
 *
 * @author Lijun Liao (xipki)
 * @since 6.1.0
 */
public class JSON {

  private static class InstantSerializer extends JsonSerializer<Instant> {

    @Override
    public void serialize(Instant instant, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
        throws IOException {
      jsonGenerator.writeString(instant.toString());
    }

  }

  private static class InstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
        throws IOException, JacksonException {
      return Instant.parse(jsonParser.getValueAsString());
    }

  }

  public static class XiJsonModule extends SimpleModule {

    public  static XiJsonModule INSTANCE = new XiJsonModule();
    public XiJsonModule() {
      addSerializer(Instant.class,   new InstantSerializer());
      addDeserializer(Instant.class, new InstantDeserializer());
    }

  }

  private static final ObjectMapper json;
  private static final ObjectWriter prettyJson;

  static {
    json = new ObjectMapper().registerModule(XiJsonModule.INSTANCE)
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    prettyJson = new ObjectMapper().registerModule(XiJsonModule.INSTANCE)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
        .writerWithDefaultPrettyPrinter();
  }

  public static <T> T parseObject(String json, Class<T> classOfT) {
    try {
      return JSON.json.readValue(json, classOfT);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T parseObject(byte[] json, Class<T> classOfT) {
    try {
      return JSON.json.readValue(json, classOfT);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T parseObject(Path jsonFilePath, Class<T> classOfT) throws IOException {
    return json.readValue(jsonFilePath.toFile(), classOfT);
  }

  public static <T> T parseObject(File jsonFile, Class<T> classOfT) throws IOException {
    return json.readValue(jsonFile, classOfT);
  }

  /**
   * The specified stream remains open after this method returns.
   */
  public static <T> T parseObject(InputStream jsonInputStream, Class<T> classOfT) throws IOException {
    Reader noCloseReader = new InputStreamReader(jsonInputStream) {
      @Override
      public void close() {
      }
    };
    // jackson closes the stream.
    return json.readValue(noCloseReader, classOfT);
  }

  /**
   * The specified stream is closed after this method returns.
   */
  public static <T> T parseObjectAndClose(InputStream jsonInputStream, Class<T> classOfT) throws IOException {
    // jackson closes the stream.
    return json.readValue(new InputStreamReader(jsonInputStream), classOfT);
  }

  public static String toJson(Object obj) {
    try {
      return json.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static byte[] toJSONBytes(Object obj) {
    try {
      return json.writeValueAsBytes(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static String toPrettyJson(Object obj) {
    try {
      return prettyJson.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * The specified stream remains open after this method returns.
   */
  public static void writeJSON(Object object, OutputStream outputStream) {
    try {
      outputStream.write(toJSONBytes(object));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * The specified stream is closed after this method returns.
   */
  public static void writeJSONAndClose(Object object, OutputStream outputStream) {
    try {
      json.writeValue(outputStream, object);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * The specified stream remains open after this method returns.
   */
  public static void writePrettyJSON(Object object, OutputStream outputStream) {
    try {
      outputStream.write(toPrettyJson(object).getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * The specified stream is closed after this method returns.
   */
  public static void writePrettyJSONAndClose(Object object, OutputStream outputStream) {
    try {
      prettyJson.writeValue(outputStream, object);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
