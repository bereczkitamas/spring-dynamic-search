package com.bereczkitamas.libs.spring.dynamicsearch.ai;

import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.FieldSchema;
import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchSchemaDescription;
import com.bereczkitamas.libs.spring.dynamicsearch.annotation.JoinedField;
import com.bereczkitamas.libs.spring.dynamicsearch.annotation.SearchableField;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchOperation;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchSchemaServiceTest {

  private SearchSchemaService schemaService;

  static class CustomerDto {
    @SearchableField(
        description = "Customer username / login name",
        examples = {"john_doe", "alice99"},
        operations = {SearchOperation.EQUALS, SearchOperation.LIKE, SearchOperation.IN})
    private String username;

    @SearchableField(description = "Customer age in years", examples = {"25", "40"})
    private int age;

    @SearchableField(description = "Customer registration date")
    private LocalDate registeredAt;

    @JoinedField(
        collection = "countries",
        localField = "country_id",
        as = "country",
        documentField = "country.name",
        description = "Country of residence",
        examples = {"Germany", "Hungary", "United States"},
        operations = {SearchOperation.EQUALS, SearchOperation.IN})
    private String countryName;
  }

  @BeforeEach
  void setUp() {
    schemaService = new SearchSchemaService();
  }

  @Test
  void shouldDescribeAnnotatedClass() {
    SearchSchemaDescription schema = schemaService.describe(CustomerDto.class);

    assertEquals("CustomerDto", schema.getEntityName());
    assertEquals(4, schema.getFields().size());

    FieldSchema username =
        schema.getFields().stream().filter(f -> f.getName().equals("username")).findFirst().orElseThrow();
    assertEquals("String", username.getType());
    assertEquals("Customer username / login name", username.getDescription());
    assertTrue(username.getExamples().contains("john_doe"));
    assertTrue(username.getAllowedOperations().contains("EQUALS"));
    assertTrue(username.getAllowedOperations().contains("LIKE"));
    assertFalse(username.isJoined());

    FieldSchema country =
        schema.getFields().stream().filter(f -> f.getName().equals("countryName")).findFirst().orElseThrow();
    assertTrue(country.isJoined());
    assertEquals("Country of residence", country.getDescription());
    assertTrue(country.getExamples().contains("Germany"));
  }

  @Test
  void shouldGenerateSystemPrompt() {
    String prompt = schemaService.generateSystemPrompt(
        com.bereczkitamas.libs.spring.dynamicsearch.data.SimpleSearchFieldRegistry.from(CustomerDto.class),
        "Customer");

    assertNotNull(prompt);
    assertTrue(prompt.contains("### Search Schema: Customer"));
    assertTrue(prompt.contains("`username` (String)"));
    assertTrue(prompt.contains("`countryName` (String)"));
    assertTrue(prompt.contains("SearchRequest JSON Structure"));
    assertTrue(prompt.contains("Operation Guidelines"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldGenerateJsonSchema() {
    Map<String, Object> jsonSchema =
        schemaService.generateJsonSchema(
            com.bereczkitamas.libs.spring.dynamicsearch.data.SimpleSearchFieldRegistry.from(CustomerDto.class),
            "Customer");

    assertNotNull(jsonSchema);
    assertEquals("object", jsonSchema.get("type"));
    assertEquals("CustomerSearchRequest", jsonSchema.get("title"));

    Map<String, Object> props = (Map<String, Object>) jsonSchema.get("properties");
    assertNotNull(props.get("criteria"));
    assertNotNull(props.get("operator"));
  }
}
