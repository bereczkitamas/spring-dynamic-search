package com.bereczkitamas.libs.spring.dynamicsearch.data;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.mongodb.core.query.Criteria;

public class MongoQueryBuilder {

  private final MongoCriteriaBuilder criteriaBuilder;
  private final SearchCriteriaValidator validator;

  public MongoQueryBuilder() {
    this(new MongoCriteriaBuilder(), new SearchCriteriaValidator());
  }

  public MongoQueryBuilder(SearchCriteriaValidator validator) {
    this(new MongoCriteriaBuilder(), validator);
  }

  public MongoQueryBuilder(
      MongoCriteriaBuilder criteriaBuilder, SearchCriteriaValidator validator) {
    this.criteriaBuilder = criteriaBuilder != null ? criteriaBuilder : new MongoCriteriaBuilder();
    this.validator = validator != null ? validator : new SearchCriteriaValidator();
  }

  public Criteria build(SearchRequest request, SearchFieldRegistry registry) {
    List<Criteria> criteriaList = new ArrayList<>();

    // Flat criteria
    if (request.getCriteria() != null) {
      request.getCriteria().stream()
          .map(c -> validator.validateAndTransform(c, registry))
          .map(criteriaBuilder::buildCriteria)
          .forEach(criteriaList::add);
    }

    // Nested groups
    if (request.getGroups() != null) {
      request.getGroups().stream().map(g -> buildFromGroup(g, registry)).forEach(criteriaList::add);
    }

    return criteriaBuilder.combine(criteriaList, request.getOperator());
  }

  private Criteria buildFromGroup(SearchGroup group, SearchFieldRegistry registry) {
    List<Criteria> criteriaList = new ArrayList<>();

    if (group.getCriteria() != null) {
      group.getCriteria().stream()
          .map(c -> validator.validateAndTransform(c, registry))
          .map(criteriaBuilder::buildCriteria)
          .forEach(criteriaList::add);
    }

    if (group.getGroups() != null) {
      group.getGroups().stream().map(g -> buildFromGroup(g, registry)).forEach(criteriaList::add);
    }

    return criteriaBuilder.combine(criteriaList, group.getOperator());
  }
}
