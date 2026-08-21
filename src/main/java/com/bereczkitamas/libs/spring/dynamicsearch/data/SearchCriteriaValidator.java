package com.bereczkitamas.libs.spring.dynamicsearch.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class SearchCriteriaValidator {

  private final ObjectMapper objectMapper;

  public SearchCriteriaValidator() {
    this(new ObjectMapper().findAndRegisterModules());
  }

  public SearchCriteriaValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
  }

  public SearchCriteria validateAndTransform(
      SearchCriteria dtoCriteria, SearchFieldRegistry registry) {
    FieldMapping mapping = registry.resolve(dtoCriteria.getField());

    if (!mapping.getAllowedOperations().contains(dtoCriteria.getOperation())) {
      throw new InvalidSearchOperationException(
          "Operation %s not allowed for field %s"
              .formatted(dtoCriteria.getOperation(), dtoCriteria.getField()),
          dtoCriteria.getOperation(),
          dtoCriteria.getField());
    }

    Object converted = convertValue(dtoCriteria.getValue(), mapping.getType(), dtoCriteria.getOperation());

    return new SearchCriteria(mapping.getDocumentField(), dtoCriteria.getOperation(), converted);
  }

  private Object convertValue(Object value, Class<?> targetType, SearchOperation operation) {
    if (value == null) {
      return null;
    }

    if (operation == SearchOperation.BETWEEN || operation == SearchOperation.NOT_BETWEEN) {
      List<?> rawList = toRawList(value);
      if (rawList.size() < 2) {
        throw new IllegalArgumentException(
            "Operation %s requires a collection or array with at least 2 elements [min, max]"
                .formatted(operation));
      }
      return List.of(
          convertSingleValue(rawList.get(0), targetType),
          convertSingleValue(rawList.get(1), targetType));
    }

    if (operation == SearchOperation.IN
        || operation == SearchOperation.NOT_IN
        || operation == SearchOperation.CONTAINS_ALL) {
      List<?> rawList = toRawList(value);
      return rawList.stream()
          .map(v -> convertSingleValue(v, targetType))
          .collect(Collectors.toList());
    }

    if (value instanceof Collection<?>) {
      return ((Collection<?>) value)
          .stream().map(v -> convertSingleValue(v, targetType)).collect(Collectors.toList());
    }

    return convertSingleValue(value, targetType);
  }

  private List<?> toRawList(Object value) {
    if (value instanceof List<?> list) {
      return list;
    }
    if (value instanceof Collection<?> coll) {
      return List.copyOf(coll);
    }
    if (value instanceof Object[] arr) {
      return Arrays.asList(arr);
    }
    return List.of(value);
  }

  private Object convertSingleValue(Object value, Class<?> targetType) {
    if (value == null) {
      return null;
    }
    if (targetType.isInstance(value)) {
      return value;
    }
    return objectMapper.convertValue(value, targetType);
  }
}
