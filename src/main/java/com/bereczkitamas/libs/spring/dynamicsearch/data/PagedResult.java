package com.bereczkitamas.libs.spring.dynamicsearch.data;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
public class PagedResult<T> {
  public static final String FIELD_DATA = "data";
  public static final String FIELD_TOTAL_COUNT = "totalCount";

  @NonNull private List<T> data = new ArrayList<>();
  @NonNull private List<CountResult> totalCount = new ArrayList<>();

  public long getTotal() {
    return totalCount.isEmpty() ? 0 : totalCount.getFirst().total();
  }
}
