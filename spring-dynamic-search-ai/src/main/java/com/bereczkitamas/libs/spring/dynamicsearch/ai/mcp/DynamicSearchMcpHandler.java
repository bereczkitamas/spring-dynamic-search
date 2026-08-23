package com.bereczkitamas.libs.spring.dynamicsearch.ai.mcp;

import com.bereczkitamas.libs.spring.dynamicsearch.ai.DynamicSearchAiTool;
import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchSchemaDescription;
import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchToolRequest;
import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchToolResult;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model Context Protocol (MCP) Tool Handler for external AI Agents (Cursor, Claude Desktop, Antigravity, etc.).
 */
public class DynamicSearchMcpHandler<T> {

  public static final String TOOL_LIST_FIELDS = "list_search_fields";
  public static final String TOOL_EXECUTE_SEARCH = "execute_dynamic_search";

  private final DynamicSearchAiTool<T> aiTool;
  private final ObjectMapper objectMapper;

  public DynamicSearchMcpHandler(DynamicSearchAiTool<T> aiTool) {
    this(aiTool, new ObjectMapper().findAndRegisterModules());
  }

  public DynamicSearchMcpHandler(DynamicSearchAiTool<T> aiTool, ObjectMapper objectMapper) {
    this.aiTool = aiTool;
    this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
  }

  /**
   * Returns MCP tool definitions for registration with an MCP server.
   */
  public List<Map<String, Object>> getToolDefinitions() {
    Map<String, Object> listFieldsTool = new LinkedHashMap<>();
    listFieldsTool.put("name", TOOL_LIST_FIELDS);
    listFieldsTool.put(
        "description",
        "Lists all available searchable fields, data types, allowed operators, and documentation for the domain.");
    listFieldsTool.put("inputSchema", Map.of("type", "object", "properties", Map.of()));

    Map<String, Object> executeSearchTool = new LinkedHashMap<>();
    executeSearchTool.put("name", TOOL_EXECUTE_SEARCH);
    executeSearchTool.put(
        "description",
        "Executes a structured search query on the domain database using whitelisted criteria, joins, and pagination.");

    Map<String, Object> executeProps = new LinkedHashMap<>();
    executeProps.put("searchRequest", aiTool.getJsonSchema());

    Map<String, Object> pageProp = new LinkedHashMap<>();
    pageProp.put("type", "integer");
    pageProp.put("default", 0);
    pageProp.put("description", "Zero-based page number");
    executeProps.put("page", pageProp);

    Map<String, Object> sizeProp = new LinkedHashMap<>();
    sizeProp.put("type", "integer");
    sizeProp.put("default", 20);
    sizeProp.put("description", "Page size limit");
    executeProps.put("size", sizeProp);

    Map<String, Object> sortFieldProp = new LinkedHashMap<>();
    sortFieldProp.put("type", "string");
    sortFieldProp.put("description", "Field name to sort by");
    executeProps.put("sortField", sortFieldProp);

    Map<String, Object> sortDirProp = new LinkedHashMap<>();
    sortDirProp.put("type", "string");
    sortDirProp.put("enum", List.of("ASC", "DESC"));
    sortDirProp.put("default", "ASC");
    sortDirProp.put("description", "Sort direction");
    executeProps.put("sortDirection", sortDirProp);

    Map<String, Object> executeInputSchema = new LinkedHashMap<>();
    executeInputSchema.put("type", "object");
    executeInputSchema.put("properties", executeProps);
    executeInputSchema.put("required", List.of("searchRequest"));

    executeSearchTool.put("inputSchema", executeInputSchema);

    return List.of(listFieldsTool, executeSearchTool);
  }

  /**
   * Dispatches an MCP tool call and returns an MCP-compliant response payload.
   */
  public Map<String, Object> handleToolCall(String toolName, Map<String, Object> arguments) {
    if (TOOL_LIST_FIELDS.equals(toolName)) {
      SearchSchemaDescription schema = aiTool.getSchema();
      return Map.of("content", List.of(Map.of("type", "text", "text", toJson(schema))));
    }

    if (TOOL_EXECUTE_SEARCH.equals(toolName)) {
      try {
        SearchToolRequest request = parseRequest(arguments);
        SearchToolResult<T> result = aiTool.execute(request);
        return Map.of(
            "isError", !result.isSuccess(),
            "content", List.of(Map.of("type", "text", "text", toJson(result))));
      } catch (Exception ex) {
        return Map.of(
            "isError", true,
            "content", List.of(Map.of("type", "text", "text", "Failed to parse tool arguments: " + ex.getMessage())));
      }
    }

    return Map.of(
        "isError", true,
        "content", List.of(Map.of("type", "text", "text", "Unknown tool: " + toolName)));
  }

  private SearchToolRequest parseRequest(Map<String, Object> arguments) {
    if (arguments == null) {
      return SearchToolRequest.builder().build();
    }

    Object searchReqObj = arguments.get("searchRequest");
    SearchRequest searchRequest =
        searchReqObj != null ? objectMapper.convertValue(searchReqObj, SearchRequest.class) : null;

    int page = arguments.containsKey("page") ? ((Number) arguments.get("page")).intValue() : 0;
    int size = arguments.containsKey("size") ? ((Number) arguments.get("size")).intValue() : 20;
    String sortField = (String) arguments.get("sortField");
    String sortDirection = (String) arguments.getOrDefault("sortDirection", "ASC");

    return SearchToolRequest.builder()
        .searchRequest(searchRequest)
        .page(page)
        .size(size)
        .sortField(sortField)
        .sortDirection(sortDirection)
        .build();
  }

  private String toJson(Object object) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    } catch (Exception e) {
      return String.valueOf(object);
    }
  }
}
