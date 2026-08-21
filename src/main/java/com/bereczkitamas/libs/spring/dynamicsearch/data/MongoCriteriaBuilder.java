package com.bereczkitamas.libs.spring.dynamicsearch.data;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.mongodb.core.query.Criteria;

public class MongoCriteriaBuilder {

  /** Builds a single Criteria for one SearchCriteria (after DTO -> document field mapping) */
  public Criteria buildCriteria(SearchCriteria sc) {
    String field = sc.getField();
    Object value = sc.getValue();

    return switch (sc.getOperation()) {
      case EQUALS -> Criteria.where(field).is(value);
      case NOT_EQUALS -> Criteria.where(field).ne(value);
      case LIKE -> Criteria.where(field).regex(".*" + Pattern.quote(value.toString()) + ".*", "i");
      case STARTS_WITH -> Criteria.where(field).regex("^" + Pattern.quote(value.toString()), "i");
      case ENDS_WITH -> Criteria.where(field).regex(Pattern.quote(value.toString()) + "$", "i");
      case REGEX -> Criteria.where(field).regex(Pattern.quote(value.toString()), "i");
      case GREATER_THAN -> Criteria.where(field).gt(value);
      case LESS_THAN -> Criteria.where(field).lt(value);
      case GREATER_THAN_OR_EQUAL -> Criteria.where(field).gte(value);
      case LESS_THAN_OR_EQUAL -> Criteria.where(field).lte(value);
      case IN -> Criteria.where(field).in((Collection<?>) value);
      case NOT_IN -> Criteria.where(field).nin((Collection<?>) value);
      case IS_NULL -> Criteria.where(field).is(null);
      case IS_NOT_NULL -> Criteria.where(field).ne(null);
    };
  }

  /** Combines a flat list of criteria with AND/OR */
  public Criteria combine(List<Criteria> criteriaList, String operator) {
    if (criteriaList.isEmpty()) {
      return new Criteria();
    }
    if (criteriaList.size() == 1) {
      return criteriaList.getFirst();
    }

    Criteria[] array = criteriaList.toArray(new Criteria[0]);
    return SearchRequest.OR_OPERATOR.equalsIgnoreCase(operator)
        ? new Criteria().orOperator(array)
        : new Criteria().andOperator(array);
  }
}
