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
      name = "customerName",
      type = String.class,
      collection = "users",
      localField = "customerId",
      foreignField = "_id",
      as = "customer",
      documentField = "customer.name",
      description = "Customer name via foreign collection join"),
  @JoinedField(
      name = "customerEmail",
      type = String.class,
      collection = "users",
      localField = "customerId",
      foreignField = "_id",
      as = "customer",
      documentField = "customer.email",
      description = "Customer email via foreign collection join"),
  @JoinedField(
      name = "customerCity",
      type = String.class,
      collection = "users",
      localField = "customerId",
      foreignField = "_id",
      as = "customer",
      documentField = "customer.address.city",
      description = "Customer city via foreign collection join"),
  @JoinedField(
      name = "customerLastLoggedIn",
      type = Instant.class,
      collection = "users",
      localField = "customerId",
      foreignField = "_id",
      as = "customer",
      documentField = "customer.lastLoggedIn",
      description = "Customer last login timestamp via foreign collection join"),
  @JoinedField(
      name = "assets",
      collection = "assets",
      localField = "assetIds",
      foreignField = "_id",
      as = "assets",
      documentField = "assets",
      singleResult = false,
      elementClass = Asset.class,
      description = "List of ordered asset line items joined from assets collection")
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
  private List<String> assetIds;

  private String customerName;
  private String customerEmail;
  private String customerCity;
  private Instant customerLastLoggedIn;
  private List<Asset> assets;

  @SearchableField(documentField = "attributes.channel", description = "Order origination channel")
  private String channel;

  @SearchableField(documentField = "attributes.priority", description = "Order processing priority")
  private String priority;

  @SearchableField(documentField = "attributes.couponCode", description = "Applied promotional coupon code")
  private String couponCode;

  private Map<String, Object> attributes;
}
