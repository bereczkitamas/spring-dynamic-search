package com.bereczkitamas.libs.spring.dynamicsearch.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.FacetOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Builds and executes paginated MongoDB aggregation pipelines.
 *
 * <p>Combines pipeline-building logic (with optional joins, pre/post-join filtering pushdown,
 * and DTO->document sort field translation via a {@link SearchFieldRegistry}) and aggregation
 * execution directly returning typed {@link PagedSearchResponse}.
 */
@Slf4j
public class PagedAggregationExecutor {

  public static final String FIELD_DATA = "data";
  public static final String FIELD_TOTAL_COUNT = "totalCount";

  private final MongoTemplate mongoTemplate;
  private final MongoQueryBuilder queryBuilder;
  private final JoinResolver joinResolver;
  private final ProjectionBuilder projectionBuilder;
  private final ObjectMapper objectMapper;
  @Nullable private final AggregationOptions defaultAggregationOptions;

  public PagedAggregationExecutor(MongoTemplate mongoTemplate) {
    this(mongoTemplate, new MongoQueryBuilder(), new JoinResolver(), new ProjectionBuilder(), null, null);
  }

  public PagedAggregationExecutor(
      MongoTemplate mongoTemplate, @Nullable AggregationOptions defaultAggregationOptions) {
    this(
        mongoTemplate,
        new MongoQueryBuilder(),
        new JoinResolver(),
        new ProjectionBuilder(),
        null,
        defaultAggregationOptions);
  }

  public PagedAggregationExecutor(
      MongoTemplate mongoTemplate, ObjectMapper objectMapper) {
    this(
        mongoTemplate,
        new MongoQueryBuilder(new SearchCriteriaValidator(objectMapper)),
        new JoinResolver(),
        new ProjectionBuilder(),
        objectMapper,
        null);
  }

  public PagedAggregationExecutor(
      MongoTemplate mongoTemplate,
      ObjectMapper objectMapper,
      @Nullable AggregationOptions defaultAggregationOptions) {
    this(
        mongoTemplate,
        new MongoQueryBuilder(new SearchCriteriaValidator(objectMapper)),
        new JoinResolver(),
        new ProjectionBuilder(),
        objectMapper,
        defaultAggregationOptions);
  }

  public PagedAggregationExecutor(
      MongoTemplate mongoTemplate,
      MongoQueryBuilder queryBuilder,
      JoinResolver joinResolver,
      ProjectionBuilder projectionBuilder) {
    this(mongoTemplate, queryBuilder, joinResolver, projectionBuilder, null, null);
  }

  public PagedAggregationExecutor(
      MongoTemplate mongoTemplate,
      MongoQueryBuilder queryBuilder,
      JoinResolver joinResolver,
      ProjectionBuilder projectionBuilder,
      @Nullable AggregationOptions defaultAggregationOptions) {
    this(mongoTemplate, queryBuilder, joinResolver, projectionBuilder, null, defaultAggregationOptions);
  }

  public PagedAggregationExecutor(
      MongoTemplate mongoTemplate,
      MongoQueryBuilder queryBuilder,
      JoinResolver joinResolver,
      ProjectionBuilder projectionBuilder,
      @Nullable ObjectMapper objectMapper,
      @Nullable AggregationOptions defaultAggregationOptions) {
    this.mongoTemplate = mongoTemplate;
    this.queryBuilder = queryBuilder != null ? queryBuilder : new MongoQueryBuilder();
    this.joinResolver = joinResolver != null ? joinResolver : new JoinResolver();
    this.projectionBuilder = projectionBuilder != null ? projectionBuilder : new ProjectionBuilder();
    this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
    this.defaultAggregationOptions = defaultAggregationOptions;
  }

  /**
   * Builds and executes a search pipeline from a {@link SearchRequest}, returning results converted
   * directly to {@code entityClass}.
   */
  public <E> PagedSearchResponse<E> executeSearch(
      SearchRequest request,
      SearchFieldRegistry registry,
      Pageable pageable,
      Class<E> entityClass) {
    return executeSearch(request, registry, pageable, (AggregationOptions) null, entityClass);
  }

  /**
   * Builds and executes a search pipeline from a {@link SearchRequest} with custom {@link AggregationOptions},
   * returning results converted directly to {@code entityClass}.
   */
  public <E> PagedSearchResponse<E> executeSearch(
      SearchRequest request,
      SearchFieldRegistry registry,
      Pageable pageable,
      @Nullable AggregationOptions options,
      Class<E> entityClass) {
    Aggregation aggregation = buildSearchPipeline(request, registry, pageable, options);
    return runAggregation(aggregation, pageable, entityClass);
  }

  /**
   * Executes a simple match-based pipeline, converting results directly to {@code entityClass}.
   */
  public <E> PagedSearchResponse<E> execute(
      Criteria criteria,
      Pageable pageable,
      @Nullable Sort sort,
      Class<E> entityClass) {
    return execute(criteria, pageable, sort, (AggregationOptions) null, entityClass);
  }

  /**
   * Executes a simple match-based pipeline with custom {@link AggregationOptions},
   * converting results directly to {@code entityClass}.
   */
  public <E> PagedSearchResponse<E> execute(
      Criteria criteria,
      Pageable pageable,
      @Nullable Sort sort,
      @Nullable AggregationOptions options,
      Class<E> entityClass) {
    List<AggregationOperation> ops = new ArrayList<>();
    ops.add(Aggregation.match(criteria));
    if (sort != null && sort.isSorted()) {
      ops.add(Aggregation.sort(sort));
    }
    if (pageable.isPaged()) {
      ops.add(buildPaginationFacet(pageable));
    }
    Aggregation aggregation = applyOptions(Aggregation.newAggregation(ops), options);
    return runAggregation(aggregation, pageable, entityClass);
  }

  /** Builds the search pipeline without executing it (useful for logging/testing). */
  public Aggregation buildSearchPipeline(
      SearchRequest request, SearchFieldRegistry registry, Pageable pageable) {
    return buildSearchPipeline(request, registry, pageable, null);
  }

  /** Builds the search pipeline without executing it (useful for logging/testing). */
  public Aggregation buildSearchPipeline(
      SearchRequest request,
      SearchFieldRegistry registry,
      Pageable pageable,
      @Nullable AggregationOptions options) {
    List<AggregationOperation> ops = new ArrayList<>();

    // 1. Determine which joins are needed
    Set<JoinDescriptor> joins = joinResolver.resolveJoins(request, pageable, registry);

    // 2. Pre-Join $match with local criteria (can use indexes on primary collection)
    Criteria preJoinCriteria = queryBuilder.buildPreJoinCriteria(request, registry);
    if (!preJoinCriteria.getCriteriaObject().isEmpty()) {
      ops.add(Aggregation.match(preJoinCriteria));
    }

    // 3. $lookup + $unwind for each join
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

    // 4. Post-Join $match with joined / remaining criteria
    Criteria postJoinCriteria = queryBuilder.buildPostJoinCriteria(request, registry);
    if (!postJoinCriteria.getCriteriaObject().isEmpty()) {
      ops.add(Aggregation.match(postJoinCriteria));
    }

    // 5. Sort (translate DTO field -> document path)
    Sort resolvedSort = resolveSort(pageable, registry);
    if (resolvedSort.isSorted()) {
      ops.add(Aggregation.sort(resolvedSort));
    }

    // 6. Project (before facet to reduce memory)
    ProjectionResult projection = projectionBuilder.build(request.getProjection(), registry);
    if (projection.isApplicable()) {
      ops.add(buildProjectionStage(projection));
    }

    // 7. $facet for pagination + count — only when paged. Unpaged callers stream the full
    // result via the entity class directly (see run()), avoiding $facet's 100MB per-branch cap.
    if (pageable.isPaged()) {
      ops.add(buildPaginationFacet(pageable));
    }

    return applyOptions(Aggregation.newAggregation(ops), options);
  }

  private Aggregation applyOptions(Aggregation aggregation, @Nullable AggregationOptions options) {
    AggregationOptions effectiveOptions = options != null ? options : defaultAggregationOptions;
    return effectiveOptions != null ? aggregation.withOptions(effectiveOptions) : aggregation;
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
        .as(FIELD_DATA)
        .and(Aggregation.count().as("total"))
        .as(FIELD_TOTAL_COUNT);
  }

  private <E> PagedSearchResponse<E> runAggregation(
      Aggregation aggregation, Pageable pageable, Class<E> entityClass) {
    String collection = mongoTemplate.getCollectionName(entityClass);

    if (pageable.isPaged()) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Executing paged aggregation on [{}]. Pipeline: {}",
            collection,
            aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT));
      }

      AggregationResults<Document> results =
          mongoTemplate.aggregate(aggregation, collection, Document.class);
      Document result = results.getUniqueMappedResult();
      if (result == null) {
        return new PagedSearchResponse<>(List.of(), 0);
      }

      List<Document> rawTotal = result.getList(FIELD_TOTAL_COUNT, Document.class, List.of());
      long total = rawTotal.isEmpty() ? 0 : ((Number) rawTotal.getFirst().get("total")).longValue();

      List<Document> rawData = result.getList(FIELD_DATA, Document.class, List.of());
      MongoConverter converter = mongoTemplate.getConverter();

      List<E> data =
          rawData.stream()
              .map(doc -> mapDocument(doc, entityClass, converter))
              .toList();

      return new PagedSearchResponse<>(data, total);
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "Executing unpaged aggregation on [{}]. Pipeline: {}",
          collection,
          aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT));
    }
    List<E> data = mongoTemplate.aggregate(aggregation, collection, entityClass).getMappedResults();
    return new PagedSearchResponse<>(data, data.size());
  }

  private <E> E mapDocument(Document doc, Class<E> entityClass, @Nullable MongoConverter converter) {
    if (converter != null) {
      try {
        E mapped = converter.read(entityClass, doc);
        if (mapped != null) {
          populateJoinedDocumentReferences(mapped, doc, entityClass, converter);
          return mapped;
        }
      } catch (Exception ignored) {
      }
    }
    return objectMapper.convertValue(doc, entityClass);
  }

  private <E> void populateJoinedDocumentReferences(
      E entity, Document doc, Class<E> entityClass, MongoConverter converter) {
    for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        if (!field.isAnnotationPresent(DocumentReference.class)) {
          continue;
        }
        String fieldName = field.getName();
        Object rawVal = doc.get(fieldName);
        if (rawVal == null) {
          continue;
        }

        try {
          field.setAccessible(true);
          if (rawVal instanceof Document subDoc) {
            Object subEntity = converter.read(field.getType(), subDoc);
            field.set(entity, subEntity);
          } else if (rawVal instanceof List<?> rawList && !rawList.isEmpty() && rawList.getFirst() instanceof Document) {
            Class<?> elemType = resolveGenericElementType(field);
            List<?> subEntities =
                rawList.stream()
                    .filter(Document.class::isInstance)
                    .map(d -> converter.read(elemType, (Document) d))
                    .toList();
            field.set(entity, subEntities);
          }
        } catch (Exception ex) {
          log.trace("Could not populate joined DocumentReference field [{}]: {}", fieldName, ex.getMessage());
        }
      }
    }
  }

  private Class<?> resolveGenericElementType(Field field) {
    if (Collection.class.isAssignableFrom(field.getType())) {
      Type genericType = field.getGenericType();
      if (genericType instanceof ParameterizedType pt) {
        Type[] args = pt.getActualTypeArguments();
        if (args.length > 0 && args[0] instanceof Class<?> clazz) {
          return clazz;
        }
      }
    }
    return Object.class;
  }
}
