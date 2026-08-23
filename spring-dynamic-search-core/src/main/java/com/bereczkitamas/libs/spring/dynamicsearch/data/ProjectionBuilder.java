package com.bereczkitamas.libs.spring.dynamicsearch.data;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ProjectionBuilder {
  /**
   * Validates and builds a MongoDB projection field list. Returns document field names to include.
   */
  public ProjectionResult build(ProjectionRequest request, SearchFieldRegistry registry) {
    if (request == null || (request.getInclude().isEmpty() && request.getExclude().isEmpty())) {
      return ProjectionResult.none();
    }

    if (!request.getInclude().isEmpty() && !request.getExclude().isEmpty()) {
      throw new InvalidProjectionException(
          "Cannot use both 'include' and 'exclude' simultaneously", null);
    }

    Map<String, FieldMapping> mappings = registry.getMappings();

    // Collect all "always included" fields
    Set<String> alwaysIncluded =
        mappings.values().stream()
            .filter(FieldMapping::isAlwaysIncluded)
            .map(FieldMapping::getDocumentField)
            .collect(Collectors.toSet());

    if (!request.getInclude().isEmpty()) {
      return buildInclude(request.getInclude(), mappings, alwaysIncluded);
    } else {
      return buildExclude(request.getExclude(), mappings);
    }
  }

  private ProjectionResult buildInclude(
      Set<String> includeDtoFields,
      Map<String, FieldMapping> mappings,
      Set<String> alwaysIncluded) {
    Set<String> docFields = new HashSet<>(alwaysIncluded);

    for (String dtoField : includeDtoFields) {
      FieldMapping mapping = mappings.get(dtoField);
      if (mapping == null) {
        throw new InvalidProjectionException("Field '" + dtoField + "' does not exist", dtoField);
      }
      if (!mapping.isProjectable()) {
        throw new InvalidProjectionException(
            "Field '" + dtoField + "' is not projectable", dtoField);
      }
      docFields.add(mapping.getDocumentField());
    }

    return ProjectionResult.include(docFields);
  }

  private ProjectionResult buildExclude(
      Set<String> excludeDtoFields,
      Map<String, FieldMapping> mappings) {
    Set<String> docFields = new HashSet<>();

    for (String dtoField : excludeDtoFields) {
      FieldMapping mapping = mappings.get(dtoField);
      if (mapping == null) {
        throw new InvalidProjectionException("Field '" + dtoField + "' does not exist", dtoField);
      }
      if (mapping.isAlwaysIncluded()) {
        throw new InvalidProjectionException(
            "Field '" + dtoField + "' cannot be excluded", dtoField);
      }
      docFields.add(mapping.getDocumentField());
    }

    return ProjectionResult.exclude(docFields);
  }
}
