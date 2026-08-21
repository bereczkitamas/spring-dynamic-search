package com.bereczkitamas.libs.spring.dynamicsearch.data;

import lombok.Getter;

import java.util.Map;

public class InvalidSearchFieldException extends RuntimeException {

  @Getter
  private final Map<String, Object> variables;

  public InvalidSearchFieldException(String message, String field) {
    super(message);
    variables = Map.of("field", field);
  }
}
