package com.bereczkitamas.libs.spring.dynamicsearch.data;

import java.util.Map;
import lombok.Getter;

public class InvalidSearchFieldException extends RuntimeException {

  @Getter
  private final Map<String, Object> variables;

  public InvalidSearchFieldException(String message, String field) {
    super(message);
    variables = Map.of("field", field);
  }

  public String getFieldName() {
    return (String) variables.get("field");
  }
}
