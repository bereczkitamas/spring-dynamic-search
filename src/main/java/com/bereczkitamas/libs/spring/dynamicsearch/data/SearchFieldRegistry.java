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
}
