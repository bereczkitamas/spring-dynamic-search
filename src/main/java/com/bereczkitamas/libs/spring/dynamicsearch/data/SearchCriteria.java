package com.bereczkitamas.libs.spring.dynamicsearch.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchCriteria {
  private String field;
  private SearchOperation operation;
  private Object value;
}
