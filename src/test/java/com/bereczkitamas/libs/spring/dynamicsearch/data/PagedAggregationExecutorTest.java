package com.bereczkitamas.libs.spring.dynamicsearch.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagedAggregationExecutorTest {

  @Mock private MongoTemplate mongoTemplate;

  private PagedAggregationExecutor executor;

  // --- Test fixtures ---

  private static final JoinDescriptor COUNTRY_JOIN =
      new JoinDescriptor("countries", "countryId", "_id", "country", true);

  private static final SearchFieldRegistry REGISTRY =
      () ->
          Map.of(
              "name",
              FieldMapping.of("name", String.class, SearchOperation.EQUALS, SearchOperation.LIKE),
              "age",
              FieldMapping.of(
                  "age",
                  Integer.class,
                  SearchOperation.EQUALS,
                  SearchOperation.GREATER_THAN,
                  SearchOperation.LESS_THAN,
                  SearchOperation.IN,
                  SearchOperation.BETWEEN,
                  SearchOperation.NOT_BETWEEN),
              "tags",
              FieldMapping.of(
                  "tags",
                  String.class,
                  SearchOperation.CONTAINS_ALL,
                  SearchOperation.IN,
                  SearchOperation.IS_EMPTY,
                  SearchOperation.IS_NOT_EMPTY,
                  SearchOperation.EXISTS,
                  SearchOperation.DOES_NOT_EXIST),
              "countryName",
              FieldMapping.joined(
                  "country.name",
                  String.class,
                  COUNTRY_JOIN,
                  SearchOperation.EQUALS,
                  SearchOperation.LIKE),
              "status",
              FieldMapping.of(
                  "status",
                  String.class,
                  SearchOperation.EQUALS,
                  SearchOperation.NOT_EQUALS,
                  SearchOperation.IN,
                  SearchOperation.NOT_IN,
                  SearchOperation.IS_NULL,
                  SearchOperation.IS_NOT_NULL,
                  SearchOperation.IS_EMPTY,
                  SearchOperation.IS_NOT_EMPTY,
                  SearchOperation.EXISTS,
                  SearchOperation.DOES_NOT_EXIST),
              "email",
              FieldMapping.of(
                  "email",
                  String.class,
                  SearchOperation.STARTS_WITH,
                  SearchOperation.ENDS_WITH,
                  SearchOperation.REGEX),
              "score",
              FieldMapping.of(
                  "score",
                  Double.class,
                  SearchOperation.GREATER_THAN_OR_EQUAL,
                  SearchOperation.LESS_THAN_OR_EQUAL),
              "createdAt",
              FieldMapping.of(
                  "createdAt",
                  java.time.Instant.class,
                  SearchOperation.GREATER_THAN,
                  SearchOperation.EQUALS,
                  SearchOperation.BETWEEN,
                  SearchOperation.NOT_BETWEEN),
              "birthDate",
              FieldMapping.of(
                  "birthDate",
                  java.time.LocalDate.class,
                  SearchOperation.EQUALS,
                  SearchOperation.LESS_THAN,
                  SearchOperation.BETWEEN));

  /** Concrete facet result type for tests. */
  static class TestPagedResult extends PagedResult<TestEntity> {}

  record TestEntity(String name, int age) {}

  @BeforeEach
  void setUp() {
    executor = new PagedAggregationExecutor(mongoTemplate);
  }

  // ============================
  // executeSearch – paged
  // ============================

  @Nested
  class ExecuteSearchPaged {

    @Test
    void shouldReturnPagedResults_withSimpleCriteria() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Alice")))
              .build();
      Pageable pageable = PageRequest.of(0, 10);

      TestPagedResult facetResult = new TestPagedResult();
      facetResult.setData(List.of(new TestEntity("Alice", 30)));
      facetResult.setTotalCount(List.of(new CountResult(1)));

      mockAggregation(facetResult);

      // When
      var response =
          executor.executeSearch(
              request, REGISTRY, pageable, TestEntity.class, TestPagedResult.class);

      // Then
      assertEquals(1, response.total());
      assertEquals(1, response.data().size());
      assertEquals("Alice", response.data().getFirst().name());
    }

    @Test
    void shouldReturnEmptyResult_whenNoMappedResult() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Nobody")))
              .build();
      Pageable pageable = PageRequest.of(0, 10);

      mockNullAggregation();

      // When
      var response =
          executor.executeSearch(
              request, REGISTRY, pageable, TestEntity.class, TestPagedResult.class);

      // Then
      assertEquals(0, response.total());
      assertTrue(response.data().isEmpty());
    }

    @Test
    void shouldResolveSortFieldFromRegistry() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Bob")))
              .build();
      Pageable pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "age"));

      TestPagedResult facetResult = new TestPagedResult();
      facetResult.setData(List.of(new TestEntity("Bob", 25)));
      facetResult.setTotalCount(List.of(new CountResult(1)));

      mockAggregation(facetResult);

      // When
      var response =
          executor.executeSearch(
              request, REGISTRY, pageable, TestEntity.class, TestPagedResult.class);

      // Then
      assertEquals(1, response.total());
    }

    @Test
    void shouldIncludeJoinOperations_whenCriteriaRequiresJoin() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(
                  List.of(new SearchCriteria("countryName", SearchOperation.EQUALS, "Germany")))
              .build();
      Pageable pageable = PageRequest.of(0, 10);

      TestPagedResult facetResult = new TestPagedResult();
      facetResult.setData(List.of(new TestEntity("Hans", 40)));
      facetResult.setTotalCount(List.of(new CountResult(1)));

      mockAggregation(facetResult);

      // When
      var response =
          executor.executeSearch(
              request, REGISTRY, pageable, TestEntity.class, TestPagedResult.class);

      // Then
      assertEquals(1, response.total());
    }

    @Test
    void shouldIncludeJoinOperations_whenSortRequiresJoin() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Alice")))
              .build();
      Pageable pageable = PageRequest.of(0, 10, Sort.by("countryName"));

      TestPagedResult facetResult = new TestPagedResult();
      facetResult.setData(List.of(new TestEntity("Alice", 30)));
      facetResult.setTotalCount(List.of(new CountResult(1)));

      mockAggregation(facetResult);

      // When
      var response =
          executor.executeSearch(
              request, REGISTRY, pageable, TestEntity.class, TestPagedResult.class);

      // Then
      assertEquals(1, response.total());
    }

    @Test
    void shouldHandleOrOperator() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(
                  List.of(
                      new SearchCriteria("name", SearchOperation.EQUALS, "Alice"),
                      new SearchCriteria("name", SearchOperation.EQUALS, "Bob")))
              .operator(SearchRequest.OR_OPERATOR)
              .build();
      Pageable pageable = PageRequest.of(0, 10);

      TestPagedResult facetResult = new TestPagedResult();
      facetResult.setData(List.of(new TestEntity("Alice", 30), new TestEntity("Bob", 25)));
      facetResult.setTotalCount(List.of(new CountResult(2)));

      mockAggregation(facetResult);

      // When
      var response =
          executor.executeSearch(
              request, REGISTRY, pageable, TestEntity.class, TestPagedResult.class);

      // Then
      assertEquals(2, response.total());
      assertEquals(2, response.data().size());
    }

    @Test
    void shouldHandleNestedGroups() {
      // Given
      SearchGroup innerGroup = new SearchGroup();
      innerGroup.setOperator(SearchRequest.OR_OPERATOR);
      innerGroup.setCriteria(
          List.of(
              new SearchCriteria("name", SearchOperation.EQUALS, "Alice"),
              new SearchCriteria("name", SearchOperation.EQUALS, "Bob")));

      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("age", SearchOperation.GREATER_THAN, 20)))
              .groups(List.of(innerGroup))
              .build();
      Pageable pageable = PageRequest.of(0, 10);

      TestPagedResult facetResult = new TestPagedResult();
      facetResult.setData(List.of(new TestEntity("Alice", 30)));
      facetResult.setTotalCount(List.of(new CountResult(1)));

      mockAggregation(facetResult);

      // When
      var response =
          executor.executeSearch(
              request, REGISTRY, pageable, TestEntity.class, TestPagedResult.class);

      // Then
      assertEquals(1, response.total());
    }
  }

  // ============================
  // executeSearch – unpaged
  // ============================

  @Nested
  class ExecuteSearchUnpaged {

    @Test
    void shouldReturnAllResults_withoutFacet() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.LIKE, "A")))
              .build();
      Pageable pageable = Pageable.unpaged();

      List<TestEntity> allEntities =
          List.of(new TestEntity("Alice", 30), new TestEntity("Adam", 22));

      mockUnpagedAggregation(allEntities);

      // When
      var response =
          executor.executeSearch(
              request, REGISTRY, pageable, TestEntity.class, TestPagedResult.class);

      // Then
      assertEquals(2, response.total());
      assertEquals(2, response.data().size());
    }
  }

  // ============================
  // execute (Criteria-based)
  // ============================

  @Nested
  class ExecuteWithCriteria {

    @Test
    void shouldExecutePagedQuery_withSort() {
      // Given
      Criteria criteria = Criteria.where("name").is("Alice");
      Sort sort = Sort.by(Sort.Direction.ASC, "age");
      Pageable pageable = PageRequest.of(0, 10);

      TestPagedResult facetResult = new TestPagedResult();
      facetResult.setData(List.of(new TestEntity("Alice", 30)));
      facetResult.setTotalCount(List.of(new CountResult(1)));

      mockAggregation(facetResult);

      // When
      var response =
          executor.execute(criteria, pageable, sort, TestEntity.class, TestPagedResult.class);

      // Then
      assertEquals(1, response.total());
      assertEquals("Alice", response.data().getFirst().name());
    }

    @Test
    void shouldExecutePagedQuery_withNullSort() {
      // Given
      Criteria criteria = Criteria.where("name").is("Alice");
      Pageable pageable = PageRequest.of(0, 10);

      TestPagedResult facetResult = new TestPagedResult();
      facetResult.setData(List.of(new TestEntity("Alice", 30)));
      facetResult.setTotalCount(List.of(new CountResult(1)));

      mockAggregation(facetResult);

      // When
      var response =
          executor.execute(criteria, pageable, null, TestEntity.class, TestPagedResult.class);

      // Then
      assertEquals(1, response.total());
    }

    @Test
    void shouldExecutePagedQuery_withUnsortedSort() {
      // Given
      Criteria criteria = Criteria.where("age").gt(18);
      Sort sort = Sort.unsorted();
      Pageable pageable = PageRequest.of(0, 5);

      TestPagedResult facetResult = new TestPagedResult();
      facetResult.setData(List.of(new TestEntity("Bob", 25)));
      facetResult.setTotalCount(List.of(new CountResult(1)));

      mockAggregation(facetResult);

      // When
      var response =
          executor.execute(criteria, pageable, sort, TestEntity.class, TestPagedResult.class);

      // Then
      assertEquals(1, response.total());
    }

    @Test
    void shouldExecuteUnpagedQuery() {
      // Given
      Criteria criteria = Criteria.where("name").regex(".*");
      Pageable pageable = Pageable.unpaged();

      List<TestEntity> allEntities =
          List.of(new TestEntity("Alice", 30), new TestEntity("Bob", 25));

      mockUnpagedAggregation(allEntities);

      // When
      var response =
          executor.execute(criteria, pageable, null, TestEntity.class, TestPagedResult.class);

      // Then
      assertEquals(2, response.total());
      assertEquals(2, response.data().size());
    }
  }

  // ============================
  // buildSearchPipeline
  // ============================

  @Nested
  class BuildSearchPipeline {

    @Test
    void shouldBuildPipeline_withNoJoins() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Alice")))
              .build();
      Pageable pageable = PageRequest.of(0, 10);

      // When
      Aggregation aggregation = executor.buildSearchPipeline(request, REGISTRY, pageable);

      // Then
      assertNotNull(aggregation);
    }

    @Test
    void shouldBuildPipeline_withJoin() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(
                  List.of(new SearchCriteria("countryName", SearchOperation.EQUALS, "Germany")))
              .build();
      Pageable pageable = PageRequest.of(0, 10);

      // When
      Aggregation aggregation = executor.buildSearchPipeline(request, REGISTRY, pageable);

      // Then
      assertNotNull(aggregation);
      // Pipeline should contain: $lookup, $unwind, $match, $facet
      var pipeline = aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT);
      assertTrue(pipeline.size() >= 4);
    }

    @Test
    void shouldBuildPipeline_unpaged_withoutFacet() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Alice")))
              .build();
      Pageable pageable = Pageable.unpaged();

      // When
      Aggregation aggregation = executor.buildSearchPipeline(request, REGISTRY, pageable);

      // Then
      assertNotNull(aggregation);
      // No $facet in pipeline for unpaged
      var pipeline = aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT);
      boolean hasFacet = pipeline.stream().anyMatch(doc -> doc.containsKey("$facet"));
      assertTrue(!hasFacet, "Unpaged pipeline should not contain $facet");
    }

    @Test
    void shouldBuildPipeline_withSortAndJoin() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Alice")))
              .build();
      Pageable pageable = PageRequest.of(0, 10, Sort.by("countryName"));

      // When
      Aggregation aggregation = executor.buildSearchPipeline(request, REGISTRY, pageable);

      // Then
      assertNotNull(aggregation);
      var pipeline = aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT);
      // Should have $lookup for country join triggered by sort field
      boolean hasLookup = pipeline.stream().anyMatch(doc -> doc.containsKey("$lookup"));
      assertTrue(hasLookup, "Pipeline should contain $lookup for sorted joined field");
    }

    @Test
    void shouldPlaceLocalCriteriaBeforeLookupAndJoinedCriteriaAfterLookup_whenRootOperatorIsAnd() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(
                  List.of(
                      new SearchCriteria("name", SearchOperation.EQUALS, "Alice"),
                      new SearchCriteria("countryName", SearchOperation.EQUALS, "Germany")))
              .operator(SearchRequest.AND_OPERATOR)
              .build();
      Pageable pageable = PageRequest.of(0, 10);

      // When
      Aggregation aggregation = executor.buildSearchPipeline(request, REGISTRY, pageable);

      // Then
      assertNotNull(aggregation);
      var pipeline = aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT);

      // Stage 0: Pre-Join $match (local field: name)
      assertTrue(pipeline.get(0).containsKey("$match"));
      org.bson.Document preMatch = (org.bson.Document) pipeline.get(0).get("$match");
      assertEquals("Alice", preMatch.get("name"));

      // Stage 1: $lookup
      assertTrue(pipeline.get(1).containsKey("$lookup"));

      // Stage 2: $unwind
      assertTrue(pipeline.get(2).containsKey("$unwind"));

      // Stage 3: Post-Join $match (joined field: country.name)
      assertTrue(pipeline.get(3).containsKey("$match"));
      org.bson.Document postMatch = (org.bson.Document) pipeline.get(3).get("$match");
      assertEquals("Germany", postMatch.get("country.name"));

      // Stage 4: $facet
      assertTrue(pipeline.get(4).containsKey("$facet"));
    }

    @Test
    void shouldPlaceAllCriteriaAfterLookup_whenRootOperatorIsOr() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(
                  List.of(
                      new SearchCriteria("name", SearchOperation.EQUALS, "Alice"),
                      new SearchCriteria("countryName", SearchOperation.EQUALS, "Germany")))
              .operator(SearchRequest.OR_OPERATOR)
              .build();
      Pageable pageable = PageRequest.of(0, 10);

      // When
      Aggregation aggregation = executor.buildSearchPipeline(request, REGISTRY, pageable);

      // Then
      assertNotNull(aggregation);
      var pipeline = aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT);

      // Stage 0: $lookup (no pre-join match because OR crosses joined fields)
      assertTrue(pipeline.get(0).containsKey("$lookup"));

      // Stage 1: $unwind
      assertTrue(pipeline.get(1).containsKey("$unwind"));

      // Stage 2: Combined Post-Join $match
      assertTrue(pipeline.get(2).containsKey("$match"));
      org.bson.Document matchStage = (org.bson.Document) pipeline.get(2).get("$match");
      assertTrue(matchStage.containsKey("$or"));

      // Stage 3: $facet
      assertTrue(pipeline.get(3).containsKey("$facet"));
    }

    @Test
    void shouldOmitPostJoinMatch_whenAllCriteriaAreLocalAndJoinOnlyForSort() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Alice")))
              .build();
      Pageable pageable = PageRequest.of(0, 10, Sort.by("countryName"));

      // When
      Aggregation aggregation = executor.buildSearchPipeline(request, REGISTRY, pageable);

      // Then
      assertNotNull(aggregation);
      var pipeline = aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT);

      // Stage 0: Pre-Join $match
      assertTrue(pipeline.get(0).containsKey("$match"));
      org.bson.Document preMatch = (org.bson.Document) pipeline.get(0).get("$match");
      assertEquals("Alice", preMatch.get("name"));

      // Stage 1: $lookup
      assertTrue(pipeline.get(1).containsKey("$lookup"));

      // Stage 2: $unwind
      assertTrue(pipeline.get(2).containsKey("$unwind"));

      // Stage 3: $sort
      assertTrue(pipeline.get(3).containsKey("$sort"));

      // Stage 4: $facet
      assertTrue(pipeline.get(4).containsKey("$facet"));

      // Ensure no post-join match was added
      long matchCount = pipeline.stream().filter(doc -> doc.containsKey("$match")).count();
      assertEquals(1, matchCount);
    }

    @Test
    void shouldApplyCustomAggregationOptions_whenProvidedPerRequest() {
      // Given
      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Alice")))
              .build();
      Pageable pageable = PageRequest.of(0, 10);
      AggregationOptions options =
          AggregationOptions.builder()
              .allowDiskUse(true)
              .maxTime(java.time.Duration.ofSeconds(5))
              .build();

      // When
      Aggregation aggregation =
          executor.buildSearchPipeline(request, REGISTRY, pageable, options);

      // Then
      assertNotNull(aggregation);
      assertTrue(aggregation.getOptions().isAllowDiskUse());
      assertEquals(
          java.time.Duration.ofSeconds(5), aggregation.getOptions().getMaxTime());
    }

    @Test
    void shouldApplyDefaultAggregationOptions_whenConfiguredOnExecutor() {
      // Given
      AggregationOptions defaultOptions =
          AggregationOptions.builder().allowDiskUse(true).build();
      PagedAggregationExecutor customExecutor =
          new PagedAggregationExecutor(mongoTemplate, defaultOptions);

      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Alice")))
              .build();
      Pageable pageable = PageRequest.of(0, 10);

      // When
      Aggregation aggregation = customExecutor.buildSearchPipeline(request, REGISTRY, pageable);

      // Then
      assertNotNull(aggregation);
      assertTrue(aggregation.getOptions().isAllowDiskUse());
    }

    @Test
    void shouldOverrideDefaultOptions_withPerRequestOptions() {
      // Given
      AggregationOptions defaultOptions =
          AggregationOptions.builder().allowDiskUse(false).build();
      PagedAggregationExecutor customExecutor =
          new PagedAggregationExecutor(mongoTemplate, defaultOptions);

      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Alice")))
              .build();
      Pageable pageable = PageRequest.of(0, 10);
      AggregationOptions perRequestOptions =
          AggregationOptions.builder().allowDiskUse(true).build();

      // When
      Aggregation aggregation =
          customExecutor.buildSearchPipeline(request, REGISTRY, pageable, perRequestOptions);

      // Then
      assertNotNull(aggregation);
      assertTrue(aggregation.getOptions().isAllowDiskUse());
    }
  }

  // ============================
  // SearchFieldRegistry / FieldMapping
  // ============================

  @Nested
  class SearchFieldRegistryTests {

    @Test
    void shouldResolveKnownField() {
      FieldMapping mapping = REGISTRY.resolve("name");
      assertNotNull(mapping);
      assertEquals("name", mapping.getDocumentField());
      assertEquals(String.class, mapping.getType());
    }

    @Test
    void shouldThrowForUnknownField() {
      assertThrows(InvalidSearchFieldException.class, () -> REGISTRY.resolve("unknownField"));
    }

    @Test
    void shouldIdentifyJoinedField() {
      FieldMapping mapping = REGISTRY.resolve("countryName");
      assertTrue(mapping.isJoined());
      assertEquals(COUNTRY_JOIN, mapping.getJoin());
    }

    @Test
    void shouldIdentifyLocalField() {
      FieldMapping mapping = REGISTRY.resolve("name");
      assertTrue(!mapping.isJoined());
    }
  }

  // ============================
  // SearchCriteriaValidator
  // ============================

  @Nested
  class SearchCriteriaValidatorTests {

    private final SearchCriteriaValidator validator = new SearchCriteriaValidator();

    @Test
    void shouldValidateAndTransformCriteria() {
      SearchCriteria input = new SearchCriteria("name", SearchOperation.EQUALS, "Alice");
      SearchCriteria result = validator.validateAndTransform(input, REGISTRY);

      assertEquals("name", result.getField()); // document field
      assertEquals(SearchOperation.EQUALS, result.getOperation());
      assertEquals("Alice", result.getValue());
    }

    @Test
    void shouldConvertValueToTargetType() {
      // age is Integer type; passing a String "25" should convert
      SearchCriteria input = new SearchCriteria("age", SearchOperation.EQUALS, "25");
      SearchCriteria result = validator.validateAndTransform(input, REGISTRY);

      assertEquals(25, result.getValue());
    }

    @Test
    void shouldConvertCollectionValues() {
      SearchCriteria input =
          new SearchCriteria("age", SearchOperation.IN, List.of("10", "20", "30"));
      SearchCriteria result = validator.validateAndTransform(input, REGISTRY);

      assertTrue(result.getValue() instanceof List);
      @SuppressWarnings("unchecked")
      List<Integer> values = (List<Integer>) result.getValue();
      assertEquals(List.of(10, 20, 30), values);
    }

    @Test
    void shouldHandleNullValue() {
      SearchCriteria input = new SearchCriteria("status", SearchOperation.IS_NULL, null);
      SearchCriteria result = validator.validateAndTransform(input, REGISTRY);

      assertEquals(null, result.getValue());
    }

    @Test
    void shouldThrow_whenOperationNotAllowed() {
      SearchCriteria input = new SearchCriteria("name", SearchOperation.GREATER_THAN, "x");
      assertThrows(
          InvalidSearchOperationException.class,
          () -> validator.validateAndTransform(input, REGISTRY));
    }

    @Test
    void shouldThrow_whenFieldNotFound() {
      SearchCriteria input = new SearchCriteria("unknown", SearchOperation.EQUALS, "x");
      assertThrows(
          InvalidSearchFieldException.class, () -> validator.validateAndTransform(input, REGISTRY));
    }

    @Test
    void shouldMapToDocumentField_forJoinedField() {
      SearchCriteria input = new SearchCriteria("countryName", SearchOperation.EQUALS, "Germany");
      SearchCriteria result = validator.validateAndTransform(input, REGISTRY);

      assertEquals("country.name", result.getField());
    }

    @Test
    void shouldConvertIsoStringToInstant() {
      SearchCriteria input =
          new SearchCriteria("createdAt", SearchOperation.GREATER_THAN, "2026-08-21T20:00:00Z");
      SearchCriteria result = validator.validateAndTransform(input, REGISTRY);

      assertEquals(java.time.Instant.parse("2026-08-21T20:00:00Z"), result.getValue());
    }

    @Test
    void shouldConvertIsoStringToLocalDate() {
      SearchCriteria input =
          new SearchCriteria("birthDate", SearchOperation.EQUALS, "1990-05-15");
      SearchCriteria result = validator.validateAndTransform(input, REGISTRY);

      assertEquals(java.time.LocalDate.of(1990, 5, 15), result.getValue());
    }

    @Test
    void shouldConvertBetweenBoundsToTargetType() {
      SearchCriteria input =
          new SearchCriteria("age", SearchOperation.BETWEEN, List.of("18", "65"));
      SearchCriteria result = validator.validateAndTransform(input, REGISTRY);

      assertEquals(List.of(18, 65), result.getValue());
    }

    @Test
    void shouldConvertBetweenDates() {
      SearchCriteria input =
          new SearchCriteria(
              "birthDate", SearchOperation.BETWEEN, List.of("1990-01-01", "2000-12-31"));
      SearchCriteria result = validator.validateAndTransform(input, REGISTRY);

      assertEquals(
          List.of(
              java.time.LocalDate.of(1990, 1, 1),
              java.time.LocalDate.of(2000, 12, 31)),
          result.getValue());
    }

    @Test
    void shouldThrow_whenBetweenHasFewerThanTwoElements() {
      SearchCriteria input =
          new SearchCriteria("age", SearchOperation.BETWEEN, List.of("18"));
      assertThrows(
          IllegalArgumentException.class,
          () -> validator.validateAndTransform(input, REGISTRY));
    }

    @Test
    void shouldConvertContainsAllCollection() {
      SearchCriteria inputAll =
          new SearchCriteria("tags", SearchOperation.CONTAINS_ALL, List.of("spring", "mongo"));
      SearchCriteria resultAll = validator.validateAndTransform(inputAll, REGISTRY);
      assertEquals(List.of("spring", "mongo"), resultAll.getValue());

      SearchCriteria inputIn =
          new SearchCriteria("tags", SearchOperation.IN, new String[] {"java", "kotlin"});
      SearchCriteria resultIn = validator.validateAndTransform(inputIn, REGISTRY);
      assertEquals(List.of("java", "kotlin"), resultIn.getValue());
    }

    @Test
    void shouldSupportCustomObjectMapper() {
      com.fasterxml.jackson.databind.ObjectMapper customMapper =
          new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
      SearchCriteriaValidator customValidator = new SearchCriteriaValidator(customMapper);

      SearchCriteria input =
          new SearchCriteria("birthDate", SearchOperation.EQUALS, "2000-01-01");
      SearchCriteria result = customValidator.validateAndTransform(input, REGISTRY);

      assertEquals(java.time.LocalDate.of(2000, 1, 1), result.getValue());
    }
  }

  // ============================
  // MongoCriteriaBuilder
  // ============================

  @Nested
  class MongoCriteriaBuilderTests {

    private final MongoCriteriaBuilder builder = new MongoCriteriaBuilder();

    @Test
    void shouldBuildEquals() {
      Criteria c = builder.buildCriteria(new SearchCriteria("name", SearchOperation.EQUALS, "A"));
      assertNotNull(c);
    }

    @Test
    void shouldBuildNotEquals() {
      Criteria c =
          builder.buildCriteria(new SearchCriteria("name", SearchOperation.NOT_EQUALS, "A"));
      assertNotNull(c);
    }

    @Test
    void shouldBuildLike() {
      Criteria c = builder.buildCriteria(new SearchCriteria("name", SearchOperation.LIKE, "ali"));
      assertNotNull(c);
    }

    @Test
    void shouldBuildStartsWith() {
      Criteria c =
          builder.buildCriteria(new SearchCriteria("email", SearchOperation.STARTS_WITH, "a@"));
      assertNotNull(c);
    }

    @Test
    void shouldBuildEndsWith() {
      Criteria c =
          builder.buildCriteria(new SearchCriteria("email", SearchOperation.ENDS_WITH, ".com"));
      assertNotNull(c);
    }

    @Test
    void shouldBuildRegex() {
      Criteria c =
          builder.buildCriteria(new SearchCriteria("email", SearchOperation.REGEX, "test"));
      assertNotNull(c);
    }

    @Test
    void shouldBuildRegex_withoutEscapingRegexSyntax() {
      Criteria c =
          builder.buildCriteria(
              new SearchCriteria("email", SearchOperation.REGEX, "^user_[0-9]+@domain\\.com$"));
      assertNotNull(c);
      org.bson.Document doc = c.getCriteriaObject();
      java.util.regex.Pattern pattern = (java.util.regex.Pattern) doc.get("email");
      assertEquals("^user_[0-9]+@domain\\.com$", pattern.pattern());
      assertTrue((pattern.flags() & java.util.regex.Pattern.CASE_INSENSITIVE) != 0);
    }

    @Test
    void shouldBuildGreaterThan() {
      Criteria c =
          builder.buildCriteria(new SearchCriteria("age", SearchOperation.GREATER_THAN, 18));
      assertNotNull(c);
    }

    @Test
    void shouldBuildLessThan() {
      Criteria c = builder.buildCriteria(new SearchCriteria("age", SearchOperation.LESS_THAN, 65));
      assertNotNull(c);
    }

    @Test
    void shouldBuildGreaterThanOrEqual() {
      Criteria c =
          builder.buildCriteria(
              new SearchCriteria("score", SearchOperation.GREATER_THAN_OR_EQUAL, 5.0));
      assertNotNull(c);
    }

    @Test
    void shouldBuildLessThanOrEqual() {
      Criteria c =
          builder.buildCriteria(
              new SearchCriteria("score", SearchOperation.LESS_THAN_OR_EQUAL, 10.0));
      assertNotNull(c);
    }

    @Test
    void shouldBuildIn() {
      Criteria c =
          builder.buildCriteria(new SearchCriteria("age", SearchOperation.IN, List.of(20, 30, 40)));
      assertNotNull(c);
    }

    @Test
    void shouldBuildIn_withArrayValue() {
      String[] array = new String[] {"A", "B"};
      Criteria c = builder.buildCriteria(new SearchCriteria("name", SearchOperation.IN, array));
      assertNotNull(c);
    }

    @Test
    void shouldBuildNotIn_withSingleValue() {
      Criteria c =
          builder.buildCriteria(new SearchCriteria("name", SearchOperation.NOT_IN, "single"));
      assertNotNull(c);
    }

    @Test
    void shouldBuildNotIn() {
      Criteria c =
          builder.buildCriteria(
              new SearchCriteria("status", SearchOperation.NOT_IN, List.of("INACTIVE")));
      assertNotNull(c);
    }

    @Test
    void shouldBuildIsNull() {
      Criteria c =
          builder.buildCriteria(new SearchCriteria("status", SearchOperation.IS_NULL, null));
      assertNotNull(c);
    }

    @Test
    void shouldBuildIsNotNull() {
      Criteria c =
          builder.buildCriteria(new SearchCriteria("status", SearchOperation.IS_NOT_NULL, null));
      assertNotNull(c);
    }

    @Test
    void shouldBuildBetween() {
      Criteria c =
          builder.buildCriteria(new SearchCriteria("age", SearchOperation.BETWEEN, List.of(18, 65)));
      assertNotNull(c);
      org.bson.Document doc = c.getCriteriaObject();
      org.bson.Document ageDoc = (org.bson.Document) doc.get("age");
      assertEquals(18, ageDoc.get("$gte"));
      assertEquals(65, ageDoc.get("$lte"));
    }

    @Test
    void shouldBuildNotBetween() {
      Criteria c =
          builder.buildCriteria(
              new SearchCriteria("age", SearchOperation.NOT_BETWEEN, List.of(18, 65)));
      assertNotNull(c);
      org.bson.Document doc = c.getCriteriaObject();
      assertTrue(doc.containsKey("$or"));
    }

    @Test
    void shouldBuildExists() {
      Criteria c =
          builder.buildCriteria(new SearchCriteria("status", SearchOperation.EXISTS, null));
      assertNotNull(c);
      org.bson.Document doc = c.getCriteriaObject();
      org.bson.Document statusDoc = (org.bson.Document) doc.get("status");
      assertEquals(true, statusDoc.get("$exists"));
    }

    @Test
    void shouldBuildDoesNotExist() {
      Criteria c =
          builder.buildCriteria(new SearchCriteria("status", SearchOperation.DOES_NOT_EXIST, null));
      assertNotNull(c);
      org.bson.Document doc = c.getCriteriaObject();
      org.bson.Document statusDoc = (org.bson.Document) doc.get("status");
      assertEquals(false, statusDoc.get("$exists"));
    }

    @Test
    void shouldBuildIsEmpty() {
      Criteria c =
          builder.buildCriteria(new SearchCriteria("tags", SearchOperation.IS_EMPTY, null));
      assertNotNull(c);
      org.bson.Document doc = c.getCriteriaObject();
      assertTrue(doc.containsKey("$or"));
    }

    @Test
    void shouldBuildIsNotEmpty() {
      Criteria c =
          builder.buildCriteria(new SearchCriteria("tags", SearchOperation.IS_NOT_EMPTY, null));
      assertNotNull(c);
      org.bson.Document doc = c.getCriteriaObject();
      assertTrue(doc.containsKey("$and"));
    }

    @Test
    void shouldBuildContainsAll() {
      Criteria c =
          builder.buildCriteria(
              new SearchCriteria("tags", SearchOperation.CONTAINS_ALL, List.of("java", "spring")));
      assertNotNull(c);
      org.bson.Document doc = c.getCriteriaObject();
      org.bson.Document tagsDoc = (org.bson.Document) doc.get("tags");
      assertEquals(List.of("java", "spring"), tagsDoc.get("$all"));
    }

    @Test
    void shouldCombineWithAnd() {
      List<Criteria> list = List.of(Criteria.where("a").is(1), Criteria.where("b").is(2));
      Criteria combined = builder.combine(list, SearchRequest.AND_OPERATOR);
      assertNotNull(combined);
    }

    @Test
    void shouldCombineWithOr() {
      List<Criteria> list = List.of(Criteria.where("a").is(1), Criteria.where("b").is(2));
      Criteria combined = builder.combine(list, SearchRequest.OR_OPERATOR);
      assertNotNull(combined);
    }

    @Test
    void shouldReturnEmptyCriteria_whenListIsEmpty() {
      Criteria combined = builder.combine(List.of(), SearchRequest.AND_OPERATOR);
      assertNotNull(combined);
    }

    @Test
    void shouldReturnSingleCriteria_whenListHasOneElement() {
      Criteria single = Criteria.where("a").is(1);
      Criteria combined = builder.combine(List.of(single), SearchRequest.AND_OPERATOR);
      assertEquals(single, combined);
    }
  }

  // ============================
  // MongoQueryBuilder
  // ============================

  @Nested
  class MongoQueryBuilderTests {

    private final MongoQueryBuilder queryBuilder = new MongoQueryBuilder();

    @Test
    void shouldBuildFromFlatCriteria() {
      SearchRequest request =
          SearchRequest.builder()
              .criteria(
                  List.of(
                      new SearchCriteria("name", SearchOperation.EQUALS, "Alice"),
                      new SearchCriteria("age", SearchOperation.GREATER_THAN, 18)))
              .build();

      Criteria result = queryBuilder.build(request, REGISTRY);
      assertNotNull(result);
    }

    @Test
    void shouldBuildFromGroups() {
      SearchGroup group = new SearchGroup();
      group.setOperator(SearchRequest.OR_OPERATOR);
      group.setCriteria(
          List.of(
              new SearchCriteria("name", SearchOperation.EQUALS, "Alice"),
              new SearchCriteria("name", SearchOperation.EQUALS, "Bob")));

      SearchRequest request = SearchRequest.builder().groups(List.of(group)).build();

      Criteria result = queryBuilder.build(request, REGISTRY);
      assertNotNull(result);
    }

    @Test
    void shouldBuildFromNestedGroups() {
      SearchGroup innerGroup = new SearchGroup();
      innerGroup.setOperator(SearchRequest.AND_OPERATOR);
      innerGroup.setCriteria(List.of(new SearchCriteria("age", SearchOperation.GREATER_THAN, 18)));

      SearchGroup outerGroup = new SearchGroup();
      outerGroup.setOperator(SearchRequest.OR_OPERATOR);
      outerGroup.setCriteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Alice")));
      outerGroup.setGroups(List.of(innerGroup));

      SearchRequest request = SearchRequest.builder().groups(List.of(outerGroup)).build();

      Criteria result = queryBuilder.build(request, REGISTRY);
      assertNotNull(result);
    }

    @Test
    void shouldHandleNullCriteriaAndGroups() {
      SearchRequest request = SearchRequest.builder().build();
      Criteria result = queryBuilder.build(request, REGISTRY);
      assertNotNull(result);
    }

    @Test
    void shouldBuildPreJoinCriteria_forLocalCriteriaAndPurelyLocalGroups() {
      SearchGroup localGroup = new SearchGroup();
      localGroup.setOperator(SearchRequest.OR_OPERATOR);
      localGroup.setCriteria(
          List.of(
              new SearchCriteria("name", SearchOperation.EQUALS, "Alice"),
              new SearchCriteria("name", SearchOperation.EQUALS, "Bob")));

      SearchRequest request =
          SearchRequest.builder()
              .criteria(
                  List.of(
                      new SearchCriteria("age", SearchOperation.GREATER_THAN, 18),
                      new SearchCriteria("countryName", SearchOperation.EQUALS, "Germany")))
              .groups(List.of(localGroup))
              .operator(SearchRequest.AND_OPERATOR)
              .build();

      Criteria preJoin = queryBuilder.buildPreJoinCriteria(request, REGISTRY);
      assertNotNull(preJoin);
      org.bson.Document doc = preJoin.getCriteriaObject();
      // Should contain $and with age and the localGroup (name Alice or Bob), but NOT country.name
      assertNotNull(doc);
      String json = doc.toJson();
      assertTrue(json.contains("age"));
      assertTrue(json.contains("Alice"));
      assertTrue(!json.contains("country.name"));
    }

    @Test
    void shouldBuildPostJoinCriteria_forJoinedCriteriaAndMixedGroups() {
      SearchGroup mixedGroup = new SearchGroup();
      mixedGroup.setOperator(SearchRequest.OR_OPERATOR);
      mixedGroup.setCriteria(
          List.of(
              new SearchCriteria("name", SearchOperation.EQUALS, "Alice"),
              new SearchCriteria("countryName", SearchOperation.EQUALS, "Germany")));

      SearchRequest request =
          SearchRequest.builder()
              .criteria(
                  List.of(
                      new SearchCriteria("age", SearchOperation.GREATER_THAN, 18),
                      new SearchCriteria("countryName", SearchOperation.EQUALS, "France")))
              .groups(List.of(mixedGroup))
              .operator(SearchRequest.AND_OPERATOR)
              .build();

      Criteria postJoin = queryBuilder.buildPostJoinCriteria(request, REGISTRY);
      assertNotNull(postJoin);
      org.bson.Document doc = postJoin.getCriteriaObject();
      String json = doc.toJson();
      // Should contain countryName (France) and mixed group, but NOT top-level age
      assertTrue(json.contains("country.name"));
      assertTrue(json.contains("France"));
      assertTrue(json.contains("Alice")); // inside mixed group
    }

    @Test
    void shouldReturnEmptyPreJoinCriteria_whenRootOperatorIsOr() {
      SearchRequest request =
          SearchRequest.builder()
              .criteria(
                  List.of(
                      new SearchCriteria("name", SearchOperation.EQUALS, "Alice"),
                      new SearchCriteria("countryName", SearchOperation.EQUALS, "Germany")))
              .operator(SearchRequest.OR_OPERATOR)
              .build();

      Criteria preJoin = queryBuilder.buildPreJoinCriteria(request, REGISTRY);
      assertTrue(preJoin.getCriteriaObject().isEmpty());

      Criteria postJoin = queryBuilder.buildPostJoinCriteria(request, REGISTRY);
      assertTrue(!postJoin.getCriteriaObject().isEmpty());
    }
  }

  // ============================
  // JoinResolver
  // ============================

  @Nested
  class JoinResolverTests {

    private final JoinResolver joinResolver = new JoinResolver();

    @Test
    void shouldResolveJoinFromCriteria() {
      SearchRequest request =
          SearchRequest.builder()
              .criteria(
                  List.of(new SearchCriteria("countryName", SearchOperation.EQUALS, "Germany")))
              .build();

      Set<JoinDescriptor> joins =
          joinResolver.resolveJoins(request, PageRequest.of(0, 10), REGISTRY);

      assertEquals(1, joins.size());
      assertTrue(joins.contains(COUNTRY_JOIN));
    }

    @Test
    void shouldResolveJoinFromSort() {
      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Alice")))
              .build();
      Pageable pageable = PageRequest.of(0, 10, Sort.by("countryName"));

      Set<JoinDescriptor> joins = joinResolver.resolveJoins(request, pageable, REGISTRY);

      assertEquals(1, joins.size());
      assertTrue(joins.contains(COUNTRY_JOIN));
    }

    @Test
    void shouldResolveNoJoins_whenLocalFieldsOnly() {
      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Alice")))
              .build();

      Set<JoinDescriptor> joins =
          joinResolver.resolveJoins(request, PageRequest.of(0, 10), REGISTRY);

      assertTrue(joins.isEmpty());
    }

    @Test
    void shouldResolveJoinsFromGroups() {
      SearchGroup group = new SearchGroup();
      group.setCriteria(
          List.of(new SearchCriteria("countryName", SearchOperation.EQUALS, "France")));

      SearchRequest request = SearchRequest.builder().groups(List.of(group)).build();

      Set<JoinDescriptor> joins =
          joinResolver.resolveJoins(request, PageRequest.of(0, 10), REGISTRY);

      assertEquals(1, joins.size());
      assertTrue(joins.contains(COUNTRY_JOIN));
    }

    @Test
    void shouldDeduplicateJoins() {
      SearchRequest request =
          SearchRequest.builder()
              .criteria(
                  List.of(
                      new SearchCriteria("countryName", SearchOperation.EQUALS, "Germany"),
                      new SearchCriteria("countryName", SearchOperation.LIKE, "Ger")))
              .build();
      Pageable pageable = PageRequest.of(0, 10, Sort.by("countryName"));

      Set<JoinDescriptor> joins = joinResolver.resolveJoins(request, pageable, REGISTRY);

      assertEquals(1, joins.size());
    }

    @Test
    void shouldHandleNullCriteriaAndGroups() {
      SearchRequest request = SearchRequest.builder().build();

      Set<JoinDescriptor> joins =
          joinResolver.resolveJoins(request, PageRequest.of(0, 10), REGISTRY);

      assertTrue(joins.isEmpty());
    }

    @Test
    void shouldHandleUnsortedPageable() {
      SearchRequest request =
          SearchRequest.builder()
              .criteria(List.of(new SearchCriteria("name", SearchOperation.EQUALS, "Alice")))
              .build();

      Set<JoinDescriptor> joins = joinResolver.resolveJoins(request, Pageable.unpaged(), REGISTRY);

      assertTrue(joins.isEmpty());
    }
  }

  // ============================
  // PagedResult
  // ============================

  @Nested
  class PagedResultTests {

    @Test
    void shouldReturnZero_whenTotalCountIsEmpty() {
      PagedResult<String> result = new PagedResult<>();
      assertEquals(0, result.getTotal());
    }

    @Test
    void shouldReturnTotal_fromFirstCountResult() {
      PagedResult<String> result = new PagedResult<>();
      result.setTotalCount(List.of(new CountResult(42)));
      assertEquals(42, result.getTotal());
    }
  }

  // ============================
  // Helper methods
  // ============================

  @SuppressWarnings("unchecked")
  private void mockAggregation(TestPagedResult facetResult) {
    when(mongoTemplate.getCollectionName(TestEntity.class)).thenReturn("testEntities");
    AggregationResults<TestPagedResult> aggResults =
        (AggregationResults<TestPagedResult>) org.mockito.Mockito.mock(AggregationResults.class);
    when(aggResults.getUniqueMappedResult()).thenReturn(facetResult);
    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq("testEntities"), eq(TestPagedResult.class)))
        .thenReturn(aggResults);
  }

  @SuppressWarnings("unchecked")
  private void mockNullAggregation() {
    when(mongoTemplate.getCollectionName(TestEntity.class)).thenReturn("testEntities");
    AggregationResults<TestPagedResult> aggResults =
        (AggregationResults<TestPagedResult>) org.mockito.Mockito.mock(AggregationResults.class);
    when(aggResults.getUniqueMappedResult()).thenReturn(null);
    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq("testEntities"), eq(TestPagedResult.class)))
        .thenReturn(aggResults);
  }

  @SuppressWarnings("unchecked")
  private void mockUnpagedAggregation(List<TestEntity> entities) {
    when(mongoTemplate.getCollectionName(TestEntity.class)).thenReturn("testEntities");
    AggregationResults<TestEntity> aggResults =
        (AggregationResults<TestEntity>) org.mockito.Mockito.mock(AggregationResults.class);
    when(aggResults.getMappedResults()).thenReturn(entities);
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("testEntities"), eq(TestEntity.class)))
        .thenReturn(aggResults);
  }
}
