package com.bereczkitamas.libs.spring.dynamicsearch.data;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.FacetOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Builds and executes paginated MongoDB aggregation pipelines.
 *
 * <p>Combines the pipeline-building logic (with optional joins and DTO->document sort field
 * translation via a {@link SearchFieldRegistry}) and the aggregation execution + result unwrapping.
 * All pipelines terminate with a {@code $facet} stage producing {@code data} and {@code totalCount}
 * branches (see {@link PagedResult}).
 */
@Slf4j
@RequiredArgsConstructor
public class PagedAggregationExecutor {

  private final MongoTemplate mongoTemplate;
  private final MongoQueryBuilder queryBuilder = new MongoQueryBuilder();
  private final JoinResolver joinResolver = new JoinResolver();
  private final ProjectionBuilder projectionBuilder = new ProjectionBuilder();

  /**
   * Builds a pipeline from a {@link SearchRequest} (with joins resolved from the registry) and
   * executes it.
   */
  public <E, F extends PagedResult<E>> PagedSearchResponse<E> executeSearch(
      SearchRequest request,
      SearchFieldRegistry registry,
      Pageable pageable,
      Class<E> entityClass,
      Class<F> facetResultClass) {
    Aggregation aggregation = buildSearchPipeline(request, registry, pageable);
    return runAggregation(aggregation, pageable, entityClass, facetResultClass);
  }

  /**
   * Executes a simple match-based pipeline: {@code $match -> [$sort] -> [$facet | passthrough]}.
   * Use this when no joins are required and the {@link Criteria} + {@link Sort} are already
   * resolved to document fields.
   */
  public <E, F extends PagedResult<E>> PagedSearchResponse<E> execute(
      Criteria criteria,
      Pageable pageable,
      @Nullable Sort sort,
      Class<E> entityClass,
      Class<F> facetResultClass) {
    List<AggregationOperation> ops = new ArrayList<>();
    ops.add(Aggregation.match(criteria));
    if (sort != null && sort.isSorted()) {
      ops.add(Aggregation.sort(sort));
    }
    if (pageable.isPaged()) {
      ops.add(buildPaginationFacet(pageable));
    }
    return runAggregation(Aggregation.newAggregation(ops), pageable, entityClass, facetResultClass);
  }

  /** Builds the search pipeline without executing it (useful for logging/testing). */
  public Aggregation buildSearchPipeline(
      SearchRequest request, SearchFieldRegistry registry, Pageable pageable) {
    List<AggregationOperation> ops = new ArrayList<>();

    // 1. Determine which joins are needed
    Set<JoinDescriptor> joins = joinResolver.resolveJoins(request, pageable, registry);

    // 2. Add $lookup + $unwind for each required join
    for (JoinDescriptor join : joins) {
      ops.add(
          Aggregation.lookup(
              join.getCollectionName(),
              join.getLocalField(),
              join.getForeignField(),
              join.getAs()));
      if (join.isSingleResult()) {
        // preserveNullAndEmptyArrays = true -> LEFT JOIN behavior
        ops.add(Aggregation.unwind(join.getAs(), true));
      }
    }

    // 3. $match with all criteria (now safe to reference joined fields)
    Criteria criteria = queryBuilder.build(request, registry);
    ops.add(Aggregation.match(criteria));

    // 4. Sort (translate DTO field -> document path)
    Sort resolvedSort = resolveSort(pageable, registry);
    if (resolvedSort.isSorted()) {
      ops.add(Aggregation.sort(resolvedSort));
    }

    // 5. Project (before facet to reduce memory)
    ProjectionResult projection = projectionBuilder.build(request.getProjection(), registry);
    if (projection.isApplicable()) {
      ops.add(buildProjectionStage(projection));
    }

    // 6. $facet for pagination + count — only when paged. Unpaged callers stream the full
    // result via the entity class directly (see run()), avoiding $facet's 100MB per-branch cap.
    if (pageable.isPaged()) {
      ops.add(buildPaginationFacet(pageable));
    }

    return Aggregation.newAggregation(ops);
  }

  private Sort resolveSort(Pageable pageable, SearchFieldRegistry registry) {
    if (!pageable.getSort().isSorted()) {
      return Sort.unsorted();
    }
    return pageable.getSort().stream()
        .map(
            ordering -> {
              String sortField = registry.resolve(ordering.getProperty()).getDocumentField();
              return Sort.by(ordering.getDirection(), sortField);
            })
        .reduce(Sort::and)
        .orElse(Sort.unsorted());
  }

  private AggregationOperation buildProjectionStage(ProjectionResult projection) {
    if (projection.getMode() == ProjectionResult.Mode.INCLUDE) {
      String[] fields = projection.getFields().toArray(new String[0]);
      return Aggregation.project(fields);
    } else {
      ProjectionOperation project = Aggregation.project();
      for (String field : projection.getFields()) {
        project = project.andExclude(field);
      }
      return project;
    }
  }

  private FacetOperation buildPaginationFacet(Pageable pageable) {
    return Aggregation.facet(
            Aggregation.skip(pageable.getOffset()), Aggregation.limit(pageable.getPageSize()))
        .as(PagedResult.FIELD_DATA)
        .and(Aggregation.count().as("total"))
        .as(PagedResult.FIELD_TOTAL_COUNT);
  }

  private <E, F extends PagedResult<E>> PagedSearchResponse<E> runAggregation(
      Aggregation aggregation, Pageable pageable, Class<E> entityClass, Class<F> facetResultClass) {
    String collection = mongoTemplate.getCollectionName(entityClass);

    if (pageable.isPaged()) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Executing {} aggregation on [{}]. Pipeline: {}",
            pageable.isPaged() ? "paged" : "unpaged",
            collection,
            aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT));
      }

      AggregationResults<F> results =
          mongoTemplate.aggregate(aggregation, collection, facetResultClass);
      F result = results.getUniqueMappedResult();
      if (result == null) {
        return new PagedSearchResponse<>(List.of(), 0);
      }
      return new PagedSearchResponse<>(result.getData(), result.getTotal());
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "Executing {} aggregation on [{}]. Pipeline: {}",
          pageable.isPaged() ? "paged" : "unpaged",
          collection,
          aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT));
    }
    List<E> data = mongoTemplate.aggregate(aggregation, collection, entityClass).getMappedResults();
    return new PagedSearchResponse<>(data, data.size());
  }
}
