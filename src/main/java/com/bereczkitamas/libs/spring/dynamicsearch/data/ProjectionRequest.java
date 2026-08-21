package com.bereczkitamas.libs.spring.dynamicsearch.data;

import jakarta.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionRequest {
  /**
   * DTO field names to include. If empty, all fields included. Cannot be combined with 'exclude'.
   */
  @Builder.Default @Nonnull private final Set<String> include = new HashSet<>();

  /**
   * DTO field names to exclude. If empty, no exclusions applied. Cannot be combined with 'include'.
   */
  @Builder.Default @Nonnull private final Set<String> exclude = new HashSet<>();
}
