package com.bereczkitamas.libs.spring.dynamicsearch.it;

import static org.junit.jupiter.api.Assertions.*;

import com.bereczkitamas.libs.spring.dynamicsearch.data.*;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.Asset;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.Order;
import java.time.Instant;
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
        new JoinDescriptor("users", "customer", "_id", "customer", true);
    JoinDescriptor assetsJoin =
        new JoinDescriptor("assets", "assets", "_id", "assets", false);

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
                FieldMapping.joined(
                    "customer.name",
                    String.class,
                    userJoin,
                    SearchOperation.EQUALS,
                    SearchOperation.LIKE,
                    SearchOperation.STARTS_WITH))
            .register(
                "customerEmail",
                FieldMapping.joined(
                    "customer.email",
                    String.class,
                    userJoin,
                    SearchOperation.EQUALS,
                    SearchOperation.ENDS_WITH))
            .register(
                "customerCity",
                FieldMapping.joined(
                    "customer.address.city",
                    String.class,
                    userJoin,
                    SearchOperation.EQUALS,
                    SearchOperation.IN))
            .register(
                "customerLastLoggedIn",
                FieldMapping.joined(
                    "customer.lastLoggedIn",
                    Instant.class,
                    userJoin,
                    SearchOperation.GREATER_THAN,
                    SearchOperation.LESS_THAN,
                    SearchOperation.BETWEEN))
            .register(
                "assets",
                FieldMapping.joinedArray(
                    "assets",
                    assetsJoin,
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
            .build();
  }

  @Test
  @DisplayName("Simple local field EQUALS search on status")
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
  @DisplayName("Joined User search on customer.address.city via DocumentReference $lookup on users")
  void testJoinedCustomerCitySearch() {
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
  @DisplayName("Joined Array ELEM_MATCH search on assets via DocumentReference $lookup on assets collection")
  void testJoinedArrayElemMatchSearch() {
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
    assertNotNull(response.data().getFirst().getAssets());
    assertTrue(response.data().getFirst().getAssets().stream().anyMatch(a -> "MacBook Pro 16".equals(a.getName())));
  }

  @Test
  @DisplayName("Joined Array SIZE search on assets count")
  void testJoinedArraySizeSearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("assets", SearchOperation.SIZE, 1)
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(2, response.total()); // ORD-1003 (Monitor) and ORD-1005 (Keyboard)
  }

  @Test
  @DisplayName("Joined field search using $lookup on users collection by customer name")
  void testJoinedUserNameSearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("customerName", SearchOperation.EQUALS, "Bob Jones")
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(1, response.total());
    assertEquals("ORD-1002", response.data().getFirst().getOrderNumber());
    assertEquals("Bob Jones", response.data().getFirst().getCustomer().getName());
  }

  @Test
  @DisplayName("Search with pagination and sorting by joined field (customerName)")
  void testSearchWithPaginationAndSorting() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("totalAmount", SearchOperation.GREATER_THAN, 100.0)
            .build();

    // Sort by joined customer name descending
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
