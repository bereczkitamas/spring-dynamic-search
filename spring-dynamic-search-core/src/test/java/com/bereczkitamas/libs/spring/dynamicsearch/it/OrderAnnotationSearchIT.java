package com.bereczkitamas.libs.spring.dynamicsearch.it;

import static org.junit.jupiter.api.Assertions.*;

import com.bereczkitamas.libs.spring.dynamicsearch.data.*;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.Order;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.OrderDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class OrderAnnotationSearchIT extends AbstractMongoIntegrationTest {

  private SearchFieldRegistry registry;

  @BeforeEach
  void setUpAnnotationRegistry() {
    registry = SimpleSearchFieldRegistry.from(OrderDto.class);
  }

  @Test
  @DisplayName("Verify annotation scanner extracted all expected field mappings from OrderDto")
  void testAnnotationScannerMappings() {
    assertNotNull(registry.resolve("orderNumber"));
    assertNotNull(registry.resolve("status"));
    assertNotNull(registry.resolve("totalAmount"));
    assertNotNull(registry.resolve("orderDate"));
    assertNotNull(registry.resolve("customerName"));
    assertNotNull(registry.resolve("customerCity"));
    assertNotNull(registry.resolve("customerLastLoggedIn"));
    assertNotNull(registry.resolve("assets"));
    assertNotNull(registry.resolve("channel"));
    assertNotNull(registry.resolve("priority"));
    assertNotNull(registry.resolve("couponCode"));
    assertNotNull(registry.resolve("joinedCustomerName"));
    assertNotNull(registry.resolve("joinedCustomerEmail"));

    // Verify documentField mappings
    assertEquals("customer.name", registry.resolve("customerName").getDocumentField());
    assertEquals("customer.address.city", registry.resolve("customerCity").getDocumentField());
    assertEquals("attributes.channel", registry.resolve("channel").getDocumentField());
    assertEquals("joinedCustomer.name", registry.resolve("joinedCustomerName").getDocumentField());
    assertTrue(registry.resolve("joinedCustomerName").isJoined());
  }

  @Test
  @DisplayName("Search by orderNumber using LIKE and STARTS_WITH on scanned mappings")
  void testOrderNumberSearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("orderNumber", SearchOperation.STARTS_WITH, "ORD-100")
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(5, response.total());
  }

  @Test
  @DisplayName("Search by nested customer fields scanned from annotations")
  void testNestedCustomerSearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("customerName", SearchOperation.LIKE, "Alice")
            .where("customerCity", SearchOperation.EQUALS, "Budapest")
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(2, response.total());
    assertTrue(
        response.data().stream()
            .allMatch(o -> "Alice Smith".equals(o.getCustomer().getName())));
  }

  @Test
  @DisplayName("Search by Map dynamic attributes scanned from annotations")
  void testMapAttributesSearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("channel", SearchOperation.EQUALS, "mobile")
            .where("priority", SearchOperation.EQUALS, "high")
            .where("couponCode", SearchOperation.EQUALS, "APPLE10")
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(1, response.total());
    assertEquals("ORD-1004", response.data().getFirst().getOrderNumber());
  }

  @Test
  @DisplayName("Search subdocument array assets using ELEM_MATCH on scanned elementClass")
  void testAssetsElemMatchSearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .elemMatch(
                "assets",
                "AND",
                elem ->
                    elem.where("category", SearchOperation.EQUALS, "SERVER")
                        .where("price", SearchOperation.GREATER_THAN_OR_EQUAL, 3000.0))
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(1, response.total());
    assertEquals("ORD-1002", response.data().getFirst().getOrderNumber());
  }

  @Test
  @DisplayName("Search by date ranges on orderDate and customerLastLoggedIn")
  void testDateRangesSearch() {
    Instant start = Instant.parse("2026-01-01T00:00:00Z");
    Instant end = Instant.parse("2026-01-31T23:59:59Z");

    SearchRequest request =
        SearchRequestBuilder.search()
            .where("orderDate", SearchOperation.BETWEEN, List.of(start, end))
            .where("customerLastLoggedIn", SearchOperation.GREATER_THAN, Instant.parse("2026-02-01T00:00:00Z"))
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    // ORD-1001 (Alice: placed Jan 10, logged in Feb 28) and ORD-1002 (Bob: placed Jan 15, logged in Feb 15)
    assertEquals(2, response.total());
  }

  @Test
  @DisplayName("Search across joined users collection scanned via @JoinedField")
  void testJoinedUserSearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("joinedCustomerEmail", SearchOperation.EQUALS, "diana@example.com")
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(1, response.total());
    assertEquals("ORD-1004", response.data().getFirst().getOrderNumber());
  }

  @Test
  @DisplayName("Search with projection and sorting on scanned properties")
  void testProjectionAndSorting() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("status", SearchOperation.EQUALS, "COMPLETED")
            .projection(ProjectionRequest.builder().include(java.util.Set.of("orderNumber", "totalAmount")).build())
            .build();

    PageRequest pageable =
        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "totalAmount"));

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, pageable, Order.class);

    assertEquals(2, response.total());
    // ORD-1004 ($2100) > ORD-1001 ($1250)
    assertEquals("ORD-1004", response.data().get(0).getOrderNumber());
    assertEquals("ORD-1001", response.data().get(1).getOrderNumber());
  }
}
