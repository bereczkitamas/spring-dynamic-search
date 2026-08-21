package com.bereczkitamas.libs.spring.dynamicsearch.data;

import lombok.Getter;

import java.util.Map;

public class InvalidProjectionException extends RuntimeException {

  @Getter
  private final Map<String, Object> variables;

  public InvalidProjectionException(String message, String field) {
    super(message);
    variables = field != null ? Map.of("field", field) : Map.of();
  }
}
