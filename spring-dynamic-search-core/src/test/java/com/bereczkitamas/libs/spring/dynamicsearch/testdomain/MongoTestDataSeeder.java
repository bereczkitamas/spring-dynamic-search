package com.bereczkitamas.libs.spring.dynamicsearch.testdomain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.mongodb.core.MongoTemplate;

public class MongoTestDataSeeder {

  public static final String USER_ID_ALICE = "usr-001";
  public static final String USER_ID_BOB = "usr-002";
  public static final String USER_ID_CHARLIE = "usr-003";
  public static final String USER_ID_DIANA = "usr-004";

  public static void seedData(MongoTemplate mongoTemplate) {
    mongoTemplate.dropCollection(Order.class);
    mongoTemplate.dropCollection(User.class);

    // 1. Seed Users
    User alice =
        User.builder()
            .id(USER_ID_ALICE)
            .name("Alice Smith")
            .email("alice@example.com")
            .address(
                Address.builder()
                    .street("10 Main St")
                    .city("Budapest")
                    .zipCode("1051")
                    .country("Hungary")
                    .build())
            .lastLoggedIn(Instant.parse("2026-02-28T08:00:00Z"))
            .status("ACTIVE")
            .build();

    User bob =
        User.builder()
            .id(USER_ID_BOB)
            .name("Bob Jones")
            .email("bob@example.com")
            .address(
                Address.builder()
                    .street("22 King Rd")
                    .city("London")
                    .zipCode("SW1A")
                    .country("United Kingdom")
                    .build())
            .lastLoggedIn(Instant.parse("2026-02-15T12:30:00Z"))
            .status("ACTIVE")
            .build();

    User charlie =
        User.builder()
            .id(USER_ID_CHARLIE)
            .name("Charlie Brown")
            .email("charlie@example.com")
            .address(
                Address.builder()
                    .street("5th Ave 100")
                    .city("New York")
                    .zipCode("10001")
                    .country("USA")
                    .build())
            .lastLoggedIn(Instant.parse("2026-01-05T19:00:00Z"))
            .status("INACTIVE")
            .build();

    User diana =
        User.builder()
            .id(USER_ID_DIANA)
            .name("Diana Prince")
            .email("diana@example.com")
            .address(
                Address.builder()
                    .street("Unter den Linden 5")
                    .city("Berlin")
                    .zipCode("10117")
                    .country("Germany")
                    .build())
            .lastLoggedIn(Instant.parse("2026-02-25T17:15:00Z"))
            .status("ACTIVE")
            .build();

    mongoTemplate.insertAll(List.of(alice, bob, charlie, diana));

    // 2. Seed Orders
    Order order1 =
        Order.builder()
            .id("ord-1001")
            .orderNumber("ORD-1001")
            .status("COMPLETED")
            .totalAmount(1250.0)
            .orderDate(Instant.parse("2026-01-10T10:00:00Z"))
            .customer(alice)
            .customerId(USER_ID_ALICE)
            .assets(
                List.of(
                    Asset.builder()
                        .id("ast-1")
                        .name("Dell XPS 15")
                        .category("LAPTOP")
                        .price(1200.0)
                        .tags(List.of("electronics", "work"))
                        .specifications(Map.of("brand", "Dell", "ram", "32GB"))
                        .build(),
                    Asset.builder()
                        .id("ast-2")
                        .name("MX Master 3")
                        .category("ACCESSORY")
                        .price(50.0)
                        .tags(List.of("usb", "work"))
                        .specifications(Map.of("brand", "Logitech"))
                        .build()))
            .attributes(
                Map.of(
                    "channel", "web",
                    "priority", "high",
                    "couponCode", "SAVE10",
                    "shippingMethod", "EXPRESS"))
            .build();

    Order order2 =
        Order.builder()
            .id("ord-1002")
            .orderNumber("ORD-1002")
            .status("PROCESSING")
            .totalAmount(3400.0)
            .orderDate(Instant.parse("2026-01-15T14:30:00Z"))
            .customer(bob)
            .customerId(USER_ID_BOB)
            .assets(
                List.of(
                    Asset.builder()
                        .id("ast-3")
                        .name("PowerEdge R750")
                        .category("SERVER")
                        .price(3000.0)
                        .tags(List.of("enterprise", "hardware"))
                        .specifications(Map.of("brand", "Dell", "ram", "128GB"))
                        .build(),
                    Asset.builder()
                        .id("ast-4")
                        .name("APC Smart-UPS Rack")
                        .category("ACCESSORY")
                        .price(400.0)
                        .tags(List.of("hardware"))
                        .specifications(Map.of("brand", "APC"))
                        .build()))
            .attributes(
                Map.of(
                    "channel", "enterprise",
                    "priority", "urgent",
                    "shippingMethod", "FREIGHT"))
            .build();

    Order order3 =
        Order.builder()
            .id("ord-1003")
            .orderNumber("ORD-1003")
            .status("NEW")
            .totalAmount(450.0)
            .orderDate(Instant.parse("2026-02-01T09:15:00Z"))
            .customer(charlie)
            .customerId(USER_ID_CHARLIE)
            .assets(
                List.of(
                    Asset.builder()
                        .id("ast-5")
                        .name("LG UltraFine 4K")
                        .category("MONITOR")
                        .price(450.0)
                        .tags(List.of("display", "electronics"))
                        .specifications(Map.of("brand", "LG", "resolution", "4K"))
                        .build()))
            .attributes(
                Map.of(
                    "channel", "web",
                    "priority", "normal",
                    "couponCode", "WELCOME"))
            .build();

    Order order4 =
        Order.builder()
            .id("ord-1004")
            .orderNumber("ORD-1004")
            .status("COMPLETED")
            .totalAmount(2100.0)
            .orderDate(Instant.parse("2026-02-15T16:45:00Z"))
            .customer(diana)
            .customerId(USER_ID_DIANA)
            .assets(
                List.of(
                    Asset.builder()
                        .id("ast-6")
                        .name("MacBook Pro 16")
                        .category("LAPTOP")
                        .price(2000.0)
                        .tags(List.of("apple", "work"))
                        .specifications(Map.of("brand", "Apple", "ram", "16GB"))
                        .build(),
                    Asset.builder()
                        .id("ast-7")
                        .name("Anker USB-C Hub")
                        .category("ACCESSORY")
                        .price(100.0)
                        .tags(List.of("usb"))
                        .specifications(Map.of("brand", "Anker"))
                        .build()))
            .attributes(
                Map.of(
                    "channel", "mobile",
                    "priority", "high",
                    "couponCode", "APPLE10"))
            .build();

    Order order5 =
        Order.builder()
            .id("ord-1005")
            .orderNumber("ORD-1005")
            .status("CANCELLED")
            .totalAmount(80.0)
            .orderDate(Instant.parse("2026-02-20T11:00:00Z"))
            .customer(alice)
            .customerId(USER_ID_ALICE)
            .assets(
                List.of(
                    Asset.builder()
                        .id("ast-8")
                        .name("Keychron Q1")
                        .category("ACCESSORY")
                        .price(80.0)
                        .tags(List.of("gaming", "usb"))
                        .specifications(Map.of("brand", "Keychron"))
                        .build()))
            .attributes(
                Map.of(
                    "channel", "in-store",
                    "priority", "low"))
            .build();

    mongoTemplate.insertAll(List.of(order1, order2, order3, order4, order5));
  }
}
