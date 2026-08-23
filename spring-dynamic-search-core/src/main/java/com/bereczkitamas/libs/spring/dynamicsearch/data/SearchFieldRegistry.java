package com.bereczkitamas.libs.spring.dynamicsearch.data;

import java.util.Map;

public interface SearchFieldRegistry {
  Map<String, FieldMapping> getMappings();

  default FieldMapping resolve(String dtoField) {
    FieldMapping mapping = getMappings().get(dtoField);
    if (mapping == null) {
      throw new InvalidSearchFieldException("Field '" + dtoField + "' is not searchable", dtoField);
    }
    return mapping;
  }

  /**
   * Creates a {@link SearchFieldRegistry} from a map of field mappings.
   */
  static SearchFieldRegistry of(Map<String, FieldMapping> mappings) {
    return SimpleSearchFieldRegistry.of(mappings);
  }

  /**
   * Creates a {@link SearchFieldRegistry} by scanning annotations on the given class.
   */
  static SearchFieldRegistry from(Class<?> clazz) {
    return SimpleSearchFieldRegistry.from(clazz);
  }
}
