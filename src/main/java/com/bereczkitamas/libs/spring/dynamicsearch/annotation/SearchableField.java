package com.bereczkitamas.libs.spring.dynamicsearch.annotation;

import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchOperation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field, record component, or getter method in an entity or DTO as searchable and/or
 * projectable in MongoDB aggregation queries.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SearchableField {

  /**
   * The document field path in MongoDB.
   * If empty, defaults to the annotated property name.
   */
  String documentField() default "";

  /**
   * The target Java type for query value conversion.
   * If {@code void.class}, inferred from the property type.
   */
  Class<?> type() default void.class;

  /**
   * Allowed search operations on this field.
   * If empty, defaults to all operations compatible with the field type.
   */
  SearchOperation[] operations() default {};

  /**
   * Whether this field can be included or excluded in projections.
   */
  boolean projectable() default true;

  /**
   * Whether this field can be queried in search criteria.
   */
  boolean searchable() default true;

  /**
   * Whether this field must always be included in response projections (e.g. ID field).
   */
  boolean alwaysIncluded() default false;
}
