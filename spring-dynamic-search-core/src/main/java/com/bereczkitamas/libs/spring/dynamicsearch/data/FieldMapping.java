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
  private final ArrayElementDescriptor arrayElement;

  public FieldMapping(
      String documentField,
      Class<?> type,
      Set<SearchOperation> allowedOperations,
      JoinDescriptor join,
      boolean projectable,
      boolean searchable,
      boolean alwaysIncluded) {
    this(documentField, type, allowedOperations, join, projectable, searchable, alwaysIncluded, "", Collections.emptySet(), null);
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
    this(documentField, type, allowedOperations, join, projectable, searchable, alwaysIncluded, description, examples, null);
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
      Set<String> examples,
      ArrayElementDescriptor arrayElement) {
    this.documentField = documentField;
    this.type = type;
    this.allowedOperations = allowedOperations != null ? allowedOperations : Collections.emptySet();
    this.join = join;
    this.projectable = projectable;
    this.searchable = searchable;
    this.alwaysIncluded = alwaysIncluded;
    this.description = description != null ? description : "";
    this.examples = examples != null ? Collections.unmodifiableSet(examples) : Collections.emptySet();
    this.arrayElement = arrayElement;
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

  // Array field factories
  public static FieldMapping arrayField(
      String documentField, ArrayElementDescriptor elementDescriptor, SearchOperation... ops) {
    Set<SearchOperation> operations = ops.length > 0 ? Set.of(ops) : Set.of(SearchOperation.ELEM_MATCH, SearchOperation.SIZE);
    return new FieldMapping(
        documentField,
        java.util.Collection.class,
        operations,
        null,
        true,
        true,
        false,
        "",
        Collections.emptySet(),
        elementDescriptor);
  }

  public static FieldMapping arrayField(
      String documentField, Class<?> elementClass, SearchOperation... ops) {
    return arrayField(documentField, ArrayElementDescriptor.from(elementClass), ops);
  }

  // Joined Array field factories
  public static FieldMapping joinedArray(
      String documentField, JoinDescriptor join, ArrayElementDescriptor elementDescriptor, SearchOperation... ops) {
    Set<SearchOperation> operations = ops.length > 0 ? Set.of(ops) : Set.of(SearchOperation.ELEM_MATCH, SearchOperation.SIZE);
    return new FieldMapping(
        documentField,
        java.util.Collection.class,
        operations,
        join,
        true,
        true,
        false,
        "",
        Collections.emptySet(),
        elementDescriptor);
  }

  public static FieldMapping joinedArray(
      String documentField, JoinDescriptor join, Class<?> elementClass, SearchOperation... ops) {
    return joinedArray(documentField, join, ArrayElementDescriptor.from(elementClass), ops);
  }

  public boolean isJoined() {
    return join != null;
  }

  public boolean isArrayField() {
    return arrayElement != null;
  }
}
