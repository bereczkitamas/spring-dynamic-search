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
      case REGEX -> Criteria.where(field).regex(value.toString(), "i");
      case GREATER_THAN -> Criteria.where(field).gt(value);
      case LESS_THAN -> Criteria.where(field).lt(value);
      case GREATER_THAN_OR_EQUAL -> Criteria.where(field).gte(value);
      case LESS_THAN_OR_EQUAL -> Criteria.where(field).lte(value);
      case IN -> Criteria.where(field).in(toCollection(value));
      case NOT_IN -> Criteria.where(field).nin(toCollection(value));
      case IS_NULL -> Criteria.where(field).is(null);
      case IS_NOT_NULL -> Criteria.where(field).ne(null);
      case BETWEEN -> {
        List<?> bounds = toList(value);
        yield Criteria.where(field).gte(bounds.get(0)).lte(bounds.get(1));
      }
      case NOT_BETWEEN -> {
        List<?> bounds = toList(value);
        yield new Criteria()
            .orOperator(Criteria.where(field).lt(bounds.get(0)), Criteria.where(field).gt(bounds.get(1)));
      }
      case EXISTS -> Criteria.where(field).exists(true);
      case DOES_NOT_EXIST -> Criteria.where(field).exists(false);
      case IS_EMPTY -> new Criteria()
          .orOperator(
              Criteria.where(field).is(null),
              Criteria.where(field).is(List.of()),
              Criteria.where(field).is(""));
      case IS_NOT_EMPTY -> new Criteria()
          .andOperator(
              Criteria.where(field).ne(null),
              Criteria.where(field).ne(List.of()),
              Criteria.where(field).ne(""));
      case CONTAINS_ALL -> Criteria.where(field).all(toCollection(value));
      case ELEM_MATCH -> {
        List<SearchCriteria> elementCriteria = sc.getElementCriteria();
        if (elementCriteria == null || elementCriteria.isEmpty()) {
          yield new Criteria();
        }
        List<Criteria> innerCriteriaList =
            elementCriteria.stream().map(this::buildCriteria).toList();
        Criteria innerCombined = combine(innerCriteriaList, sc.getElementOperator());
        yield Criteria.where(field).elemMatch(innerCombined);
      }
      case SIZE -> Criteria.where(field).size(((Number) value).intValue());
    };

  }

  private List<?> toList(Object value) {
    if (value instanceof List<?> list) {
      return list;
    }
    if (value instanceof Collection<?> coll) {
      return List.copyOf(coll);
    }
    if (value instanceof Object[] arr) {
      return List.of(arr);
    }
    if (value == null) {
      return List.of();
    }
    return List.of(value);
  }

  private Collection<?> toCollection(Object value) {
    if (value instanceof Collection<?> coll) {
      return coll;
    }
    if (value instanceof Object[] arr) {
      return List.of(arr);
    }
    if (value == null) {
      return List.of();
    }
    return List.of(value);
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
