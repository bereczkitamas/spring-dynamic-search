package com.bereczkitamas.libs.spring.dynamicsearch.data;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class SearchCriteria {
  private String field;
  private SearchOperation operation;
  private Object value;

  @Nullable private List<SearchCriteria> elementCriteria;
  @Builder.Default @Nonnull private String elementOperator = SearchRequest.AND_OPERATOR;

  public SearchCriteria(String field, SearchOperation operation, Object value) {
    this(field, operation, value, null, SearchRequest.AND_OPERATOR);
  }
}

