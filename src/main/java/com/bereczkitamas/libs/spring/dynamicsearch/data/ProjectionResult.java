package com.bereczkitamas.libs.spring.dynamicsearch.data;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProjectionResult {
  public enum Mode {
    NONE,
    INCLUDE,
    EXCLUDE
  }

  private final Mode mode;
  private final Set<String> fields;

  public static ProjectionResult none() {
    return new ProjectionResult(Mode.NONE, Set.of());
  }

  public static ProjectionResult include(Set<String> fields) {
    return new ProjectionResult(Mode.INCLUDE, fields);
  }

  public static ProjectionResult exclude(Set<String> fields) {
    return new ProjectionResult(Mode.EXCLUDE, fields);
  }

  public boolean isApplicable() {
    return mode != Mode.NONE;
  }
}
