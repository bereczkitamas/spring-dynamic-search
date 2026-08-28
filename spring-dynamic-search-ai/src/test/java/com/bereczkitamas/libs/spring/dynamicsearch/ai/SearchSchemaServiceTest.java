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

  static class OrderItemDto {
    @SearchableField(description = "Item product SKU", examples = {"SKU-100", "SKU-200"})
    private String sku;

    @SearchableField(description = "Item price")
    private double price;
  }

  static class OrderDto {
    @SearchableField(description = "Order identifier")
    private String orderId;

    @SearchableField(description = "List of items in order")
    private java.util.List<OrderItemDto> items;
  }

  @Test
  void shouldDescribeArrayFieldWithElementFields() {
    SearchSchemaDescription schema = schemaService.describe(OrderDto.class);

    FieldSchema itemsField =
        schema.getFields().stream().filter(f -> f.getName().equals("items")).findFirst().orElseThrow();
    assertTrue(itemsField.isArrayField());
    assertNotNull(itemsField.getElementFields());
    assertEquals(2, itemsField.getElementFields().size());

    FieldSchema skuField =
        itemsField.getElementFields().stream().filter(f -> f.getName().equals("sku")).findFirst().orElseThrow();
    assertEquals("sku", skuField.getName());
    assertEquals("Item product SKU", skuField.getDescription());
  }

  @Test
  void shouldIncludeElemMatchAndElementFieldsInSystemPrompt() {
    String prompt =
        schemaService.generateSystemPrompt(
            com.bereczkitamas.libs.spring.dynamicsearch.data.SimpleSearchFieldRegistry.from(OrderDto.class),
            "Order");

    assertNotNull(prompt);
    assertTrue(prompt.contains("Element Fields (for ELEM_MATCH):"));
    assertTrue(prompt.contains("`sku` (String)"));
    assertTrue(prompt.contains("ELEM_MATCH"));
    assertTrue(prompt.contains("SIZE"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldGenerateJsonSchemaWithElementCriteria() {
    Map<String, Object> jsonSchema =
        schemaService.generateJsonSchema(
            com.bereczkitamas.libs.spring.dynamicsearch.data.SimpleSearchFieldRegistry.from(OrderDto.class),
            "Order");

    assertNotNull(jsonSchema);
    Map<String, Object> props = (Map<String, Object>) jsonSchema.get("properties");
    Map<String, Object> criteriaProp = (Map<String, Object>) props.get("criteria");
    Map<String, Object> itemsSchema = (Map<String, Object>) criteriaProp.get("items");
    Map<String, Object> criterionProps = (Map<String, Object>) itemsSchema.get("properties");

    assertTrue(criterionProps.containsKey("elementCriteria"));
    assertTrue(criterionProps.containsKey("elementOperator"));
  }
}

