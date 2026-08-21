package com.bereczkitamas.libs.spring.dynamicsearch.data;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SearchRequest {
  public static final String AND_OPERATOR = "AND";
  public static final String OR_OPERATOR = "OR";

  @Nullable private List<SearchCriteria> criteria;
  @Nullable private List<SearchGroup> groups;
  @Builder.Default @Nonnull private String operator = AND_OPERATOR; // AND or OR

  @Nullable private ProjectionRequest projection;
}
