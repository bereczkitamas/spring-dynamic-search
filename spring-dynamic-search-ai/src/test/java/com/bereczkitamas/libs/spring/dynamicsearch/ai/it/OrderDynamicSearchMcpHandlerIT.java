package com.bereczkitamas.libs.spring.dynamicsearch.ai.it;

import static org.junit.jupiter.api.Assertions.*;

import com.bereczkitamas.libs.spring.dynamicsearch.ai.DynamicSearchAiTool;
import com.bereczkitamas.libs.spring.dynamicsearch.ai.mcp.DynamicSearchMcpHandler;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchOperation;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchRequest;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchRequestBuilder;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SimpleSearchFieldRegistry;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.Order;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.OrderDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderDynamicSearchMcpHandlerIT extends AbstractAiMongoIntegrationTest {

  private DynamicSearchMcpHandler<Order> mcpHandler;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUpMcpHandler() {
    objectMapper = new ObjectMapper().findAndRegisterModules();
    DynamicSearchAiTool<Order> aiTool =
        DynamicSearchAiTool.of(
            executor, SimpleSearchFieldRegistry.from(OrderDto.class), Order.class);
    mcpHandler = new DynamicSearchMcpHandler<>(aiTool, objectMapper);
  }

  @Test
  @DisplayName("MCP handler exposes tool definitions")
  void testToolDefinitions() {
    List<Map<String, Object>> definitions = mcpHandler.getToolDefinitions();
    assertNotNull(definitions);
    assertEquals(2, definitions.size());
    assertEquals(DynamicSearchMcpHandler.TOOL_LIST_FIELDS, definitions.get(0).get("name"));
    assertEquals(DynamicSearchMcpHandler.TOOL_EXECUTE_SEARCH, definitions.get(1).get("name"));
  }

  @Test
  @DisplayName("MCP handler returns schema on list_search_fields call")
  void testListSearchFields() {
    Map<String, Object> response =
        mcpHandler.handleToolCall(DynamicSearchMcpHandler.TOOL_LIST_FIELDS, Map.of());

    assertNotNull(response);
    List<?> content = (List<?>) response.get("content");
    assertNotNull(content);
    assertFalse(content.isEmpty());

    Map<?, ?> firstContent = (Map<?, ?>) content.getFirst();
    String text = (String) firstContent.get("text");
    assertTrue(text.contains("orderNumber"));
    assertTrue(text.contains("customerName"));
    assertTrue(text.contains("assets"));
  }

  @Test
  @DisplayName("MCP handler executes search against embedded MongoDB on execute_dynamic_search call")
  void testExecuteDynamicSearchMcpCall() throws Exception {
    SearchRequest searchRequest =
        SearchRequestBuilder.search()
            .where("status", SearchOperation.EQUALS, "COMPLETED")
            .build();

    Map<String, Object> arguments =
        Map.of(
            "searchRequest", objectMapper.convertValue(searchRequest, Map.class),
            "page", 0,
            "size", 10,
            "sortField", "orderNumber",
            "sortDirection", "ASC");

    Map<String, Object> response =
        mcpHandler.handleToolCall(DynamicSearchMcpHandler.TOOL_EXECUTE_SEARCH, arguments);

    assertNotNull(response);
    assertEquals(false, response.get("isError"));

    List<?> content = (List<?>) response.get("content");
    Map<?, ?> firstContent = (Map<?, ?>) content.getFirst();
    String text = (String) firstContent.get("text");
    com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(text);

    assertTrue(root.get("success").asBoolean());
    assertEquals(2, root.get("totalElements").asInt());
    assertTrue(text.contains("ORD-1001"));
    assertTrue(text.contains("ORD-1004"));
  }

  @Test
  @DisplayName("MCP handler returns structured error on invalid search request")
  void testExecuteInvalidSearchMcpCall() throws Exception {
    Map<String, Object> invalidSearchReq =
        Map.of(
            "operator", "AND",
            "criteria",
                List.of(
                    Map.of(
                        "field", "nonExistentField",
                        "operation", "EQUALS",
                        "value", "123")));

    Map<String, Object> arguments = Map.of("searchRequest", invalidSearchReq);

    Map<String, Object> response =
        mcpHandler.handleToolCall(DynamicSearchMcpHandler.TOOL_EXECUTE_SEARCH, arguments);

    assertNotNull(response);
    assertEquals(true, response.get("isError"));

    List<?> content = (List<?>) response.get("content");
    Map<?, ?> firstContent = (Map<?, ?>) content.getFirst();
    String text = (String) firstContent.get("text");
    com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(text);

    assertFalse(root.get("success").asBoolean());
    assertTrue(text.contains("nonExistentField"));
  }
}
