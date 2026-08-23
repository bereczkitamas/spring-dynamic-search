package com.bereczkitamas.libs.spring.dynamicsearch.annotation;

import com.bereczkitamas.libs.spring.dynamicsearch.data.FieldMapping;
import com.bereczkitamas.libs.spring.dynamicsearch.data.JoinDescriptor;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchOperation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Scans classes, superclasses, record components, and methods for {@link SearchableField} and
 * {@link JoinedField} annotations to generate {@link FieldMapping} definitions.
 */
public final class SearchAnnotationScanner {

  private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS =
      Map.of(
          int.class, Integer.class,
          long.class, Long.class,
          double.class, Double.class,
          float.class, Float.class,
          boolean.class, Boolean.class,
          byte.class, Byte.class,
          short.class, Short.class,
          char.class, Character.class);

  private SearchAnnotationScanner() {}

  /**
   * Scans the given class and returns a map of property names to {@link FieldMapping} definitions.
   *
   * @param targetClass the entity or DTO class to scan
   * @return an immutable map of property names to field mappings
   */
  public static Map<String, FieldMapping> scan(Class<?> targetClass) {
    if (targetClass == null) {
      return Collections.emptyMap();
    }

    Map<String, FieldMapping> mappings = new HashMap<>();

    // 1. Scan class-level @JoinedField / @JoinedFields annotations
    scanClassAnnotations(targetClass, mappings);

    // 2. Scan class hierarchy for fields, record components, and methods
    Class<?> current = targetClass;
    while (current != null && current != Object.class) {
      if (current.isRecord()) {
        scanRecordComponents(current, mappings);
      }
      scanFields(current, mappings);
      scanMethods(current, mappings);
      current = current.getSuperclass();
    }

    return Collections.unmodifiableMap(mappings);
  }

  private static void scanClassAnnotations(Class<?> targetClass, Map<String, FieldMapping> mappings) {
    JoinedField[] joinedFields = targetClass.getAnnotationsByType(JoinedField.class);
    for (JoinedField joined : joinedFields) {
      String propertyName = joined.name();
      if (propertyName.isBlank()) {
        throw new IllegalArgumentException(
            "Class-level @JoinedField on %s must specify a non-empty name()".formatted(targetClass.getName()));
      }
      if (!mappings.containsKey(propertyName)) {
        mappings.put(propertyName, buildJoinedMapping(propertyName, joined, joined.type()));
      }
    }
  }

  private static void scanRecordComponents(Class<?> recordClass, Map<String, FieldMapping> mappings) {
    for (RecordComponent component : recordClass.getRecordComponents()) {
      String propertyName = component.getName();
      SearchableField searchable = component.getAnnotation(SearchableField.class);
      if (searchable != null && !mappings.containsKey(propertyName)) {
        mappings.put(propertyName, buildSearchableMapping(propertyName, searchable, component.getType()));
      }

      JoinedField[] joinedFields = component.getAnnotationsByType(JoinedField.class);
      for (JoinedField joined : joinedFields) {
        String name = joined.name().isBlank() ? propertyName : joined.name();
        if (!mappings.containsKey(name)) {
          mappings.put(name, buildJoinedMapping(name, joined, component.getType()));
        }
      }
    }
  }

  private static void scanFields(Class<?> currentClass, Map<String, FieldMapping> mappings) {
    for (Field field : currentClass.getDeclaredFields()) {
      String propertyName = field.getName();
      SearchableField searchable = field.getAnnotation(SearchableField.class);
      if (searchable != null && !mappings.containsKey(propertyName)) {
        mappings.put(propertyName, buildSearchableMapping(propertyName, searchable, field.getType()));
      }

      JoinedField[] joinedFields = field.getAnnotationsByType(JoinedField.class);
      for (JoinedField joined : joinedFields) {
        String name = joined.name().isBlank() ? propertyName : joined.name();
        if (!mappings.containsKey(name)) {
          mappings.put(name, buildJoinedMapping(name, joined, field.getType()));
        }
      }
    }
  }

  private static void scanMethods(Class<?> currentClass, Map<String, FieldMapping> mappings) {
    for (Method method : currentClass.getDeclaredMethods()) {
      SearchableField searchable = method.getAnnotation(SearchableField.class);
      JoinedField[] joinedFields = method.getAnnotationsByType(JoinedField.class);

      if (searchable == null && joinedFields.length == 0) {
        continue;
      }

      String propertyName = extractPropertyName(method.getName());
      Class<?> returnType = method.getReturnType();

      if (searchable != null && !mappings.containsKey(propertyName)) {
        mappings.put(propertyName, buildSearchableMapping(propertyName, searchable, returnType));
      }

      for (JoinedField joined : joinedFields) {
        String name = joined.name().isBlank() ? propertyName : joined.name();
        if (!mappings.containsKey(name)) {
          mappings.put(name, buildJoinedMapping(name, joined, returnType));
        }
      }
    }
  }

  private static FieldMapping buildSearchableMapping(
      String propertyName, SearchableField annotation, Class<?> fallbackType) {
    String docField = annotation.documentField().isBlank() ? propertyName : annotation.documentField();
    Class<?> type = resolveType(annotation.type(), fallbackType);
    Set<SearchOperation> ops =
        annotation.operations().length > 0
            ? Set.of(annotation.operations())
            : defaultOperationsForType(type);

    return new FieldMapping(
        docField,
        type,
        ops,
        null,
        annotation.projectable(),
        annotation.searchable(),
        annotation.alwaysIncluded(),
        annotation.description(),
        Set.of(annotation.examples()));
  }

  private static FieldMapping buildJoinedMapping(
      String propertyName, JoinedField annotation, Class<?> fallbackType) {
    String docField =
        annotation.documentField().isBlank()
            ? (annotation.as() + "." + propertyName)
            : annotation.documentField();
    Class<?> type = resolveType(annotation.type(), fallbackType);
    Set<SearchOperation> ops =
        annotation.operations().length > 0
            ? Set.of(annotation.operations())
            : defaultOperationsForType(type);

    JoinDescriptor join =
        new JoinDescriptor(
            annotation.collection(),
            annotation.localField(),
            annotation.foreignField(),
            annotation.as(),
            annotation.singleResult());

    return new FieldMapping(
        docField,
        type,
        ops,
        join,
        annotation.projectable(),
        annotation.searchable(),
        annotation.alwaysIncluded(),
        annotation.description(),
        Set.of(annotation.examples()));
  }

  private static Class<?> resolveType(Class<?> configuredType, Class<?> fallbackType) {
    Class<?> rawType = (configuredType != null && configuredType != void.class) ? configuredType : fallbackType;
    if (rawType != null && rawType.isPrimitive()) {
      return PRIMITIVE_WRAPPERS.getOrDefault(rawType, rawType);
    }
    return rawType;
  }

  public static Set<SearchOperation> defaultOperationsForType(Class<?> type) {
    if (type == null) {
      return EnumSet.allOf(SearchOperation.class);
    }

    if (String.class.isAssignableFrom(type)) {
      return Set.of(
          SearchOperation.EQUALS,
          SearchOperation.NOT_EQUALS,
          SearchOperation.LIKE,
          SearchOperation.STARTS_WITH,
          SearchOperation.ENDS_WITH,
          SearchOperation.REGEX,
          SearchOperation.IN,
          SearchOperation.NOT_IN,
          SearchOperation.IS_NULL,
          SearchOperation.IS_NOT_NULL,
          SearchOperation.IS_EMPTY,
          SearchOperation.IS_NOT_EMPTY,
          SearchOperation.EXISTS,
          SearchOperation.DOES_NOT_EXIST);
    }

    if (Number.class.isAssignableFrom(type)) {
      return Set.of(
          SearchOperation.EQUALS,
          SearchOperation.NOT_EQUALS,
          SearchOperation.GREATER_THAN,
          SearchOperation.LESS_THAN,
          SearchOperation.GREATER_THAN_OR_EQUAL,
          SearchOperation.LESS_THAN_OR_EQUAL,
          SearchOperation.IN,
          SearchOperation.NOT_IN,
          SearchOperation.BETWEEN,
          SearchOperation.NOT_BETWEEN,
          SearchOperation.IS_NULL,
          SearchOperation.IS_NOT_NULL,
          SearchOperation.EXISTS,
          SearchOperation.DOES_NOT_EXIST);
    }

    if (Temporal.class.isAssignableFrom(type) || Date.class.isAssignableFrom(type)) {
      return Set.of(
          SearchOperation.EQUALS,
          SearchOperation.NOT_EQUALS,
          SearchOperation.GREATER_THAN,
          SearchOperation.LESS_THAN,
          SearchOperation.GREATER_THAN_OR_EQUAL,
          SearchOperation.LESS_THAN_OR_EQUAL,
          SearchOperation.IN,
          SearchOperation.NOT_IN,
          SearchOperation.BETWEEN,
          SearchOperation.NOT_BETWEEN,
          SearchOperation.IS_NULL,
          SearchOperation.IS_NOT_NULL,
          SearchOperation.EXISTS,
          SearchOperation.DOES_NOT_EXIST);
    }

    if (Boolean.class.isAssignableFrom(type)) {
      return Set.of(
          SearchOperation.EQUALS,
          SearchOperation.NOT_EQUALS,
          SearchOperation.IN,
          SearchOperation.NOT_IN,
          SearchOperation.IS_NULL,
          SearchOperation.IS_NOT_NULL,
          SearchOperation.EXISTS,
          SearchOperation.DOES_NOT_EXIST);
    }

    if (Collection.class.isAssignableFrom(type) || type.isArray()) {
      return Set.of(
          SearchOperation.IN,
          SearchOperation.NOT_IN,
          SearchOperation.CONTAINS_ALL,
          SearchOperation.IS_EMPTY,
          SearchOperation.IS_NOT_EMPTY,
          SearchOperation.IS_NULL,
          SearchOperation.IS_NOT_NULL,
          SearchOperation.EXISTS,
          SearchOperation.DOES_NOT_EXIST);
    }

    return Set.of(
        SearchOperation.EQUALS,
        SearchOperation.NOT_EQUALS,
        SearchOperation.IN,
        SearchOperation.NOT_IN,
        SearchOperation.IS_NULL,
        SearchOperation.IS_NOT_NULL,
        SearchOperation.EXISTS,
        SearchOperation.DOES_NOT_EXIST);
  }

  private static String extractPropertyName(String methodName) {
    if (methodName.startsWith("get") && methodName.length() > 3) {
      return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
    }
    if (methodName.startsWith("is") && methodName.length() > 2) {
      return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
    }
    return methodName;
  }
}
