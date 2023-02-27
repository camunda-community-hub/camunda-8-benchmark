package org.camunda.community.benchmarks.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtils {

  private JsonUtils() {}

  private static ObjectMapper mapper;
  
  public static <T> T fromJsonFile(File file, Class<T> type)
      throws StreamReadException, DatabindException, IOException {
    return getObjectMapper().readValue(file, type);
  }

  public static <T> T fromJsonInputStream(InputStream stream, Class<T> type)
      throws StreamReadException, DatabindException, IOException {
    return getObjectMapper().readValue(stream, type);
  }

  private static ObjectMapper getObjectMapper() {
    if (mapper == null) {
      mapper = new ObjectMapper();
      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    return mapper;
  }
}
