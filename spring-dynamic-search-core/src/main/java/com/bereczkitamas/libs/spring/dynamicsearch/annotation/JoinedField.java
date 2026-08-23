package com.bereczkitamas.libs.spring.dynamicsearch.annotation;

import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchOperation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a joined field relationship requiring a MongoDB {@code $lookup} and optional {@code $unwind}.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(JoinedFields.class)
public @interface JoinedField {

  /**
   * The property name in the DTO if placed on a class, or when overriding the field name.
   * If placed on a field/method, defaults to the field/property name.
   */
  String name() default "";

  /**
   * The document field path in MongoDB after the join (e.g. "country.name").
   * If empty, defaults to {@code as() + "." + (name().isEmpty() ? propertyName : name())}.
   */
  String documentField() default "";

  /**
   * The name of the foreign collection to join with (e.g. "countries").
   */
  String collection();

  /**
   * The local field in the primary collection used for the join (e.g. "country_id").
   */
  String localField();

  /**
   * The foreign field in the joined collection used for the join (e.g. "_id").
   */
  String foreignField() default "_id";

  /**
   * The alias name for the joined document/array in the aggregation pipeline (e.g. "country").
   */
  String as();

  /**
   * Whether the join produces a single unwound document ({@code true}, LEFT JOIN with {@code $unwind})
   * or preserves an array of documents ({@code false}).
   */
  boolean singleResult() default true;

  /**
   * The target Java type for query value conversion.
   * If {@code void.class}, inferred from the property type.
   */
  Class<?> type() default void.class;

  /**
   * Allowed search operations on this joined field.
   * If empty, defaults to all operations compatible with the field type.
   */
  SearchOperation[] operations() default {};

  /**
   * Whether this field can be included/excluded in projections.
   */
  boolean projectable() default true;

  /**
   * Whether this field can be queried in search criteria.
   */
  boolean searchable() default true;

  /**
   * Whether this field must always be included in the response projection.
   */
  boolean alwaysIncluded() default false;

  /**
   * Human and AI readable description explaining the joined field and its semantics.
   */
  String description() default "";

  /**
   * Example values for this field (used in AI agent prompt schemas).
   */
  String[] examples() default {};
}
