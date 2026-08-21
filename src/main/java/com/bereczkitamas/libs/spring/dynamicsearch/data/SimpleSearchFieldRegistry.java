package com.bereczkitamas.libs.spring.dynamicsearch.data;

import com.bereczkitamas.libs.spring.dynamicsearch.annotation.SearchAnnotationScanner;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * An immutable, map-backed implementation of {@link SearchFieldRegistry}.
 */
@EqualsAndHashCode
@ToString
public class SimpleSearchFieldRegistry implements SearchFieldRegistry {

  private final Map<String, FieldMapping> mappings;

  public SimpleSearchFieldRegistry(Map<String, FieldMapping> mappings) {
    this.mappings = mappings != null ? Collections.unmodifiableMap(new HashMap<>(mappings)) : Collections.emptyMap();
  }

  @Override
  public Map<String, FieldMapping> getMappings() {
    return mappings;
  }

  /**
   * Creates a registry from a predefined map of mappings.
   */
  public static SimpleSearchFieldRegistry of(Map<String, FieldMapping> mappings) {
    return new SimpleSearchFieldRegistry(mappings);
  }

  /**
   * Creates a registry by scanning annotations on the given class.
   */
  public static SimpleSearchFieldRegistry from(Class<?> clazz) {
    return new SimpleSearchFieldRegistry(SearchAnnotationScanner.scan(clazz));
  }

  /**
   * Returns a builder for constructing a {@link SimpleSearchFieldRegistry}.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final Map<String, FieldMapping> mappings = new HashMap<>();

    public Builder register(String dtoField, FieldMapping mapping) {
      this.mappings.put(dtoField, mapping);
      return this;
    }

    public Builder registerAll(Map<String, FieldMapping> mappings) {
      if (mappings != null) {
        this.mappings.putAll(mappings);
      }
      return this;
    }

    public Builder scan(Class<?> clazz) {
      if (clazz != null) {
        this.mappings.putAll(SearchAnnotationScanner.scan(clazz));
      }
      return this;
    }

    public SimpleSearchFieldRegistry build() {
      return new SimpleSearchFieldRegistry(mappings);
    }
  }
}
