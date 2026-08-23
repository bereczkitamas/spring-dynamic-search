package com.bereczkitamas.libs.spring.dynamicsearch.ai.model;

import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchRequest;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard tool request payload passed by LLMs / AI Agents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchToolRequest {
  private SearchRequest searchRequest;
  @Builder.Default
  private int page = 0;
  @Builder.Default
  private int size = 20;
  private String sortField;
  @Builder.Default
  private String sortDirection = "ASC";
}
