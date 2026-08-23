package com.bereczkitamas.libs.spring.dynamicsearch.ai;

import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchSchemaDescription;
import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchToolRequest;
import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchToolResult;
import com.bereczkitamas.libs.spring.dynamicsearch.data.PagedAggregationExecutor;
import com.bereczkitamas.libs.spring.dynamicsearch.data.PagedSearchResponse;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchFieldRegistry;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Reusable AI Agent Tool for executing dynamic queries and providing schema / auto-repair diagnostics.
 *
 * @param <T> the entity or result DTO class
 */
public class DynamicSearchAiTool<T> {

  private final PagedAggregationExecutor executor;
  private final SearchFieldRegistry registry;
  private final Class<T> domainClass;
  private final SearchSchemaService schemaService;
  private final SearchErrorFeedbackFormatter feedbackFormatter;

  public DynamicSearchAiTool(
      PagedAggregationExecutor executor,
      SearchFieldRegistry registry,
      Class<T> domainClass) {
    this(
        executor,
        registry,
        domainClass,
        new SearchSchemaService(),
        new SearchErrorFeedbackFormatter());
  }

  public DynamicSearchAiTool(
      PagedAggregationExecutor executor,
      SearchFieldRegistry registry,
      Class<T> domainClass,
      SearchSchemaService schemaService,
      SearchErrorFeedbackFormatter feedbackFormatter) {
    this.executor = executor;
    this.registry = registry;
    this.domainClass = domainClass;
    this.schemaService = schemaService != null ? schemaService : new SearchSchemaService();
    this.feedbackFormatter = feedbackFormatter != null ? feedbackFormatter : new SearchErrorFeedbackFormatter();
  }

  /**
   * Factory method creating a tool for the specified domain class.
   */
  public static <T> DynamicSearchAiTool<T> of(
      PagedAggregationExecutor executor,
      SearchFieldRegistry registry,
      Class<T> domainClass) {
    return new DynamicSearchAiTool<>(executor, registry, domainClass);
  }

  /**
   * Executes a search tool request and returns structured items or actionable LLM error feedback.
   */
  public SearchToolResult<T> execute(SearchToolRequest request) {
    if (request == null || request.getSearchRequest() == null) {
      return SearchToolResult.<T>builder()
          .success(false)
          .items(Collections.emptyList())
          .error(
              feedbackFormatter.format(
                  new IllegalArgumentException("searchRequest cannot be null"), registry))
          .build();
    }

    try {
      Pageable pageable = buildPageable(request);
      PagedSearchResponse<T> response =
          executor.executeSearch(
              request.getSearchRequest(), registry, pageable, domainClass);

      int totalPages =
          pageable.getPageSize() > 0
              ? (int) Math.ceil((double) response.total() / pageable.getPageSize())
              : 1;

      return SearchToolResult.<T>builder()
          .success(true)
          .items(response.data())
          .totalElements(response.total())
          .totalPages(totalPages)
          .pageNumber(pageable.getPageNumber())
          .pageSize(pageable.getPageSize())
          .build();

    } catch (Exception ex) {
      return SearchToolResult.<T>builder()
          .success(false)
          .items(Collections.emptyList())
          .error(feedbackFormatter.format(ex, registry))
          .build();
    }
  }

  /**
   * Returns the searchable schema description for this domain.
   */
  public SearchSchemaDescription getSchema() {
    return schemaService.describe(registry, domainClass.getSimpleName());
  }

  /**
   * Returns a ready-to-use system prompt for LLMs querying this domain.
   */
  public String getSystemPrompt() {
    return schemaService.generateSystemPrompt(registry, domainClass.getSimpleName());
  }

  /**
   * Returns a JSON schema definition for function calling.
   */
  public Map<String, Object> getJsonSchema() {
    return schemaService.generateJsonSchema(registry, domainClass.getSimpleName());
  }

  private Pageable buildPageable(SearchToolRequest request) {
    int page = Math.max(0, request.getPage());
    int size = request.getSize() > 0 ? request.getSize() : 20;

    if (request.getSortField() != null && !request.getSortField().isBlank()) {
      Sort.Direction direction =
          "DESC".equalsIgnoreCase(request.getSortDirection())
              ? Sort.Direction.DESC
              : Sort.Direction.ASC;
      return PageRequest.of(page, size, Sort.by(direction, request.getSortField()));
    }

    return PageRequest.of(page, size);
  }
}
