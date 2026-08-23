package com.bereczkitamas.libs.spring.dynamicsearch.ai;

import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchErrorFeedback;
import com.bereczkitamas.libs.spring.dynamicsearch.data.FieldMapping;
import com.bereczkitamas.libs.spring.dynamicsearch.data.InvalidSearchFieldException;
import com.bereczkitamas.libs.spring.dynamicsearch.data.InvalidSearchOperationException;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchFieldRegistry;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchOperation;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SimpleSearchFieldRegistry;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchErrorFeedbackFormatterTest {

  private SearchErrorFeedbackFormatter formatter;
  private SearchFieldRegistry registry;

  @BeforeEach
  void setUp() {
    formatter = new SearchErrorFeedbackFormatter();
    registry =
        SimpleSearchFieldRegistry.of(
            Map.of(
                "username", FieldMapping.of("username", String.class, SearchOperation.EQUALS, SearchOperation.LIKE),
                "countryName", FieldMapping.of("country.name", String.class, SearchOperation.EQUALS),
                "age", FieldMapping.of("age", Integer.class, SearchOperation.EQUALS, SearchOperation.BETWEEN)));
  }

  @Test
  void shouldProvideSuggestion_whenInvalidFieldGiven() {
    InvalidSearchFieldException ex = new InvalidSearchFieldException("Field 'country_name' is not searchable", "country_name");
    SearchErrorFeedback feedback = formatter.format(ex, registry);

    assertFalse(feedback.isValid());
    assertEquals("country_name", feedback.getInvalidField());
    assertEquals("countryName", feedback.getSuggestedField());
    assertTrue(feedback.getRepairGuidance().contains("Did you mean 'countryName'?"));
    assertTrue(feedback.getAllowedFields().contains("countryName"));
  }

  @Test
  void shouldListAllowedOperations_whenInvalidOperationGiven() {
    InvalidSearchOperationException ex =
        new InvalidSearchOperationException("Operation LIKE not allowed on age", SearchOperation.LIKE, "age");
    SearchErrorFeedback feedback = formatter.format(ex, registry);

    assertFalse(feedback.isValid());
    assertEquals("age", feedback.getInvalidField());
    assertEquals("LIKE", feedback.getInvalidOperation());
    assertTrue(feedback.getAllowedOperationsForField().contains("BETWEEN"));
    assertTrue(feedback.getAllowedOperationsForField().contains("EQUALS"));
    assertTrue(feedback.getRepairGuidance().contains("Allowed operations for 'age' are:"));
  }
}
