package com.bereczkitamas.libs.spring.dynamicsearch.testdomain;

import java.time.Instant;
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
@Document(collection = "users")
public class User {
  @Id
  private String id;
  private String name;
  private String email;
  private Address address;
  private Instant lastLoggedIn;
  private String status;
}
