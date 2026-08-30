package com.bereczkitamas.libs.spring.dynamicsearch.testdomain;

import com.bereczkitamas.libs.spring.dynamicsearch.annotation.JoinedField;
import com.bereczkitamas.libs.spring.dynamicsearch.annotation.JoinedFields;
import com.bereczkitamas.libs.spring.dynamicsearch.annotation.SearchableField;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JoinedFields({
  @JoinedField(
      name = "joinedCustomerName",
      type = String.class,
      collection = "users",
      localField = "customerId",
      foreignField = "_id",
      as = "joinedCustomer",
      documentField = "joinedCustomer.name",
      description = "Customer name via foreign collection join"),
  @JoinedField(
      name = "joinedCustomerEmail",
      type = String.class,
      collection = "users",
      localField = "customerId",
      foreignField = "_id",
      as = "joinedCustomer",
      documentField = "joinedCustomer.email",
      description = "Customer email via foreign collection join")
})
public class OrderDto {

  @SearchableField(alwaysIncluded = true, description = "Unique order identifier")
  private String id;

  @SearchableField(description = "Order reference number")
  private String orderNumber;

  @SearchableField(description = "Order status (e.g. NEW, PROCESSING, COMPLETED, CANCELLED)")
  private String status;

  @SearchableField(description = "Order total monetary amount")
  private Double totalAmount;

  @SearchableField(description = "Timestamp when the order was placed")
  private Instant orderDate;

  private String customerId;

  @SearchableField(documentField = "customer.name", description = "Embedded customer name")
  private String customerName;

  @SearchableField(documentField = "customer.email", description = "Embedded customer email")
  private String customerEmail;

  @SearchableField(documentField = "customer.address.city", description = "Embedded customer shipping city")
  private String customerCity;

  @SearchableField(documentField = "customer.lastLoggedIn", description = "Embedded customer last login timestamp")
  private Instant customerLastLoggedIn;

  @SearchableField(elementClass = Asset.class, description = "List of ordered asset line items")
  private List<Asset> assets;

  @SearchableField(documentField = "attributes.channel", description = "Order origination channel")
  private String channel;

  @SearchableField(documentField = "attributes.priority", description = "Order processing priority")
  private String priority;

  @SearchableField(documentField = "attributes.couponCode", description = "Applied promotional coupon code")
  private String couponCode;

  private Map<String, Object> attributes;
}
