package com.bereczkitamas.libs.spring.dynamicsearch.ai.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Encapsulates the complete searchable and projectable schema metadata for an entity or domain.
 */
@Value
@Builder
public class SearchSchemaDescription {
  String entityName;
  List<FieldSchema> fields;
  List<String> supportedOperations;
}
