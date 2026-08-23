package com.bereczkitamas.libs.spring.dynamicsearch.ai.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Structured diagnostic error feedback designed specifically for LLMs to auto-repair invalid search queries.
 */
@Value
@Builder
public class SearchErrorFeedback {
  boolean valid;
  String errorMessage;
  String invalidField;
  String suggestedField;
  String invalidOperation;
  List<String> allowedFields;
  List<String> allowedOperationsForField;
  String repairGuidance;
}
