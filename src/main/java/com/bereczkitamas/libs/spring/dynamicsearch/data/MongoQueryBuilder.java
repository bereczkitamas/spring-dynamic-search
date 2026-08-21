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

  /**
   * Builds criteria for local fields that can be safely evaluated before $lookup stages.
   * Only applicable when the top-level operator is AND.
   */
  public Criteria buildPreJoinCriteria(SearchRequest request, SearchFieldRegistry registry) {
    if (!SearchRequest.AND_OPERATOR.equalsIgnoreCase(request.getOperator())) {
      return new Criteria();
    }

    List<Criteria> criteriaList = new ArrayList<>();

    // Local flat criteria
    if (request.getCriteria() != null) {
      request.getCriteria().stream()
          .filter(c -> !registry.resolve(c.getField()).isJoined())
          .map(c -> validator.validateAndTransform(c, registry))
          .map(criteriaBuilder::buildCriteria)
          .forEach(criteriaList::add);
    }

    // Purely local groups
    if (request.getGroups() != null) {
      request.getGroups().stream()
          .filter(g -> !hasJoinedFields(g, registry))
          .map(g -> buildFromGroup(g, registry))
          .forEach(criteriaList::add);
    }

    return criteriaBuilder.combine(criteriaList, SearchRequest.AND_OPERATOR);
  }

  /**
   * Builds criteria that must be evaluated after $lookup stages.
   * If the top-level operator is OR and joined fields exist, returns all criteria.
   */
  public Criteria buildPostJoinCriteria(SearchRequest request, SearchFieldRegistry registry) {
    if (!SearchRequest.AND_OPERATOR.equalsIgnoreCase(request.getOperator())) {
      return build(request, registry);
    }

    List<Criteria> criteriaList = new ArrayList<>();

    // Joined flat criteria
    if (request.getCriteria() != null) {
      request.getCriteria().stream()
          .filter(c -> registry.resolve(c.getField()).isJoined())
          .map(c -> validator.validateAndTransform(c, registry))
          .map(criteriaBuilder::buildCriteria)
          .forEach(criteriaList::add);
    }

    // Mixed or purely joined groups
    if (request.getGroups() != null) {
      request.getGroups().stream()
          .filter(g -> hasJoinedFields(g, registry))
          .map(g -> buildFromGroup(g, registry))
          .forEach(criteriaList::add);
    }

    return criteriaBuilder.combine(criteriaList, SearchRequest.AND_OPERATOR);
  }

  /** Recursively checks if a group contains any criteria referencing joined fields. */
  public boolean hasJoinedFields(SearchGroup group, SearchFieldRegistry registry) {
    if (group.getCriteria() != null) {
      boolean hasJoinedCriteria =
          group.getCriteria().stream()
              .anyMatch(c -> registry.resolve(c.getField()).isJoined());
      if (hasJoinedCriteria) {
        return true;
      }
    }

    if (group.getGroups() != null) {
      return group.getGroups().stream().anyMatch(g -> hasJoinedFields(g, registry));
    }

    return false;
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
