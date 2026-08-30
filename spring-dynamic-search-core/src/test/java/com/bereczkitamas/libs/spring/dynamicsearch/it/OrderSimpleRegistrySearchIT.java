package com.bereczkitamas.libs.spring.dynamicsearch.it;

import static org.junit.jupiter.api.Assertions.*;

import com.bereczkitamas.libs.spring.dynamicsearch.data.*;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.Asset;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.Order;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
                "id",
                FieldMapping.alwaysIncluded(
                    "_id",
                    String.class,
                    SearchOperation.EQUALS,
                    SearchOperation.IN))
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
            .register(
                "projectionOnlyDate",
                FieldMapping.projectionOnly(
                    "orderDate",
                    Instant.class))
            .register(
                "tags",
                FieldMapping.arrayField(
                    "tags",
                    ArrayElementDescriptor.from(String.class),
                    SearchOperation.SIZE,
                    SearchOperation.CONTAINS_ALL))
            .register(
                "tagsByClass",
                FieldMapping.arrayField(
                    "tags",
                    String.class,
                    SearchOperation.SIZE,
                    SearchOperation.CONTAINS_ALL))
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
  @DisplayName("Numeric range search on totalAmount using GREATER_THAN_OR_EQUAL and LESS_THAN")
  void testNumericRangeSearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("totalAmount", SearchOperation.GREATER_THAN_OR_EQUAL, 1000.0)
            .where("totalAmount", SearchOperation.LESS_THAN, 3000.0)
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(2, response.total()); // ORD-1001 ($1250) and ORD-1004 ($2100)
    assertTrue(response.data().stream().allMatch(o -> o.getTotalAmount() >= 1000.0 && o.getTotalAmount() < 3000.0));
  }

  @Test
  @DisplayName("String pattern search using LIKE (case-insensitive contains) and STARTS_WITH")
  void testStringPatternSearch() {
    SearchRequest requestLike =
        SearchRequestBuilder.search()
            .where("orderNumber", SearchOperation.LIKE, "100")
            .build();

    PagedSearchResponse<Order> responseLike =
        executor.executeSearch(requestLike, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(5, responseLike.total());

    SearchRequest requestStartsWith =
        SearchRequestBuilder.search()
            .where("orderNumber", SearchOperation.STARTS_WITH, "ORD-1005")
            .build();

    PagedSearchResponse<Order> responseStartsWith =
        executor.executeSearch(requestStartsWith, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(1, responseStartsWith.total());
    assertEquals("ORD-1005", responseStartsWith.data().getFirst().getOrderNumber());
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
  @DisplayName("Joined Array ELEM_MATCH search on assets collection items")
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

  @Test
  @DisplayName("alwaysIncluded field (id) is retained in projection even when not explicitly requested")
  void testAlwaysIncludedIdFieldInCustomProjection() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("status", SearchOperation.EQUALS, "PROCESSING")
            .projection(ProjectionRequest.builder().include(Set.of("orderNumber", "totalAmount")).build())
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(1, response.total());
    Order order = response.data().getFirst();
    assertEquals("ORD-1002", order.getOrderNumber());
    assertEquals(3400.0, order.getTotalAmount());
    assertEquals("ord-1002", order.getId()); // Retained because of alwaysIncluded=true
  }

  @Test
  @DisplayName("projectionOnly field rejects search with InvalidSearchOperationException")
  void testProjectionOnlyFieldRejectsSearch() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("projectionOnlyDate", SearchOperation.EQUALS, "2026-01-10T10:00:00Z")
            .build();

    assertThrows(
        InvalidSearchOperationException.class,
        () -> executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class));
  }

  @Test
  @DisplayName("projectionOnly field is included in custom projection")
  void testProjectionOnlyFieldIncludedInProjection() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("orderNumber", SearchOperation.EQUALS, "ORD-1001")
            .projection(ProjectionRequest.builder().include(Set.of("orderNumber", "projectionOnlyDate")).build())
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(1, response.total());
    Order order = response.data().getFirst();
    assertEquals("ORD-1001", order.getOrderNumber());
    assertNotNull(order.getOrderDate());
    assertEquals(Instant.parse("2026-01-10T10:00:00Z"), order.getOrderDate());
  }

  @Test
  @DisplayName("Local arrayField with ArrayElementDescriptor supports SIZE and CONTAINS")
  void testLocalArrayFieldWithDescriptor() {
    // 1. SIZE = 3 (ORD-1002 has ["b2b", "bulk", "freight"])
    SearchRequest sizeRequest =
        SearchRequestBuilder.search()
            .where("tags", SearchOperation.SIZE, 3)
            .build();

    PagedSearchResponse<Order> sizeResponse =
        executor.executeSearch(sizeRequest, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(1, sizeResponse.total());
    assertEquals("ORD-1002", sizeResponse.data().getFirst().getOrderNumber());

    // 2. CONTAINS_ALL on array elements (ORD-1001 and ORD-1004 have "vip")
    SearchRequest tagRequest =
        SearchRequestBuilder.search()
            .where("tags", SearchOperation.CONTAINS_ALL, List.of("vip"))
            .build();

    PagedSearchResponse<Order> tagResponse =
        executor.executeSearch(tagRequest, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(2, tagResponse.total());
  }

  @Test
  @DisplayName("Local arrayField with Class<?> factory supports SIZE and CONTAINS")
  void testLocalArrayFieldWithClass() {
    SearchRequest request =
        SearchRequestBuilder.search()
            .where("tagsByClass", SearchOperation.SIZE, 1)
            .build();

    PagedSearchResponse<Order> response =
        executor.executeSearch(request, registry, PageRequest.of(0, 10), Order.class);

    assertEquals(2, response.total()); // ORD-1003 ("regular") and ORD-1005 ("cancelled")
  }
}
