package com.bereczkitamas.libs.spring.dynamicsearch.data;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
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
public class SearchGroup {
  @Builder.Default @Nonnull private String operator = SearchRequest.AND_OPERATOR;
  @Builder.Default @Nonnull private List<SearchCriteria> criteria = new ArrayList<>();
  @Builder.Default @Nonnull private List<SearchGroup> groups = new ArrayList<>();
}
