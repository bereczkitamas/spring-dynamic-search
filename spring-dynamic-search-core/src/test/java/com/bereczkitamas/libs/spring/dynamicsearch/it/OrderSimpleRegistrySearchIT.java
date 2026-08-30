package com.bereczkitamas.libs.spring.dynamicsearch.it;

import static org.junit.jupiter.api.Assertions.*;

import com.bereczkitamas.libs.spring.dynamicsearch.data.*;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.Asset;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.Order;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class OrderSimpleRegistrySearchIT extends AbstractMongoIntegrationTest {

  private SearchFieldRegistry registry;

  @BeforeEach
  void setUpRegistry() {
    JoinDescriptor userJoin =
        new JoinDescriptor("users", "customerId", "_id", "joinedCustomer", true);

    registry =
        SimpleSearchFieldRegistry.builder()
            .register(
                "orderNumber",
                FieldMapping.of(
                    "orderNumber",
                    String.class,
                    SearchOperation.EQUALS,
                    SearchOperation.LIKE,
                    SearchOperation.STARTS_WITH,
                    SearchOperation.IN))
            .register(
                "status",
                FieldMapping.of(
                    "status",
                    String.class,
                    SearchOperation.EQUALS,
                    SearchOperation.NOT_EQUALS,
                    SearchOperation.IN,
                    SearchOperation.NOT_IN))
            .register(
                "totalAmount",
                FieldMapping.of(
                    "totalAmount",
                    Double.class,
                    SearchOperation.EQUALS,
                    SearchOperation.GREATER_THAN,
                    SearchOperation.LESS_THAN,
                    SearchOperation.GREATER_THAN_OR_EQUAL,
                    SearchOperation.LESS_THAN_OR_EQUAL,
                    SearchOperation.BETWEEN))
            .register(
                "orderDate",
                FieldMapping.of(
                    "orderDate",
                    Instant.class,
                    SearchOperation.GREATER_THAN,
                    SearchOperation.LESS_THAN,
                    SearchOperation.BETWEEN))
            .register(
                "customerName",
                FieldMapping.of(
                    "customer.name",
                    String.class,
                    SearchOperation.EQUALS,
                    SearchOperation.LIKE,
                    SearchOperation.STARTS_WITH))
            .register(
                "customerEmail",
                FieldMapping.of(
                    "customer.email",
                    String.class,
                    SearchOperation.EQUALS,
                    SearchOperation.ENDS_WITH))
            .register(
                "customerCity",
                FieldMapping.of(
                    "customer.address.city",
                    String.class,
                    SearchOperation.EQUALS,
                    SearchOperation.IN))
            .register(
                "assets",
                FieldMapping.arrayField(
                    "assets",
                    Asset.class,
                    SearchOperation.ELEM_MATCH,
                    SearchOperation.SIZE))
            .register(
                "channel",
                FieldMapping.of(
                    "attributes.channel",
                    String.class,
                    SearchOperation.EQUALS,
                    SearchOperation.IN))
            .register(
                "priority",
                FieldMapping.of(
                    "attributes.priority",
                    String.class,
                    SearchOperation.EQUALS,
                    SearchOperation.IN))
            .register(
                "joinedCustomerName",
                FieldMapping.joined(
                    "joinedCustomer.name",
                    String.class,
                    userJoin,
                    SearchOperation.EQUALS,
                    SearchOperation.LIKE))
            .build();
  }

  @Test
  @DisplayName("Simple field EQUALS search on status")
  void testSimpleFieldEqualsSearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("status", SearchOperation.EQUALS, "COMPLETED")
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertNotNull(response);
    assertEquals(2, response.total());
    assertEquals(2, response.data().size());
    assertTrue(response.data().stream().allMatch(o -> "COMPLETED".equals(o.getStatus())));
  }

  @Test
  @DisplayName("Nested object path search on customer.address.city")
  void testNestedCustomerCitySearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("customerCity", SearchOperation.EQUALS, "Budapest")
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(2, response.total());
    assertTrue(
        response.data().stream()
            .allMatch(o -> "Budapest".equals(o.getCustomer().getAddress().getCity())));
  }

  @Test
  @DisplayName("Dynamic Map attribute search on attributes.channel")
  void testMapAttributeSearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("channel", SearchOperation.EQUALS, "web")
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(2, response.total());
    assertTrue(
        response.data().stream()
            .allMatch(o -> "web".equals(o.getAttributes().get("channel"))));
  }

  @Test
  @DisplayName("Array ELEM_MATCH search on assets line items")
  void testArrayElemMatchSearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .elemMatch(
                "assets",
                "AND",
                elem ->
                    elem.where("category", SearchOperation.EQUALS, "LAPTOP")
                        .where("price", SearchOperation.GREATER_THAN, 1500.0))
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(1, response.total());
    assertEquals("ORD-1004", response.data().getFirst().getOrderNumber());
  }

  @Test
  @DisplayName("Array SIZE search on assets count")
  void testArraySizeSearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("assets", SearchOperation.SIZE, 1)
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(2, response.total()); // ORD-1003 (Monitor) and ORD-1005 (Keyboard)
  }

  @Test
  @DisplayName("Joined field search using $lookup on users collection")
  void testJoinedUserSearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("joinedCustomerName", SearchOperation.EQUALS, "Bob Jones")
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(1, response.total());
    assertEquals("ORD-1002", response.data().getFirst().getOrderNumber());
  }

  @Test
  @DisplayName("Search with pagination and sorting by nested field")
  void testSearchWithPaginationAndSorting() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("totalAmount", SearchOperation.GREATER_THAN, 100.0)
            .build();

    // Sort by customer name descending
    PageRequest pageable =
        PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "customerName"));

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, pageable, Order.class);

    assertEquals(4, response.total());
    assertEquals(2, response.data().size());
    // Diana Prince > Charlie Brown > Bob Jones > Alice Smith
    assertEquals("Diana Prince", response.data().getFirst().getCustomer().getName());
  }
}
