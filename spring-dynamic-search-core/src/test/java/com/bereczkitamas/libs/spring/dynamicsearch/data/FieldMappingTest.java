package com.bereczkitamas.libs.spring.dynamicsearch.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FieldMappingTest {

  @Test
  @DisplayName("of() creates searchable, projectable field with specified operations")
  void testOfFactory() {
    FieldMapping mapping =
        FieldMapping.of("orderNumber", String.class, SearchOperation.EQUALS, SearchOperation.LIKE);

    assertEquals("orderNumber", mapping.getDocumentField());
    assertEquals(String.class, mapping.getType());
    assertEquals(Set.of(SearchOperation.EQUALS, SearchOperation.LIKE), mapping.getAllowedOperations());
    assertNull(mapping.getJoin());
    assertTrue(mapping.isProjectable());
    assertTrue(mapping.isSearchable());
    assertFalse(mapping.isAlwaysIncluded());
    assertFalse(mapping.isJoined());
    assertFalse(mapping.isArrayField());
    assertNull(mapping.getArrayElement());
  }

  @Test
  @DisplayName("projectionOnly() creates non-searchable, projectable field with empty operations")
  void testProjectionOnlyFactory() {
    FieldMapping mapping = FieldMapping.projectionOnly("remarks", String.class);

    assertEquals("remarks", mapping.getDocumentField());
    assertEquals(String.class, mapping.getType());
    assertTrue(mapping.getAllowedOperations().isEmpty());
    assertNull(mapping.getJoin());
    assertTrue(mapping.isProjectable());
    assertFalse(mapping.isSearchable());
    assertFalse(mapping.isAlwaysIncluded());
    assertFalse(mapping.isJoined());
    assertFalse(mapping.isArrayField());
  }

  @Test
  @DisplayName("alwaysIncluded() creates always-included field mapping")
  void testAlwaysIncludedFactory() {
    FieldMapping mapping =
        FieldMapping.alwaysIncluded("_id", String.class, SearchOperation.EQUALS, SearchOperation.IN);

    assertEquals("_id", mapping.getDocumentField());
    assertEquals(String.class, mapping.getType());
    assertEquals(Set.of(SearchOperation.EQUALS, SearchOperation.IN), mapping.getAllowedOperations());
    assertNull(mapping.getJoin());
    assertTrue(mapping.isProjectable());
    assertTrue(mapping.isSearchable());
    assertTrue(mapping.isAlwaysIncluded());
    assertFalse(mapping.isJoined());
    assertFalse(mapping.isArrayField());
  }

  @Test
  @DisplayName("joined() creates joined field mapping")
  void testJoinedFactory() {
    JoinDescriptor join = new JoinDescriptor("users", "customer", "_id", "customer", true);
    FieldMapping mapping =
        FieldMapping.joined("customer.name", String.class, join, SearchOperation.EQUALS);

    assertEquals("customer.name", mapping.getDocumentField());
    assertEquals(String.class, mapping.getType());
    assertEquals(Set.of(SearchOperation.EQUALS), mapping.getAllowedOperations());
    assertEquals(join, mapping.getJoin());
    assertTrue(mapping.isProjectable());
    assertTrue(mapping.isSearchable());
    assertFalse(mapping.isAlwaysIncluded());
    assertTrue(mapping.isJoined());
    assertFalse(mapping.isArrayField());
  }

  @Test
  @DisplayName("arrayField() with ArrayElementDescriptor sets collection type and descriptor")
  void testArrayFieldWithDescriptor() {
    ArrayElementDescriptor descriptor = ArrayElementDescriptor.from(String.class);
    FieldMapping mapping =
        FieldMapping.arrayField("tags", descriptor, SearchOperation.ELEM_MATCH, SearchOperation.SIZE);

    assertEquals("tags", mapping.getDocumentField());
    assertEquals(Collection.class, mapping.getType());
    assertEquals(Set.of(SearchOperation.ELEM_MATCH, SearchOperation.SIZE), mapping.getAllowedOperations());
    assertNull(mapping.getJoin());
    assertTrue(mapping.isProjectable());
    assertTrue(mapping.isSearchable());
    assertFalse(mapping.isAlwaysIncluded());
    assertFalse(mapping.isJoined());
    assertTrue(mapping.isArrayField());
    assertEquals(descriptor, mapping.getArrayElement());
  }

  @Test
  @DisplayName("arrayField() with Class<?> resolves ArrayElementDescriptor and default operations")
  void testArrayFieldWithClass() {
    FieldMapping mapping = FieldMapping.arrayField("tags", String.class);

    assertEquals("tags", mapping.getDocumentField());
    assertEquals(Collection.class, mapping.getType());
    assertEquals(Set.of(SearchOperation.ELEM_MATCH, SearchOperation.SIZE), mapping.getAllowedOperations());
    assertNull(mapping.getJoin());
    assertTrue(mapping.isArrayField());
    assertNotNull(mapping.getArrayElement());
  }

  @Test
  @DisplayName("joinedArray() with ArrayElementDescriptor creates joined array field")
  void testJoinedArrayWithDescriptor() {
    JoinDescriptor join = new JoinDescriptor("assets", "assets", "_id", "assets", false);
    ArrayElementDescriptor descriptor = ArrayElementDescriptor.from(String.class);
    FieldMapping mapping =
        FieldMapping.joinedArray("assets", join, descriptor, SearchOperation.ELEM_MATCH);

    assertEquals("assets", mapping.getDocumentField());
    assertEquals(Collection.class, mapping.getType());
    assertEquals(Set.of(SearchOperation.ELEM_MATCH), mapping.getAllowedOperations());
    assertEquals(join, mapping.getJoin());
    assertTrue(mapping.isJoined());
    assertTrue(mapping.isArrayField());
    assertEquals(descriptor, mapping.getArrayElement());
  }

  @Test
  @DisplayName("joinedArray() with Class<?> creates joined array field with default operations")
  void testJoinedArrayWithClass() {
    JoinDescriptor join = new JoinDescriptor("assets", "assets", "_id", "assets", false);
    FieldMapping mapping = FieldMapping.joinedArray("assets", join, String.class);

    assertEquals("assets", mapping.getDocumentField());
    assertEquals(Collection.class, mapping.getType());
    assertEquals(Set.of(SearchOperation.ELEM_MATCH, SearchOperation.SIZE), mapping.getAllowedOperations());
    assertEquals(join, mapping.getJoin());
    assertTrue(mapping.isJoined());
    assertTrue(mapping.isArrayField());
    assertNotNull(mapping.getArrayElement());
  }

  @Test
  @DisplayName("Custom constructor preserves description and examples")
  void testFullConstructor() {
    Set<String> examples = Set.of("vip", "express");
    FieldMapping mapping =
        new FieldMapping(
            "tags",
            Collection.class,
            Set.of(SearchOperation.ELEM_MATCH),
            null,
            true,
            true,
            false,
            "Order labels",
            examples,
            ArrayElementDescriptor.from(String.class));

    assertEquals("Order labels", mapping.getDescription());
    assertEquals(examples, mapping.getExamples());
    assertTrue(mapping.isArrayField());
  }
}
