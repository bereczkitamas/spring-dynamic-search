package com.bereczkitamas.libs.spring.dynamicsearch.ai.model;

import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

/**
 * Describes a single searchable or projectable field in a domain schema for AI Agents.
 */
@Value
@Builder
public class FieldSchema {
  String name;
  String type;
  String description;
  Set<String> allowedOperations;
  Set<String> examples;
  boolean joined;
  boolean projectable;
  boolean searchable;
  boolean arrayField;
  List<FieldSchema> elementFields;
}

