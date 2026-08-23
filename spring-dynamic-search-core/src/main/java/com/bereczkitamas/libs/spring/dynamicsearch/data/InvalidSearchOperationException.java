package com.bereczkitamas.libs.spring.dynamicsearch.data;

import java.util.Map;
import lombok.Getter;

public class InvalidSearchOperationException extends RuntimeException {

  @Getter
  private final Map<String, Object> variables;

  public InvalidSearchOperationException(String message, SearchOperation operation, String field) {
    super(message);
    variables = Map.of("operation", operation != null ? operation.name() : "null", "field", field);
  }

  public String getFieldName() {
    return (String) variables.get("field");
  }

  public String getOperationName() {
    return (String) variables.get("operation");
  }
}
