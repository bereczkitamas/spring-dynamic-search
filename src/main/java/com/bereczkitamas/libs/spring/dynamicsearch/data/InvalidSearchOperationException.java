package com.bereczkitamas.libs.spring.dynamicsearch.data;

import lombok.Getter;

import java.util.Map;

public class InvalidSearchOperationException extends RuntimeException {

  @Getter
  private final Map<String, Object> variables;

  public InvalidSearchOperationException(String message, SearchOperation operation, String field) {
    super(message);
    variables = Map.of("operation", operation.name(), "field", field);
  }
}
