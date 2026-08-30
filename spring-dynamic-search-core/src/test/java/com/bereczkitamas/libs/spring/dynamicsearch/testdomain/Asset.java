package com.bereczkitamas.libs.spring.dynamicsearch.testdomain;

import com.bereczkitamas.libs.spring.dynamicsearch.annotation.SearchableField;
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
@Document(collection = "assets")
public class Asset {
  @Id
  @SearchableField(description = "Asset identifier")
  private String id;

  @SearchableField(description = "Asset name")
  private String name;

  @SearchableField(description = "Asset category")
  private String category;

  @SearchableField(description = "Asset unit price")
  private Double price;

  @SearchableField(description = "Tags associated with the asset")
  private List<String> tags;

  @SearchableField(description = "Dynamic asset specifications and attributes")
  private Map<String, Object> specifications;
}
