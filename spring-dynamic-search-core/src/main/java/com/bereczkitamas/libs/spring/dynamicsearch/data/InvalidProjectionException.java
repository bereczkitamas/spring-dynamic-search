package com.bereczkitamas.libs.spring.dynamicsearch.data;

import java.util.Map;
import lombok.Getter;

public class InvalidProjectionException extends RuntimeException {

  @Getter
  private final Map<String, Object> variables;

  public InvalidProjectionException(String message, String field) {
    super(message);
    variables = field != null ? Map.of("field", field) : Map.of();
  }

  public String getFieldName() {
    return (String) variables.get("field");
  }
}
