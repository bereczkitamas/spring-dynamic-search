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
            .map(entry -> toFieldSchema(entry.getKey(), entry.getValue()))
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

  private FieldSchema toFieldSchema(String name, FieldMapping mapping) {
    List<FieldSchema> elemFields = null;
    if (mapping.isArrayField() && mapping.getArrayElement() != null) {
      elemFields =
          mapping.getArrayElement().getElementFields().entrySet().stream()
              .map(e -> toFieldSchema(e.getKey(), e.getValue()))
              .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
              .toList();
    }

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
        .arrayField(mapping.isArrayField())
        .elementFields(elemFields)
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
      appendFieldPrompt(sb, field, "  ");
    }

    sb.append("\n#### SearchRequest JSON Structure:\n");
    sb.append("```json\n");
    sb.append("{\n");
    sb.append("  \"criteria\": [\n");
    sb.append("    { \"field\": \"<fieldName>\", \"operation\": \"<OPERATION>\", \"value\": <value> },\n");
    sb.append("    {\n");
    sb.append("      \"field\": \"<arrayField>\",\n");
    sb.append("      \"operation\": \"ELEM_MATCH\",\n");
    sb.append("      \"elementOperator\": \"AND\" | \"OR\",\n");
    sb.append("      \"elementCriteria\": [\n");
    sb.append("        { \"field\": \"<nestedField>\", \"operation\": \"<OPERATION>\", \"value\": <value> }\n");
    sb.append("      ]\n");
    sb.append("    }\n");
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
    sb.append("- `SIZE`: integer array length\n");
    sb.append("- `ELEM_MATCH`: used on array fields; requires `elementCriteria` array and optional `elementOperator` (\"AND\" or \"OR\")\n");

    return sb.toString();
  }

  private void appendFieldPrompt(StringBuilder sb, FieldSchema field, String indent) {
    sb.append("- `").append(field.getName()).append("` (").append(field.getType()).append(")");
    if (field.getDescription() != null && !field.getDescription().isBlank()) {
      sb.append(": ").append(field.getDescription());
    }
    if (field.getExamples() != null && !field.getExamples().isEmpty()) {
      sb.append(" | Examples: ").append(String.join(", ", field.getExamples()));
    }
    if (field.getAllowedOperations() != null && !field.getAllowedOperations().isEmpty()) {
      sb.append("\n").append(indent).append("- Allowed Operations: ").append(String.join(", ", field.getAllowedOperations()));
    }
    if (field.isArrayField() && field.getElementFields() != null && !field.getElementFields().isEmpty()) {
      sb.append("\n").append(indent).append("- Element Fields (for ELEM_MATCH):\n");
      for (FieldSchema elemField : field.getElementFields()) {
        sb.append(indent).append("  - `").append(elemField.getName()).append("` (").append(elemField.getType()).append(")");
        if (elemField.getDescription() != null && !elemField.getDescription().isBlank()) {
          sb.append(": ").append(elemField.getDescription());
        }
        if (elemField.getAllowedOperations() != null && !elemField.getAllowedOperations().isEmpty()) {
          sb.append(" [").append(String.join(", ", elemField.getAllowedOperations())).append("]");
        }
        sb.append("\n");
      }
    } else {
      sb.append("\n");
    }
  }

  /**
   * Generates a standard JSON Schema (OpenAI / JSON Schema Draft 7) for function calling tools.
   */
  public Map<String, Object> generateJsonSchema(SearchFieldRegistry registry, String entityName) {
    SearchSchemaDescription schema = describe(registry, entityName);

    List<String> allowedFieldNames = schema.getFields().stream().map(FieldSchema::getName).toList();
    List<String> allowedOps = schema.getSupportedOperations();

    Map<String, Object> innerCriterionSchema = new LinkedHashMap<>();
    innerCriterionSchema.put("type", "object");
    innerCriterionSchema.put("required", List.of("field", "operation"));

    Map<String, Object> innerCritProps = new LinkedHashMap<>();
    Map<String, Object> innerFieldProp = new LinkedHashMap<>();
    innerFieldProp.put("type", "string");
    innerFieldProp.put("description", "Nested field name on the array element");
    innerCritProps.put("field", innerFieldProp);

    Map<String, Object> innerOpProp = new LinkedHashMap<>();
    innerOpProp.put("type", "string");
    innerOpProp.put("enum", allowedOps);
    innerOpProp.put("description", "Filter operator on nested element");
    innerCritProps.put("operation", innerOpProp);

    Map<String, Object> innerValProp = new LinkedHashMap<>();
    innerValProp.put("description", "Filter value on nested element");
    innerCritProps.put("value", innerValProp);
    innerCriterionSchema.put("properties", innerCritProps);

    Map<String, Object> criterionSchema = new LinkedHashMap<>();
    criterionSchema.put("type", "object");
    criterionSchema.put("required", List.of("field", "operation"));

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
    valProp.put("description", "Filter value (scalar, array for IN/BETWEEN/CONTAINS_ALL, integer for SIZE, or true for EXISTS/EMPTY)");
    critProps.put("value", valProp);

    Map<String, Object> elemCritProp = new LinkedHashMap<>();
    elemCritProp.put("type", "array");
    elemCritProp.put("items", innerCriterionSchema);
    elemCritProp.put("description", "Nested criteria applied to array elements (used with ELEM_MATCH)");
    critProps.put("elementCriteria", elemCritProp);

    Map<String, Object> elemOpProp = new LinkedHashMap<>();
    elemOpProp.put("type", "string");
    elemOpProp.put("enum", List.of("AND", "OR"));
    elemOpProp.put("default", "AND");
    elemOpProp.put("description", "Logical operator between elementCriteria (AND or OR)");
    critProps.put("elementOperator", elemOpProp);

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
