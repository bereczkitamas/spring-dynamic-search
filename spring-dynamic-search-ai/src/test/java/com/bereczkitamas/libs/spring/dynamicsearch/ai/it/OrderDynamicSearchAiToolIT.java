package com.bereczkitamas.libs.spring.dynamicsearch.ai.it;

import static org.junit.jupiter.api.Assertions.*;

import com.bereczkitamas.libs.spring.dynamicsearch.ai.DynamicSearchAiTool;
import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.FieldSchema;
import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchSchemaDescription;
import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchToolRequest;
import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchToolResult;
import com.bereczkitamas.libs.spring.dynamicsearch.data.*;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.Order;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.OrderDto;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderDynamicSearchAiToolIT extends AbstractAiMongoIntegrationTest {

  private DynamicSearchAiTool<Order> aiTool;
  private SearchFieldRegistry registry;

  @BeforeEach
  void setUpAiTool() {
    registry = SimpleSearchFieldRegistry.from(OrderDto.class);
    aiTool = DynamicSearchAiTool.of(executor, registry, Order.class);
  }

  @Test
  @DisplayName("AI tool executes search against embedded MongoDB and returns structured result")
  void testSuccessfulAiToolSearch() {
    SearchRequest searchRequest =
        SearchRequestBuilder.search()
            .where("status", SearchOperation.EQUALS, "COMPLETED")
            .build();

    SearchToolRequest toolRequest =
        SearchToolRequest.builder()
            .searchRequest(searchRequest)
            .page(0)
            .size(5)
            .sortField("totalAmount")
            .sortDirection("DESC")
            .build();

    SearchToolResult<Order> result = aiTool.execute(toolRequest);

    assertNotNull(result);
    assertTrue(result.isSuccess());
    assertNull(result.getError());
    assertEquals(2, result.getTotalElements());
    assertEquals(1, result.getTotalPages());
    assertEquals(2, result.getItems().size());
    // Highest totalAmount first: ORD-1004 ($2100) > ORD-1001 ($1250)
    assertEquals("ORD-1004", result.getItems().get(0).getOrderNumber());
    assertEquals("ORD-1001", result.getItems().get(1).getOrderNumber());
  }

  @Test
  @DisplayName("AI tool searches nested objects and dynamic Map attributes")
  void testAiToolNestedAndMapSearch() {
    SearchRequest searchRequest =
        SearchRequestBuilder.search()
            .where("customerCity", SearchOperation.EQUALS, "Budapest")
            .where("channel", SearchOperation.EQUALS, "web")
            .build();

    SearchToolRequest toolRequest =
        SearchToolRequest.builder()
            .searchRequest(searchRequest)
            .page(0)
            .size(10)
            .build();

    SearchToolResult<Order> result = aiTool.execute(toolRequest);

    assertTrue(result.isSuccess());
    assertEquals(1, result.getTotalElements());
    assertEquals("ORD-1001", result.getItems().getFirst().getOrderNumber());
  }

  @Test
  @DisplayName("AI tool searches subdocument array line items with ELEM_MATCH")
  void testAiToolArrayElemMatchSearch() {
    SearchRequest searchRequest =
        SearchRequestBuilder.search()
            .elemMatch(
                "assets",
                "AND",
                elem ->
                    elem.where("category", SearchOperation.EQUALS, "LAPTOP")
                        .where("price", SearchOperation.GREATER_THAN, 1500.0))
            .build();

    SearchToolRequest toolRequest =
        SearchToolRequest.builder()
            .searchRequest(searchRequest)
            .page(0)
            .size(10)
            .build();

    SearchToolResult<Order> result = aiTool.execute(toolRequest);

    assertTrue(result.isSuccess());
    assertEquals(1, result.getTotalElements());
    assertEquals("ORD-1004", result.getItems().getFirst().getOrderNumber());
  }

  @Test
  @DisplayName("AI tool provides actionable error feedback and Levenshtein suggestion on hallucinated field")
  void testAiToolFuzzyErrorFeedbackOnHallucinatedField() {
    // LLM mistakenly asks for 'totlAmount' instead of 'totalAmount'
    SearchRequest invalidRequest =
        SearchRequest.builder()
            .operator("AND")
            .criteria(
                List.of(
                    new SearchCriteria("totlAmount", SearchOperation.GREATER_THAN, 1000.0)))
            .build();

    SearchToolRequest toolRequest =
        SearchToolRequest.builder().searchRequest(invalidRequest).build();

    SearchToolResult<Order> result = aiTool.execute(toolRequest);

    assertNotNull(result);
    assertFalse(result.isSuccess());
    assertTrue(result.getItems().isEmpty());
    assertNotNull(result.getError());
    assertEquals("totlAmount", result.getError().getInvalidField());
    assertEquals("totalAmount", result.getError().getSuggestedField());
    assertTrue(result.getError().getRepairGuidance().contains("Did you mean 'totalAmount'?"));
  }

  @Test
  @DisplayName("AI tool generates valid schema, system prompt, and JSON schema for Order domain")
  void testAiToolSchemaAndPromptGeneration() {
    SearchSchemaDescription schema = aiTool.getSchema();
    assertNotNull(schema);
    assertEquals("Order", schema.getEntityName());

    Set<String> fieldNames =
        schema.getFields().stream().map(FieldSchema::getName).collect(Collectors.toSet());

    assertTrue(fieldNames.contains("orderNumber"));
    assertTrue(fieldNames.contains("customerName"));
    assertTrue(fieldNames.contains("assets"));
    assertTrue(fieldNames.contains("channel"));

    // Verify system prompt
    String prompt = aiTool.getSystemPrompt();
    assertNotNull(prompt);
    assertTrue(prompt.contains("Order"));
    assertTrue(prompt.contains("orderNumber"));
    assertTrue(prompt.contains("customerName"));

    // Verify JSON Schema
    Map<String, Object> jsonSchema = aiTool.getJsonSchema();
    assertNotNull(jsonSchema);
    assertEquals("object", jsonSchema.get("type"));
  }
}
