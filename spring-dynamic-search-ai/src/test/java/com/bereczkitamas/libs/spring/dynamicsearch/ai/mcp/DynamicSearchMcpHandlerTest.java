package com.bereczkitamas.libs.spring.dynamicsearch.ai.mcp;

import com.bereczkitamas.libs.spring.dynamicsearch.ai.DynamicSearchAiTool;
import com.bereczkitamas.libs.spring.dynamicsearch.data.FieldMapping;
import com.bereczkitamas.libs.spring.dynamicsearch.data.PagedAggregationExecutor;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchFieldRegistry;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchOperation;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SimpleSearchFieldRegistry;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicSearchMcpHandlerTest {

  @Mock
  private MongoTemplate mongoTemplate;

  private DynamicSearchMcpHandler<Item> mcpHandler;

  record Item(String id, String title, double price) {}

  @BeforeEach
  void setUp() {
    PagedAggregationExecutor executor = new PagedAggregationExecutor(mongoTemplate);
    SearchFieldRegistry registry =
        SimpleSearchFieldRegistry.of(
            Map.of(
                "title", FieldMapping.of("title", String.class, SearchOperation.EQUALS, SearchOperation.LIKE),
                "price", FieldMapping.of("price", Double.class, SearchOperation.BETWEEN, SearchOperation.GREATER_THAN)));

    DynamicSearchAiTool<Item> aiTool =
        new DynamicSearchAiTool<>(executor, registry, Item.class);

    mcpHandler = new DynamicSearchMcpHandler<>(aiTool);
  }

  @Test
  void shouldReturnMcpToolDefinitions() {
    List<Map<String, Object>> tools = mcpHandler.getToolDefinitions();

    assertEquals(2, tools.size());
    assertEquals("list_search_fields", tools.get(0).get("name"));
    assertEquals("execute_dynamic_search", tools.get(1).get("name"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldHandleListFieldsToolCall() {
    Map<String, Object> response = mcpHandler.handleToolCall("list_search_fields", Map.of());

    assertNotNull(response);
    List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
    assertNotNull(content);
    String jsonText = (String) content.getFirst().get("text");
    assertTrue(jsonText.contains("title"));
    assertTrue(jsonText.contains("price"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldHandleExecuteSearchToolCall() {
    Document doc = new Document("id", "101").append("title", "Keyboard").append("price", 79.99);
    Document facetDoc =
        new Document(PagedAggregationExecutor.FIELD_DATA, List.of(doc))
            .append(PagedAggregationExecutor.FIELD_TOTAL_COUNT, List.of(new Document("total", 1L)));

    AggregationResults<Document> results = Mockito.mock(AggregationResults.class);
    when(results.getUniqueMappedResult()).thenReturn(facetDoc);

    when(mongoTemplate.getCollectionName(Item.class)).thenReturn("item");
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("item"), eq(Document.class)))
        .thenReturn(results);

    Map<String, Object> arguments =
        Map.of(
            "searchRequest",
            Map.of(
                "criteria", List.of(Map.of("field", "title", "operation", "EQUALS", "value", "Keyboard")),
                "operator", "AND"),
            "page", 0,
            "size", 10);

    Map<String, Object> response = mcpHandler.handleToolCall("execute_dynamic_search", arguments);

    assertNotNull(response);
    assertFalse((Boolean) response.get("isError"));
    List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
    String text = (String) content.getFirst().get("text");
    assertTrue(text.contains("Keyboard"));
    assertTrue(text.contains("79.99"));
  }
}
