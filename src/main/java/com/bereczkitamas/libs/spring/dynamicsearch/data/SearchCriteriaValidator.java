package com.bereczkitamas.libs.spring.dynamicsearch.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
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

    Object converted = convertValue(dtoCriteria.getValue(), mapping.getType());

    return new SearchCriteria(mapping.getDocumentField(), dtoCriteria.getOperation(), converted);
  }

  private Object convertValue(Object value, Class<?> targetType) {
    if (value == null) {
      return null;
    }
    if (targetType.isInstance(value)) {
      return value;
    }

    if (value instanceof Collection) {
      return ((Collection<?>) value)
          .stream().map(v -> objectMapper.convertValue(v, targetType)).collect(Collectors.toList());
    }
    return objectMapper.convertValue(value, targetType);
  }
}
