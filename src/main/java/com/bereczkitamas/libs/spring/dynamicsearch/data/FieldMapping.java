package com.bereczkitamas.libs.spring.dynamicsearch.data;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FieldMapping {
  private final String documentField;
  private final Class<?> type;
  private final Set<SearchOperation> allowedOperations;
  private final JoinDescriptor join;
  private final boolean projectable;
  private final boolean searchable;
  private final boolean alwaysIncluded; // always in response (e.g., id)

  // Local field factory
  public static FieldMapping of(String documentField, Class<?> type, SearchOperation... ops) {
    return new FieldMapping(documentField, type, Set.of(ops), null, true, true, false);
  }

  public static FieldMapping projectionOnly(String documentField, Class<?> type) {
    return new FieldMapping(documentField, type, Set.of(), null, true, false, false);
  }

  public static FieldMapping alwaysIncluded(
      String documentField, Class<?> type, SearchOperation... ops) {
    return new FieldMapping(documentField, type, Set.of(ops), null, true, true, true);
  }

  // Joined field factory
  public static FieldMapping joined(
      String documentField, Class<?> type, JoinDescriptor join, SearchOperation... ops) {
    return new FieldMapping(documentField, type, Set.of(ops), join, true, true, false);
  }

  public boolean isJoined() {
    return join != null;
  }
}
