package com.bereczkitamas.libs.spring.dynamicsearch.data;

import com.bereczkitamas.libs.spring.dynamicsearch.annotation.SearchAnnotationScanner;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Describes the structure and searchable fields of array/collection elements.
 */
@Getter
@EqualsAndHashCode
@ToString
public class ArrayElementDescriptor {

  private final Map<String, FieldMapping> elementFields;

  public ArrayElementDescriptor(Map<String, FieldMapping> elementFields) {
    this.elementFields =
        elementFields != null
            ? Collections.unmodifiableMap(new HashMap<>(elementFields))
            : Collections.emptyMap();
  }

  /**
   * Resolves a field within the array element by its DTO field name.
   *
   * @param dtoField the name of the field on the element
   * @return the resolved {@link FieldMapping}
   * @throws InvalidSearchFieldException if the field is not registered or searchable
   */
  public FieldMapping resolveElementField(String dtoField) {
    FieldMapping mapping = elementFields.get(dtoField);
    if (mapping == null) {
      throw new InvalidSearchFieldException(
          "Element field '" + dtoField + "' is not searchable", dtoField);
    }
    return mapping;
  }

  /**
   * Creates an {@link ArrayElementDescriptor} from a map of element field mappings.
   */
  public static ArrayElementDescriptor of(Map<String, FieldMapping> elementFields) {
    return new ArrayElementDescriptor(elementFields);
  }

  /**
   * Creates an {@link ArrayElementDescriptor} by scanning the annotations on the given element class.
   */
  public static ArrayElementDescriptor from(Class<?> elementClass) {
    return new ArrayElementDescriptor(SearchAnnotationScanner.scan(elementClass));
  }
}
