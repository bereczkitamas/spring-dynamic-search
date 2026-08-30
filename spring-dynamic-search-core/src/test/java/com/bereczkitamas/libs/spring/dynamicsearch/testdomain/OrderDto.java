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
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JoinedFields({
  @JoinedField(
      name = "customerName",
      type = String.class,
      collection = "users",
      localField = "customer",
      foreignField = "_id",
      as = "customer",
      documentField = "customer.name",
      description = "Customer name via referenced User document"),
  @JoinedField(
      name = "customerEmail",
      type = String.class,
      collection = "users",
      localField = "customer",
      foreignField = "_id",
      as = "customer",
      documentField = "customer.email",
      description = "Customer email via referenced User document"),
  @JoinedField(
      name = "customerCity",
      type = String.class,
      collection = "users",
      localField = "customer",
      foreignField = "_id",
      as = "customer",
      documentField = "customer.address.city",
      description = "Customer city via referenced User document"),
  @JoinedField(
      name = "customerLastLoggedIn",
      type = Instant.class,
      collection = "users",
      localField = "customer",
      foreignField = "_id",
      as = "customer",
      documentField = "customer.lastLoggedIn",
      description = "Customer last login timestamp via referenced User document"),
  @JoinedField(
      name = "assets",
      collection = "assets",
      localField = "assets",
      foreignField = "_id",
      as = "assets",
      documentField = "assets",
      singleResult = false,
      elementClass = Asset.class,
      description = "List of ordered asset line items via referenced Asset documents")
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

  @DocumentReference(collection = "users")
  private User customer;

  @DocumentReference(collection = "assets")
  private List<Asset> assets;

  private String customerName;
  private String customerEmail;
  private String customerCity;
  private Instant customerLastLoggedIn;

  @SearchableField(documentField = "attributes.channel", description = "Order origination channel")
  private String channel;

  @SearchableField(documentField = "attributes.priority", description = "Order processing priority")
  private String priority;

  @SearchableField(documentField = "attributes.couponCode", description = "Applied promotional coupon code")
  private String couponCode;

  private Map<String, Object> attributes;
}
