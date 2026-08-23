package com.bereczkitamas.libs.spring.dynamicsearch.data;

import java.util.Collections;
import java.util.Set;
import lombok.Data;

@Data
public class FieldMapping {
  private final String documentField;
  private final Class<?> type;
  private final Set<SearchOperation> allowedOperations;
  private final JoinDescriptor join;
  private final boolean projectable;
  private final boolean searchable;
  private final boolean alwaysIncluded; // always in response (e.g., id)
  private final String description;
  private final Set<String> examples;

  public FieldMapping(
      String documentField,
      Class<?> type,
      Set<SearchOperation> allowedOperations,
      JoinDescriptor join,
      boolean projectable,
      boolean searchable,
      boolean alwaysIncluded) {
    this(documentField, type, allowedOperations, join, projectable, searchable, alwaysIncluded, "", Collections.emptySet());
  }

  public FieldMapping(
      String documentField,
      Class<?> type,
      Set<SearchOperation> allowedOperations,
      JoinDescriptor join,
      boolean projectable,
      boolean searchable,
      boolean alwaysIncluded,
      String description,
      Set<String> examples) {
    this.documentField = documentField;
    this.type = type;
    this.allowedOperations = allowedOperations != null ? allowedOperations : Collections.emptySet();
    this.join = join;
    this.projectable = projectable;
    this.searchable = searchable;
    this.alwaysIncluded = alwaysIncluded;
    this.description = description != null ? description : "";
    this.examples = examples != null ? Collections.unmodifiableSet(examples) : Collections.emptySet();
  }

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
