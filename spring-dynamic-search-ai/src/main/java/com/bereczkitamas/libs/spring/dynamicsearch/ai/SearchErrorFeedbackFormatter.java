package com.bereczkitamas.libs.spring.dynamicsearch.ai;

import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchErrorFeedback;
import com.bereczkitamas.libs.spring.dynamicsearch.data.FieldMapping;
import com.bereczkitamas.libs.spring.dynamicsearch.data.InvalidProjectionException;
import com.bereczkitamas.libs.spring.dynamicsearch.data.InvalidSearchFieldException;
import com.bereczkitamas.libs.spring.dynamicsearch.data.InvalidSearchOperationException;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchFieldRegistry;
import java.util.Comparator;
import java.util.List;

/**
 * Transforms validation exceptions into structured LLM diagnostic feedback with auto-repair suggestions.
 */
public class SearchErrorFeedbackFormatter {

  /**
   * Formats an exception thrown during search validation or execution into actionable LLM feedback.
   */
  public SearchErrorFeedback format(Exception exception, SearchFieldRegistry registry) {
    if (exception == null) {
      return SearchErrorFeedback.builder().valid(true).build();
    }

    List<String> allFields =
        registry != null
            ? registry.getMappings().keySet().stream().sorted().toList()
            : List.of();

    if (exception instanceof InvalidSearchFieldException isfe) {
      String invalidField = isfe.getFieldName();
      String closestMatch = findClosestField(invalidField, allFields);

      StringBuilder guidance = new StringBuilder();
      guidance.append("Field '").append(invalidField).append("' does not exist in the search registry.");
      if (closestMatch != null) {
        guidance.append(" Did you mean '").append(closestMatch).append("'?");
      }
      guidance.append(" Allowed fields are: ").append(String.join(", ", allFields));

      return SearchErrorFeedback.builder()
          .valid(false)
          .errorMessage(isfe.getMessage())
          .invalidField(invalidField)
          .suggestedField(closestMatch)
          .allowedFields(allFields)
          .repairGuidance(guidance.toString())
          .build();
    }

    if (exception instanceof InvalidSearchOperationException isoe) {
      String field = isoe.getFieldName();
      String op = isoe.getOperationName() != null ? isoe.getOperationName() : "UNKNOWN";

      List<String> allowedOps = List.of();
      if (registry != null && registry.getMappings().containsKey(field)) {
        FieldMapping mapping = registry.getMappings().get(field);
        allowedOps = mapping.getAllowedOperations().stream().map(Enum::name).sorted().toList();
      }

      String guidance =
          String.format(
              "Operation '%s' is not permitted on field '%s'. Allowed operations for '%s' are: [%s].",
              op, field, field, String.join(", ", allowedOps));

      return SearchErrorFeedback.builder()
          .valid(false)
          .errorMessage(isoe.getMessage())
          .invalidField(field)
          .invalidOperation(op)
          .allowedFields(allFields)
          .allowedOperationsForField(allowedOps)
          .repairGuidance(guidance)
          .build();
    }

    if (exception instanceof InvalidProjectionException ipe) {
      String invalidField = ipe.getFieldName();
      String closestMatch = findClosestField(invalidField, allFields);

      return SearchErrorFeedback.builder()
          .valid(false)
          .errorMessage(ipe.getMessage())
          .invalidField(invalidField)
          .suggestedField(closestMatch)
          .allowedFields(allFields)
          .repairGuidance("Field '" + invalidField + "' is not projectable. Choose from: " + String.join(", ", allFields))
          .build();
    }

    return SearchErrorFeedback.builder()
        .valid(false)
        .errorMessage(exception.getMessage())
        .allowedFields(allFields)
        .repairGuidance("Query failed: " + exception.getMessage() + ". Please verify query parameters against the search schema.")
        .build();
  }

  private String findClosestField(String input, List<String> availableFields) {
    if (input == null || availableFields.isEmpty()) {
      return null;
    }

    String lowerInput = input.toLowerCase().replaceAll("[_\\-]", "");

    // 1. Direct case-insensitive / normalized match
    for (String field : availableFields) {
      String lowerField = field.toLowerCase().replaceAll("[_\\-]", "");
      if (lowerField.equals(lowerInput) || lowerField.contains(lowerInput) || lowerInput.contains(lowerField)) {
        return field;
      }
    }

    // 2. Levenshtein distance match
    return availableFields.stream()
        .min(Comparator.comparingInt(f -> levenshteinDistance(input.toLowerCase(), f.toLowerCase())))
        .filter(f -> levenshteinDistance(input.toLowerCase(), f.toLowerCase()) <= 3)
        .orElse(null);
  }

  private int levenshteinDistance(String a, String b) {
    int[][] dp = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
    for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
        dp[i][j] =
            Math.min(
                Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                dp[i - 1][j - 1] + cost);
      }
    }
    return dp[a.length()][b.length()];
  }
}
