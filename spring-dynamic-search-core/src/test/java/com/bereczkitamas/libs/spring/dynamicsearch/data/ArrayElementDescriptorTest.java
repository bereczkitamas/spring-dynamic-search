package com.bereczkitamas.libs.spring.dynamicsearch.data;

import com.bereczkitamas.libs.spring.dynamicsearch.annotation.SearchableField;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrayElementDescriptorTest {

  static class ItemDto {
    @SearchableField
    private String sku;

    @SearchableField
    private double price;
  }

  @Test
  void shouldResolveRegisteredElementField() {
    FieldMapping skuMapping = FieldMapping.of("sku", String.class, SearchOperation.EQUALS);
    ArrayElementDescriptor descriptor = ArrayElementDescriptor.of(Map.of("sku", skuMapping));

    FieldMapping resolved = descriptor.resolveElementField("sku");
    assertNotNull(resolved);
    assertEquals("sku", resolved.getDocumentField());
  }

  @Test
  void shouldThrow_whenElementFieldNotFound() {
    ArrayElementDescriptor descriptor = ArrayElementDescriptor.of(Map.of());

    assertThrows(
        InvalidSearchFieldException.class,
        () -> descriptor.resolveElementField("unknownField"));
  }

  @Test
  void shouldScanFromElementClass() {
    ArrayElementDescriptor descriptor = ArrayElementDescriptor.from(ItemDto.class);

    assertNotNull(descriptor.resolveElementField("sku"));
    assertNotNull(descriptor.resolveElementField("price"));
    assertEquals(2, descriptor.getElementFields().size());
  }

  @Test
  void shouldHandleNullMapGracefully() {
    ArrayElementDescriptor descriptor = new ArrayElementDescriptor(null);
    assertTrue(descriptor.getElementFields().isEmpty());
  }
}
