package com.bereczkitamas.libs.spring.dynamicsearch.ai;

import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchToolRequest;
import com.bereczkitamas.libs.spring.dynamicsearch.ai.model.SearchToolResult;
import com.bereczkitamas.libs.spring.dynamicsearch.data.FieldMapping;
import com.bereczkitamas.libs.spring.dynamicsearch.data.PagedAggregationExecutor;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchCriteria;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchFieldRegistry;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchOperation;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchRequest;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SimpleSearchFieldRegistry;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicSearchAiToolTest {

  @Mock
  private MongoTemplate mongoTemplate;

  private PagedAggregationExecutor executor;
  private SearchFieldRegistry registry;
  private DynamicSearchAiTool<TestUser> aiTool;

  record TestUser(String username, int age) {}

  @BeforeEach
  void setUp() {
    executor = new PagedAggregationExecutor(mongoTemplate);
    registry =
        SimpleSearchFieldRegistry.of(
            Map.of(
                "username", FieldMapping.of("username", String.class, SearchOperation.EQUALS, SearchOperation.LIKE),
                "age", FieldMapping.of("age", Integer.class, SearchOperation.EQUALS, SearchOperation.GREATER_THAN)));

    aiTool = new DynamicSearchAiTool<>(executor, registry, TestUser.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldExecuteSearchSuccessfully() {
    Document doc = new Document("username", "alice").append("age", 30);
    Document facetDoc =
        new Document(PagedAggregationExecutor.FIELD_DATA, List.of(doc))
            .append(PagedAggregationExecutor.FIELD_TOTAL_COUNT, List.of(new Document("total", 1L)));

    AggregationResults<Document> results = Mockito.mock(AggregationResults.class);
    when(results.getUniqueMappedResult()).thenReturn(facetDoc);

    when(mongoTemplate.getCollectionName(TestUser.class)).thenReturn("testUser");
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("testUser"), eq(Document.class)))
        .thenReturn(results);

    SearchToolRequest toolRequest =
        SearchToolRequest.builder()
            .searchRequest(
                SearchRequest.builder()
                    .criteria(List.of(new SearchCriteria("username", SearchOperation.EQUALS, "alice")))
                    .build())
            .page(0)
            .size(10)
            .build();

    SearchToolResult<TestUser> result = aiTool.execute(toolRequest);

    assertTrue(result.isSuccess());
    assertEquals(1, result.getTotalElements());
    assertEquals(1, result.getItems().size());
    assertEquals("alice", result.getItems().getFirst().username());
  }

  @Test
  void shouldReturnStructuredError_whenInvalidFieldSupplied() {
    SearchToolRequest toolRequest =
        SearchToolRequest.builder()
            .searchRequest(
                SearchRequest.builder()
                    .criteria(List.of(new SearchCriteria("user_name", SearchOperation.EQUALS, "alice")))
                    .build())
            .build();

    SearchToolResult<TestUser> result = aiTool.execute(toolRequest);

    assertFalse(result.isSuccess());
    assertNotNull(result.getError());
    assertEquals("user_name", result.getError().getInvalidField());
    assertEquals("username", result.getError().getSuggestedField());
    assertTrue(result.getError().getRepairGuidance().contains("Did you mean 'username'?"));
  }
}
