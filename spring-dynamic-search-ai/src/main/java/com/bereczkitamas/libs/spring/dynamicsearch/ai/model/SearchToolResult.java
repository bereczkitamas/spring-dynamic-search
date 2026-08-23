package com.bereczkitamas.libs.spring.dynamicsearch.ai.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Standard structured tool execution response returned to LLMs / AI Agents.
 */
@Value
@Builder
public class SearchToolResult<T> {
  boolean success;
  List<T> items;
  long totalElements;
  int totalPages;
  int pageNumber;
  int pageSize;
  SearchErrorFeedback error;
}
