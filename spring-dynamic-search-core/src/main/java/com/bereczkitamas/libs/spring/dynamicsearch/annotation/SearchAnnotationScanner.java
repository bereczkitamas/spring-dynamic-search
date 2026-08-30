package com.bereczkitamas.libs.spring.dynamicsearch.annotation;

import com.bereczkitamas.libs.spring.dynamicsearch.data.ArrayElementDescriptor;
import com.bereczkitamas.libs.spring.dynamicsearch.data.FieldMapping;
import com.bereczkitamas.libs.spring.dynamicsearch.data.JoinDescriptor;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchOperation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
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
        mappings.put(propertyName, buildJoinedMapping(propertyName, joined, joined.type(), null));
      }
    }
  }

  private static void scanRecordComponents(Class<?> recordClass, Map<String, FieldMapping> mappings) {
    for (RecordComponent component : recordClass.getRecordComponents()) {
      String propertyName = component.getName();
      SearchableField searchable = component.getAnnotation(SearchableField.class);
      if (searchable != null && !mappings.containsKey(propertyName)) {
        mappings.put(
            propertyName,
            buildSearchableMapping(
                propertyName, searchable, component.getType(), component.getGenericType()));
      }

      JoinedField[] joinedFields = component.getAnnotationsByType(JoinedField.class);
      for (JoinedField joined : joinedFields) {
        String name = joined.name().isBlank() ? propertyName : joined.name();
        if (!mappings.containsKey(name)) {
          mappings.put(name, buildJoinedMapping(name, joined, component.getType(), component.getGenericType()));
        }
      }
    }
  }

  private static void scanFields(Class<?> currentClass, Map<String, FieldMapping> mappings) {
    for (Field field : currentClass.getDeclaredFields()) {
      String propertyName = field.getName();
      SearchableField searchable = field.getAnnotation(SearchableField.class);
      if (searchable != null && !mappings.containsKey(propertyName)) {
        mappings.put(
            propertyName,
            buildSearchableMapping(propertyName, searchable, field.getType(), field.getGenericType()));
      }

      JoinedField[] joinedFields = field.getAnnotationsByType(JoinedField.class);
      for (JoinedField joined : joinedFields) {
        String name = joined.name().isBlank() ? propertyName : joined.name();
        if (!mappings.containsKey(name)) {
          mappings.put(name, buildJoinedMapping(name, joined, field.getType(), field.getGenericType()));
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
        mappings.put(
            propertyName,
            buildSearchableMapping(
                propertyName, searchable, returnType, method.getGenericReturnType()));
      }

      for (JoinedField joined : joinedFields) {
        String name = joined.name().isBlank() ? propertyName : joined.name();
        if (!mappings.containsKey(name)) {
          mappings.put(name, buildJoinedMapping(name, joined, returnType, method.getGenericReturnType()));
        }
      }
    }
  }

  private static FieldMapping buildSearchableMapping(
      String propertyName,
      SearchableField annotation,
      Class<?> fallbackType,
      Type genericType) {
    String docField = annotation.documentField().isBlank() ? propertyName : annotation.documentField();
    Class<?> type = resolveType(annotation.type(), fallbackType);

    ArrayElementDescriptor arrayElement = null;
    Class<?> elemClass = resolveElementClass(annotation, genericType, type);
    if (elemClass != null) {
      Map<String, FieldMapping> elementMappings = scan(elemClass);
      if (!elementMappings.isEmpty() || (annotation.elementClass() != null && annotation.elementClass() != void.class)) {
        arrayElement = new ArrayElementDescriptor(elementMappings);
      }
    }

    Set<SearchOperation> ops =
        annotation.operations().length > 0
            ? Set.of(annotation.operations())
            : defaultOperationsForType(type, arrayElement != null);

    return new FieldMapping(
        docField,
        type,
        ops,
        null,
        annotation.projectable(),
        annotation.searchable(),
        annotation.alwaysIncluded(),
        annotation.description(),
        Set.of(annotation.examples()),
        arrayElement);
  }

  private static Class<?> resolveElementClass(
      SearchableField annotation, Type genericType, Class<?> rawType) {
    if (annotation != null && annotation.elementClass() != void.class) {
      return annotation.elementClass();
    }
    if (rawType != null && rawType.isArray()) {
      return rawType.getComponentType();
    }
    if (rawType != null
        && Collection.class.isAssignableFrom(rawType)
        && genericType instanceof ParameterizedType paramType) {
      Type[] typeArgs = paramType.getActualTypeArguments();
      if (typeArgs.length > 0) {
        Type arg = typeArgs[0];
        if (arg instanceof Class<?> elemClass) {
          return elemClass;
        }
        if (arg instanceof WildcardType wildcardType) {
          Type[] upperBounds = wildcardType.getUpperBounds();
          if (upperBounds.length > 0 && upperBounds[0] instanceof Class<?> elemClass) {
            return elemClass;
          }
        }
      }
    }
    return null;
  }


  private static FieldMapping buildJoinedMapping(
      String propertyName,
      JoinedField annotation,
      Class<?> fallbackType,
      Type genericType) {
    String docField =
        annotation.documentField().isBlank()
            ? (annotation.as() + "." + propertyName)
            : annotation.documentField();
    Class<?> type = resolveType(annotation.type(), fallbackType);

    ArrayElementDescriptor arrayElement = null;
    Class<?> elemClass = resolveJoinedElementClass(annotation, genericType, type);
    if (elemClass != null) {
      Map<String, FieldMapping> elementMappings = scan(elemClass);
      if (!elementMappings.isEmpty() || (annotation.elementClass() != null && annotation.elementClass() != void.class)) {
        arrayElement = new ArrayElementDescriptor(elementMappings);
      }
    }

    if (arrayElement != null && (type == null || type == void.class)) {
      type = Collection.class;
    }

    Set<SearchOperation> ops =
        annotation.operations().length > 0
            ? Set.of(annotation.operations())
            : defaultOperationsForType(type, arrayElement != null);

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
        Set.of(annotation.examples()),
        arrayElement);
  }

  private static Class<?> resolveJoinedElementClass(
      JoinedField annotation, Type genericType, Class<?> rawType) {
    if (annotation != null && annotation.elementClass() != void.class) {
      return annotation.elementClass();
    }
    return resolveElementClass(null, genericType, rawType);
  }

  private static Class<?> resolveType(Class<?> configuredType, Class<?> fallbackType) {
    Class<?> rawType = (configuredType != null && configuredType != void.class) ? configuredType : fallbackType;
    if (rawType != null && rawType.isPrimitive()) {
      return PRIMITIVE_WRAPPERS.getOrDefault(rawType, rawType);
    }
    return rawType;
  }

  public static Set<SearchOperation> defaultOperationsForType(Class<?> type) {
    return defaultOperationsForType(type, false);
  }

  public static Set<SearchOperation> defaultOperationsForType(Class<?> type, boolean hasArrayElements) {
    if (type == null || type == void.class) {
      if (hasArrayElements) {
        return Set.of(
            SearchOperation.ELEM_MATCH,
            SearchOperation.SIZE,
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
      if (hasArrayElements) {
        return Set.of(
            SearchOperation.ELEM_MATCH,
            SearchOperation.SIZE,
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
          SearchOperation.SIZE,
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
