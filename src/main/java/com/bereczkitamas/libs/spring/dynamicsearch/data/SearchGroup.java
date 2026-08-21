package com.bereczkitamas.libs.spring.dynamicsearch.data;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SearchGroup {
  @Nonnull private String operator = SearchRequest.AND_OPERATOR;
  @Nonnull private List<SearchCriteria> criteria = new ArrayList<>();
  @Nonnull private List<SearchGroup> groups = new ArrayList<>();
}
