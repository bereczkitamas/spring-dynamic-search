package com.bereczkitamas.libs.spring.dynamicsearch.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent builder DSL for constructing {@link SearchRequest} instances with nested groups
 * and array {@code elemMatch} criteria.
 */
public class SearchRequestBuilder {

  private String operator = SearchRequest.AND_OPERATOR;
  private final List<SearchCriteria> criteria = new ArrayList<>();
  private final List<SearchGroup> groups = new ArrayList<>();
  private ProjectionRequest projection;

  public static SearchRequestBuilder search() {
    return new SearchRequestBuilder();
  }

  public SearchRequestBuilder and() {
    this.operator = SearchRequest.AND_OPERATOR;
    return this;
  }

  public SearchRequestBuilder or() {
    this.operator = SearchRequest.OR_OPERATOR;
    return this;
  }

  public SearchRequestBuilder operator(String operator) {
    this.operator = operator != null ? operator : SearchRequest.AND_OPERATOR;
    return this;
  }

  public SearchRequestBuilder where(String field, SearchOperation op, Object value) {
    this.criteria.add(new SearchCriteria(field, op, value));
    return this;
  }

  public SearchRequestBuilder where(SearchCriteria criteria) {
    if (criteria != null) {
      this.criteria.add(criteria);
    }
    return this;
  }

  public SearchRequestBuilder elemMatch(String arrayField, Consumer<ElemMatchBuilder> configurer) {
    return elemMatch(arrayField, SearchRequest.AND_OPERATOR, configurer);
  }

  public SearchRequestBuilder elemMatch(
      String arrayField, String elementOperator, Consumer<ElemMatchBuilder> configurer) {
    ElemMatchBuilder emb = new ElemMatchBuilder();
    if (elementOperator != null) {
      emb.operator(elementOperator);
    }
    if (configurer != null) {
      configurer.accept(emb);
    }
    this.criteria.add(
        SearchCriteria.builder()
            .field(arrayField)
            .operation(SearchOperation.ELEM_MATCH)
            .elementOperator(emb.getOperator())
            .elementCriteria(emb.build())
            .build());
    return this;
  }

  public SearchRequestBuilder group(Consumer<SearchGroupBuilder> configurer) {
    SearchGroupBuilder builder = new SearchGroupBuilder();
    if (configurer != null) {
      configurer.accept(builder);
    }
    this.groups.add(builder.build());
    return this;
  }

  public SearchRequestBuilder projection(ProjectionRequest projection) {
    this.projection = projection;
    return this;
  }

  public SearchRequest build() {
    return SearchRequest.builder()
        .operator(this.operator)
        .criteria(new ArrayList<>(this.criteria))
        .groups(new ArrayList<>(this.groups))
        .projection(this.projection)
        .build();
  }

  public static class SearchGroupBuilder {
    private String operator = SearchRequest.AND_OPERATOR;
    private final List<SearchCriteria> criteria = new ArrayList<>();
    private final List<SearchGroup> groups = new ArrayList<>();

    public SearchGroupBuilder and() {
      this.operator = SearchRequest.AND_OPERATOR;
      return this;
    }

    public SearchGroupBuilder or() {
      this.operator = SearchRequest.OR_OPERATOR;
      return this;
    }

    public SearchGroupBuilder operator(String operator) {
      this.operator = operator != null ? operator : SearchRequest.AND_OPERATOR;
      return this;
    }

    public SearchGroupBuilder where(String field, SearchOperation op, Object value) {
      this.criteria.add(new SearchCriteria(field, op, value));
      return this;
    }

    public SearchGroupBuilder where(SearchCriteria criteria) {
      if (criteria != null) {
        this.criteria.add(criteria);
      }
      return this;
    }

    public SearchGroupBuilder elemMatch(String arrayField, Consumer<ElemMatchBuilder> configurer) {
      return elemMatch(arrayField, SearchRequest.AND_OPERATOR, configurer);
    }

    public SearchGroupBuilder elemMatch(
        String arrayField, String elementOperator, Consumer<ElemMatchBuilder> configurer) {
      ElemMatchBuilder emb = new ElemMatchBuilder();
      if (elementOperator != null) {
        emb.operator(elementOperator);
      }
      if (configurer != null) {
        configurer.accept(emb);
      }
      this.criteria.add(
          SearchCriteria.builder()
              .field(arrayField)
              .operation(SearchOperation.ELEM_MATCH)
              .elementOperator(emb.getOperator())
              .elementCriteria(emb.build())
              .build());
      return this;
    }

    public SearchGroupBuilder subGroup(Consumer<SearchGroupBuilder> configurer) {
      SearchGroupBuilder nested = new SearchGroupBuilder();
      if (configurer != null) {
        configurer.accept(nested);
      }
      this.groups.add(nested.build());
      return this;
    }

    public SearchGroup build() {
      return SearchGroup.builder()
          .operator(this.operator)
          .criteria(new ArrayList<>(this.criteria))
          .groups(new ArrayList<>(this.groups))
          .build();
    }
  }

  public static class ElemMatchBuilder {
    private String operator = SearchRequest.AND_OPERATOR;
    private final List<SearchCriteria> criteria = new ArrayList<>();

    public ElemMatchBuilder and() {
      this.operator = SearchRequest.AND_OPERATOR;
      return this;
    }

    public ElemMatchBuilder or() {
      this.operator = SearchRequest.OR_OPERATOR;
      return this;
    }

    public ElemMatchBuilder operator(String operator) {
      this.operator = operator != null ? operator : SearchRequest.AND_OPERATOR;
      return this;
    }

    public String getOperator() {
      return this.operator;
    }

    public ElemMatchBuilder where(String field, SearchOperation op, Object value) {
      this.criteria.add(new SearchCriteria(field, op, value));
      return this;
    }

    public ElemMatchBuilder where(SearchCriteria criteria) {
      if (criteria != null) {
        this.criteria.add(criteria);
      }
      return this;
    }

    public List<SearchCriteria> build() {
      return new ArrayList<>(this.criteria);
    }
  }
}
