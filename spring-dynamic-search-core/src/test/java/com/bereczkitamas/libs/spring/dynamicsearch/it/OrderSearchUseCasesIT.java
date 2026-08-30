package com.bereczkitamas.libs.spring.dynamicsearch.it;

import static org.junit.jupiter.api.Assertions.*;

import com.bereczkitamas.libs.spring.dynamicsearch.data.*;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.Order;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.OrderDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class OrderSearchUseCasesIT extends AbstractMongoIntegrationTest {

  private SearchFieldRegistry registry;

  @BeforeEach
  void setUpRegistry() {
    registry = SimpleSearchFieldRegistry.from(OrderDto.class);
  }

  @Nested
  @DisplayName("1. String Search Operations")
  class StringOperations {

    @Test
    @DisplayName("EQUALS and NOT_EQUALS on status")
    void testEqualsAndNotEquals() {
      SearchRequest reqEquals =
          SearchRequestBuilder.search().where("status", SearchOperation.EQUALS, "PROCESSING").build();
      PagedSearchResponse<Order> resEquals =
          executor.executeSearch(reqEquals, registry, Pageable.unpaged(), Order.class);
      assertEquals(1, resEquals.total());
      assertEquals("ORD-1002", resEquals.data().getFirst().getOrderNumber());

      SearchRequest reqNotEquals =
          SearchRequestBuilder.search().where("status", SearchOperation.NOT_EQUALS, "COMPLETED").build();
      PagedSearchResponse<Order> resNotEquals =
          executor.executeSearch(reqNotEquals, registry, Pageable.unpaged(), Order.class);
      assertEquals(3, resNotEquals.total()); // PROCESSING, NEW, CANCELLED
    }

    @Test
    @DisplayName("LIKE substring match on customerName")
    void testLikeSubstring() {
      SearchRequest request =
          SearchRequestBuilder.search().where("customerName", SearchOperation.LIKE, "smith").build();
      PagedSearchResponse<Order> res =
          executor.executeSearch(request, registry, Pageable.unpaged(), Order.class);
      assertEquals(2, res.total());
    }

    @Test
    @DisplayName("STARTS_WITH and ENDS_WITH on orderNumber and email")
    void testStartsEndsWith() {
      SearchRequest startsReq =
          SearchRequestBuilder.search().where("orderNumber", SearchOperation.STARTS_WITH, "ORD-100").build();
      PagedSearchResponse<Order> startsRes =
          executor.executeSearch(startsReq, registry, Pageable.unpaged(), Order.class);
      assertEquals(5, startsRes.total());

      SearchRequest endsReq =
          SearchRequestBuilder.search().where("customerEmail", SearchOperation.ENDS_WITH, "@example.com").build();
      PagedSearchResponse<Order> endsRes =
          executor.executeSearch(endsReq, registry, Pageable.unpaged(), Order.class);
      assertEquals(5, endsRes.total());
    }

    @Test
    @DisplayName("REGEX pattern matching on orderNumber")
    void testRegex() {
      SearchRequest request =
          SearchRequestBuilder.search().where("orderNumber", SearchOperation.REGEX, "^ORD-100[135]$").build();
      PagedSearchResponse<Order> res =
          executor.executeSearch(request, registry, Pageable.unpaged(), Order.class);
      assertEquals(3, res.total()); // 1001, 1003, 1005
    }
  }

  @Nested
  @DisplayName("2. Numeric & Range Operations")
  class NumericOperations {

    @Test
    @DisplayName("GREATER_THAN, LESS_THAN on totalAmount")
    void testNumericComparisons() {
      SearchRequest gtReq =
          SearchRequestBuilder.search().where("totalAmount", SearchOperation.GREATER_THAN, 2000.0).build();
      PagedSearchResponse<Order> gtRes =
          executor.executeSearch(gtReq, registry, Pageable.unpaged(), Order.class);
      assertEquals(2, gtRes.total()); // ORD-1002 (3400) and ORD-1004 (2100)

      SearchRequest ltReq =
          SearchRequestBuilder.search().where("totalAmount", SearchOperation.LESS_THAN, 500.0).build();
      PagedSearchResponse<Order> ltRes =
          executor.executeSearch(ltReq, registry, Pageable.unpaged(), Order.class);
      assertEquals(2, ltRes.total()); // ORD-1003 (450) and ORD-1005 (80)
    }

    @Test
    @DisplayName("BETWEEN and NOT_BETWEEN on totalAmount")
    void testBetweenAndNotBetween() {
      SearchRequest betweenReq =
          SearchRequestBuilder.search()
              .where("totalAmount", SearchOperation.BETWEEN, List.of(400.0, 2500.0))
              .build();
      PagedSearchResponse<Order> betweenRes =
          executor.executeSearch(betweenReq, registry, Pageable.unpaged(), Order.class);
      assertEquals(3, betweenRes.total()); // 450, 1250, 2100

      SearchRequest notBetweenReq =
          SearchRequestBuilder.search()
              .where("totalAmount", SearchOperation.NOT_BETWEEN, List.of(400.0, 2500.0))
              .build();
      PagedSearchResponse<Order> notBetweenRes =
          executor.executeSearch(notBetweenReq, registry, Pageable.unpaged(), Order.class);
      assertEquals(2, notBetweenRes.total()); // 80 and 3400
    }
  }

  @Nested
  @DisplayName("3. Date and Time Operations")
  class DateTimeOperations {

    @Test
    @DisplayName("BETWEEN date ranges on orderDate")
    void testOrderDateRange() {
      Instant start = Instant.parse("2026-02-01T00:00:00Z");
      Instant end = Instant.parse("2026-02-28T23:59:59Z");

      SearchRequest request =
          SearchRequestBuilder.search()
              .where("orderDate", SearchOperation.BETWEEN, List.of(start, end))
              .build();

      PagedSearchResponse<Order> res =
          executor.executeSearch(request, registry, Pageable.unpaged(), Order.class);

      assertEquals(3, res.total()); // ORD-1003, ORD-1004, ORD-1005 placed in Feb 2026
    }
  }

  @Nested
  @DisplayName("4. Set & Membership Operations")
  class SetOperations {

    @Test
    @DisplayName("IN and NOT_IN on status")
    void testInAndNotIn() {
      SearchRequest inReq =
          SearchRequestBuilder.search()
              .where("status", SearchOperation.IN, List.of("NEW", "PROCESSING"))
              .build();
      PagedSearchResponse<Order> inRes =
          executor.executeSearch(inReq, registry, Pageable.unpaged(), Order.class);
      assertEquals(2, inRes.total());

      SearchRequest notInReq =
          SearchRequestBuilder.search()
              .where("status", SearchOperation.NOT_IN, List.of("COMPLETED", "CANCELLED"))
              .build();
      PagedSearchResponse<Order> notInRes =
          executor.executeSearch(notInReq, registry, Pageable.unpaged(), Order.class);
      assertEquals(2, notInRes.total());
    }
  }

  @Nested
  @DisplayName("5. Array Subdocument Operations (elemMatch & size)")
  class ArrayOperations {

    @Test
    @DisplayName("ELEM_MATCH with compound AND conditions")
    void testElemMatchCompoundAnd() {
      SearchRequest request =
          SearchRequestBuilder.search()
              .elemMatch(
                "assets",
                "AND",
                elem ->
                    elem.where("category", SearchOperation.EQUALS, "ACCESSORY")
                        .where("price", SearchOperation.LESS_THAN_OR_EQUAL, 50.0))
              .build();

      PagedSearchResponse<Order> res =
          executor.executeSearch(request, registry, Pageable.unpaged(), Order.class);

      assertEquals(1, res.total()); // ORD-1001 (Mouse $50)
      assertEquals("ORD-1001", res.data().getFirst().getOrderNumber());
    }

    @Test
    @DisplayName("ELEM_MATCH with compound OR conditions")
    void testElemMatchCompoundOr() {
      SearchRequest request =
          SearchRequestBuilder.search()
              .elemMatch(
                "assets",
                "OR",
                elem ->
                    elem.where("category", SearchOperation.EQUALS, "SERVER")
                        .where("price", SearchOperation.GREATER_THAN, 1800.0))
              .build();

      PagedSearchResponse<Order> res =
          executor.executeSearch(request, registry, Pageable.unpaged(), Order.class);

      // ORD-1002 (Server $3000) and ORD-1004 (MacBook Pro $2000)
      assertEquals(2, res.total());
    }

    @Test
    @DisplayName("SIZE operator on assets array")
    void testArraySize() {
      SearchRequest request =
          SearchRequestBuilder.search().where("assets", SearchOperation.SIZE, 2).build();

      PagedSearchResponse<Order> res =
          executor.executeSearch(request, registry, Pageable.unpaged(), Order.class);

      assertEquals(3, res.total()); // ORD-1001, ORD-1002, ORD-1004 have 2 assets
    }
  }

  @Nested
  @DisplayName("6. Map & Dynamic Attribute Operations")
  class MapOperations {

    @Test
    @DisplayName("Direct filtering on dynamic map attributes")
    void testMapAttributeFilters() {
      SearchRequest reqWeb =
          SearchRequestBuilder.search().where("channel", SearchOperation.EQUALS, "web").build();
      PagedSearchResponse<Order> resWeb =
          executor.executeSearch(reqWeb, registry, Pageable.unpaged(), Order.class);
      assertEquals(2, resWeb.total());

      SearchRequest reqHigh =
          SearchRequestBuilder.search().where("priority", SearchOperation.EQUALS, "high").build();
      PagedSearchResponse<Order> resHigh =
          executor.executeSearch(reqHigh, registry, Pageable.unpaged(), Order.class);
      assertEquals(2, resHigh.total());
    }
  }

  @Nested
  @DisplayName("7. Complex Boolean Logic & Group Nesting")
  class ComplexBooleanLogic {

    @Test
    @DisplayName("Top-level AND with nested OR groups: (status == COMPLETED AND totalAmount > 2000) OR (priority == urgent)")
    void testNestedBooleanGroups() {
      SearchRequest request =
          SearchRequestBuilder.search()
              .or()
              .group(
                  g ->
                      g.and()
                          .where("status", SearchOperation.EQUALS, "COMPLETED")
                          .where("totalAmount", SearchOperation.GREATER_THAN, 2000.0))
              .group(
                  g ->
                      g.where("priority", SearchOperation.EQUALS, "urgent"))
              .build();

      PagedSearchResponse<Order> res =
          executor.executeSearch(request, registry, Pageable.unpaged(), Order.class);

      // ORD-1004 (COMPLETED & 2100) and ORD-1002 (urgent priority)
      assertEquals(2, res.total());
    }
  }

  @Nested
  @DisplayName("8. Pagination and Multi-Field Sorting")
  class PaginationAndSorting {

    @Test
    @DisplayName("Paging across all 5 orders with page size 2")
    void testPaginationTraversal() {
      SearchRequest request = SearchRequestBuilder.search().build();

      // Page 0
      PagedSearchResponse<Order> page0 =
          executor.executeSearch(
              request, registry, PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "orderNumber")), Order.class);
      assertEquals(5, page0.total());
      assertEquals(2, page0.data().size());
      assertEquals("ORD-1001", page0.data().get(0).getOrderNumber());
      assertEquals("ORD-1002", page0.data().get(1).getOrderNumber());

      // Page 1
      PagedSearchResponse<Order> page1 =
          executor.executeSearch(
              request, registry, PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "orderNumber")), Order.class);
      assertEquals(5, page1.total());
      assertEquals(2, page1.data().size());
      assertEquals("ORD-1003", page1.data().get(0).getOrderNumber());
      assertEquals("ORD-1004", page1.data().get(1).getOrderNumber());

      // Page 2
      PagedSearchResponse<Order> page2 =
          executor.executeSearch(
              request, registry, PageRequest.of(2, 2, Sort.by(Sort.Direction.ASC, "orderNumber")), Order.class);
      assertEquals(5, page2.total());
      assertEquals(1, page2.data().size());
      assertEquals("ORD-1005", page2.data().get(0).getOrderNumber());
    }
  }
}
