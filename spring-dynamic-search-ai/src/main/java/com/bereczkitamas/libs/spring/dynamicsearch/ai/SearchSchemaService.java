package com.bereczkitamas.libs.spring.dynamicsearch.ai;

import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.FieldSchema;
import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchSchemaDescription;
import com.bereczkitamas.libs.spring.dynamicsearch.annotation.SearchAnnotationScanner;
import com.bereczkitamas.libs.spring.dynamicsearch.data.FieldMapping;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchFieldRegistry;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchOperation;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SimpleSearchFieldRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Introspects {@link SearchFieldRegistry} and annotated classes to generate AI schemas,
 * OpenAPI/JSON Schemas, and token-optimized system prompts for LLM tool calling.
 */
public class SearchSchemaService {

  /**
   * Describes the search schema for a given registry.
   */
  public SearchSchemaDescription describe(SearchFieldRegistry registry, String entityName) {
    if (registry == null) {
      return SearchSchemaDescription.builder()
          .entityName(entityName)
          .fields(List.of())
          .supportedOperations(List.of())
          .build();
    }

    List<FieldSchema> fields =
        registry.getMappings().entrySet().stream()
            .map(
                entry -> {
                  String name = entry.getKey();
                  FieldMapping mapping = entry.getValue();
                  return FieldSchema.builder()
                      .name(name)
                      .type(mapping.getType() != null ? mapping.getType().getSimpleName() : "Object")
                      .description(mapping.getDescription())
                      .allowedOperations(
                          mapping.getAllowedOperations().stream()
                              .map(Enum::name)
                              .collect(Collectors.toSet()))
                      .examples(mapping.getExamples())
                      .joined(mapping.isJoined())
                      .projectable(mapping.isProjectable())
                      .searchable(mapping.isSearchable())
                      .build();
                })
            .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
            .toList();

    List<String> ops =
        Arrays.stream(SearchOperation.values())
            .map(Enum::name)
            .sorted()
            .toList();

    return SearchSchemaDescription.builder()
        .entityName(entityName)
        .fields(fields)
        .supportedOperations(ops)
        .build();
  }

  /**
   * Describes the search schema for an annotated entity or DTO class.
   */
  public SearchSchemaDescription describe(Class<?> entityClass) {
    if (entityClass == null) {
      return SearchSchemaDescription.builder()
          .entityName("Unknown")
          .fields(List.of())
          .supportedOperations(List.of())
          .build();
    }
    SearchFieldRegistry registry = SimpleSearchFieldRegistry.from(entityClass);
    return describe(registry, entityClass.getSimpleName());
  }

  /**
   * Generates a token-efficient system prompt snippet explaining the search schema and instructions for LLMs.
   */
  public String generateSystemPrompt(SearchFieldRegistry registry, String entityName) {
    SearchSchemaDescription schema = describe(registry, entityName);

    StringBuilder sb = new StringBuilder();
    sb.append("### Search Schema: ").append(schema.getEntityName()).append("\n");
    sb.append("You can query ").append(schema.getEntityName()).append(" records by generating a structured SearchRequest.\n\n");
    sb.append("#### Available Search Fields:\n");

    for (FieldSchema field : schema.getFields()) {
      sb.append("- `").append(field.getName()).append("` (").append(field.getType()).append(")");
      if (!field.getDescription().isBlank()) {
        sb.append(": ").append(field.getDescription());
      }
      if (!field.getExamples().isEmpty()) {
        sb.append(" | Examples: ").append(String.join(", ", field.getExamples()));
      }
      sb.append("\n  - Allowed Operations: ").append(String.join(", ", field.getAllowedOperations())).append("\n");
    }

    sb.append("\n#### SearchRequest JSON Structure:\n");
    sb.append("```json\n");
    sb.append("{\n");
    sb.append("  \"criteria\": [\n");
    sb.append("    { \"field\": \"<fieldName>\", \"operation\": \"<OPERATION>\", \"value\": <value> }\n");
    sb.append("  ],\n");
    sb.append("  \"operator\": \"AND\" | \"OR\",\n");
    sb.append("  \"groups\": [\n");
    sb.append("    { \"criteria\": [...], \"operator\": \"AND\" | \"OR\" }\n");
    sb.append("  ]\n");
    sb.append("}\n");
    sb.append("```\n\n");
    sb.append("#### Operation Guidelines:\n");
    sb.append("- `EQUALS`, `NOT_EQUALS`, `GREATER_THAN`, `LESS_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL`: scalar value\n");
    sb.append("- `LIKE`, `STARTS_WITH`, `ENDS_WITH`, `REGEX`: string pattern\n");
    sb.append("- `IN`, `NOT_IN`: array of values, e.g. `\"value\": [\"A\", \"B\"]`\n");
    sb.append("- `BETWEEN`, `NOT_BETWEEN`: 2-element array `[min, max]`, e.g. `\"value\": [10, 50]` or `[\"2025-01-01\", \"2025-12-31\"]`\n");
    sb.append("- `EXISTS`, `DOES_NOT_EXIST`, `IS_EMPTY`, `IS_NOT_EMPTY`: boolean value `true`\n");
    sb.append("- `CONTAINS_ALL`: array of values required in collection\n");

    return sb.toString();
  }

  /**
   * Generates a standard JSON Schema (OpenAI / JSON Schema Draft 7) for function calling tools.
   */
  public Map<String, Object> generateJsonSchema(SearchFieldRegistry registry, String entityName) {
    SearchSchemaDescription schema = describe(registry, entityName);

    List<String> allowedFieldNames = schema.getFields().stream().map(FieldSchema::getName).toList();
    List<String> allowedOps = schema.getSupportedOperations();

    Map<String, Object> criterionSchema = new LinkedHashMap<>();
    criterionSchema.put("type", "object");
    criterionSchema.put("required", List.of("field", "operation", "value"));

    Map<String, Object> critProps = new LinkedHashMap<>();

    Map<String, Object> fieldProp = new LinkedHashMap<>();
    fieldProp.put("type", "string");
    fieldProp.put("enum", allowedFieldNames);
    fieldProp.put("description", "Field name to filter on");
    critProps.put("field", fieldProp);

    Map<String, Object> opProp = new LinkedHashMap<>();
    opProp.put("type", "string");
    opProp.put("enum", allowedOps);
    opProp.put("description", "Filter operator");
    critProps.put("operation", opProp);

    Map<String, Object> valProp = new LinkedHashMap<>();
    valProp.put("description", "Filter value (scalar, array for IN/BETWEEN/CONTAINS_ALL, or true for EXISTS/EMPTY)");
    critProps.put("value", valProp);

    criterionSchema.put("properties", critProps);

    Map<String, Object> rootSchema = new LinkedHashMap<>();
    rootSchema.put("type", "object");
    rootSchema.put("title", entityName + "SearchRequest");
    rootSchema.put("description", "Dynamic search request for " + entityName);

    Map<String, Object> rootProps = new LinkedHashMap<>();

    Map<String, Object> criteriaProp = new LinkedHashMap<>();
    criteriaProp.put("type", "array");
    criteriaProp.put("items", criterionSchema);
    criteriaProp.put("description", "List of search criteria");
    rootProps.put("criteria", criteriaProp);

    Map<String, Object> operatorProp = new LinkedHashMap<>();
    operatorProp.put("type", "string");
    operatorProp.put("enum", List.of("AND", "OR"));
    operatorProp.put("default", "AND");
    rootProps.put("operator", operatorProp);

    rootSchema.put("properties", rootProps);
    return rootSchema;
  }
}
