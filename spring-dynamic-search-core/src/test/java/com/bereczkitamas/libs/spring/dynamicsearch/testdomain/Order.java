package com.bereczkitamas.libs.spring.dynamicsearch.testdomain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orders")
public class Order {
  @Id
  private String id;
  private String orderNumber;
  private String status;
  private Double totalAmount;
  private Instant orderDate;
  private User customer;
  private String customerId;
  private List<String> assetIds;
  private List<Asset> assets;
  private Map<String, Object> attributes;
}
