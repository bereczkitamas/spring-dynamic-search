package com.bereczkitamas.libs.spring.dynamicsearch.annotation;

import com.bereczkitamas.libs.spring.dynamicsearch.data.FieldMapping;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchFieldRegistry;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchOperation;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SimpleSearchFieldRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchAnnotationScannerTest {

  // Sample POJO
  static class UserDto {
    @SearchableField(operations = {SearchOperation.EQUALS, SearchOperation.LIKE})
    private String username;

    @SearchableField(documentField = "user_age")
    private int age;

    @SearchableField
    private LocalDate birthDate;

    @SearchableField
    private List<String> tags;

    @SearchableField(alwaysIncluded = true)
    private String id;

    @JoinedField(
        collection = "countries",
        localField = "country_id",
        foreignField = "_id",
        as = "country",
        documentField = "country.name",
        operations = {SearchOperation.EQUALS, SearchOperation.LIKE})
    private String countryName;
  }

  // Sample Record
  record ProductRecord(
      @SearchableField(documentField = "prod_name") String name,
      @SearchableField double price,
      @JoinedField(
          collection = "categories",
          localField = "category_id",
          as = "category",
          operations = {SearchOperation.EQUALS})
      String categoryTitle) {}

  // Inheritance Hierarchy
  static class BaseEntityDto {
    @SearchableField
    private String id;

    @SearchableField
    private Instant createdAt;
  }

  @JoinedField(
      name = "deptName",
      collection = "departments",
      localField = "dept_id",
      as = "department",
      documentField = "department.name",
      type = String.class,
      operations = {SearchOperation.EQUALS})
  static class EmployeeDto extends BaseEntityDto {
    @SearchableField
    private String employeeName;

    private boolean active;

    @SearchableField
    public boolean isActive() {
      return active;
    }
  }

  static class InvalidClassJoinedDto {
    @JoinedField(
        collection = "departments",
        localField = "dept_id",
        as = "department")
    private String missingClassJoinedName;
  }

  @JoinedField(
      collection = "departments",
      localField = "dept_id",
      as = "department")
  static class InvalidClassLevelJoinedDto {}

  @Nested
  class PojoScanningTests {

    @Test
    void shouldScanPojoFields() {
      Map<String, FieldMapping> mappings = SearchAnnotationScanner.scan(UserDto.class);

      assertEquals(6, mappings.size());

      // username
      FieldMapping username = mappings.get("username");
      assertNotNull(username);
      assertEquals("username", username.getDocumentField());
      assertEquals(String.class, username.getType());
      assertEquals(Set.of(SearchOperation.EQUALS, SearchOperation.LIKE), username.getAllowedOperations());
      assertNull(username.getJoin());
      assertTrue(username.isSearchable());
      assertTrue(username.isProjectable());
      assertFalse(username.isAlwaysIncluded());

      // age (primitive int mapped to Integer.class with custom doc field and default numeric operations)
      FieldMapping age = mappings.get("age");
      assertNotNull(age);
      assertEquals("user_age", age.getDocumentField());
      assertEquals(Integer.class, age.getType());
      assertTrue(age.getAllowedOperations().contains(SearchOperation.BETWEEN));
      assertTrue(age.getAllowedOperations().contains(SearchOperation.GREATER_THAN));

      // birthDate
      FieldMapping birthDate = mappings.get("birthDate");
      assertNotNull(birthDate);
      assertEquals(LocalDate.class, birthDate.getType());
      assertTrue(birthDate.getAllowedOperations().contains(SearchOperation.BETWEEN));

      // tags (Collection defaults)
      FieldMapping tags = mappings.get("tags");
      assertNotNull(tags);
      assertTrue(tags.getAllowedOperations().contains(SearchOperation.CONTAINS_ALL));
      assertTrue(tags.getAllowedOperations().contains(SearchOperation.IN));

      // id (always included)
      FieldMapping id = mappings.get("id");
      assertNotNull(id);
      assertTrue(id.isAlwaysIncluded());

      // countryName (JoinedField)
      FieldMapping country = mappings.get("countryName");
      assertNotNull(country);
      assertTrue(country.isJoined());
      assertEquals("country.name", country.getDocumentField());
      assertEquals("countries", country.getJoin().getCollectionName());
      assertEquals("country_id", country.getJoin().getLocalField());
      assertEquals("_id", country.getJoin().getForeignField());
      assertEquals("country", country.getJoin().getAs());
      assertTrue(country.getJoin().isSingleResult());
    }
  }

  @Nested
  class RecordScanningTests {

    @Test
    void shouldScanRecordComponents() {
      Map<String, FieldMapping> mappings = SearchAnnotationScanner.scan(ProductRecord.class);

      assertEquals(3, mappings.size());

      FieldMapping name = mappings.get("name");
      assertEquals("prod_name", name.getDocumentField());
      assertEquals(String.class, name.getType());

      FieldMapping price = mappings.get("price");
      assertEquals(Double.class, price.getType());
      assertTrue(price.getAllowedOperations().contains(SearchOperation.BETWEEN));

      FieldMapping category = mappings.get("categoryTitle");
      assertTrue(category.isJoined());
      assertEquals("category.categoryTitle", category.getDocumentField());
      assertEquals("categories", category.getJoin().getCollectionName());
    }
  }

  @Nested
  class InheritanceAndMethodScanningTests {

    @Test
    void shouldScanHierarchyAndMethodsAndClassJoined() {
      Map<String, FieldMapping> mappings = SearchAnnotationScanner.scan(EmployeeDto.class);

      // id & createdAt from BaseEntityDto, employeeName & active from EmployeeDto, deptName from class JoinedField
      assertTrue(mappings.containsKey("id"));
      assertTrue(mappings.containsKey("createdAt"));
      assertTrue(mappings.containsKey("employeeName"));
      assertTrue(mappings.containsKey("active"));
      assertTrue(mappings.containsKey("deptName"));

      FieldMapping active = mappings.get("active");
      assertEquals(Boolean.class, active.getType());

      FieldMapping dept = mappings.get("deptName");
      assertTrue(dept.isJoined());
      assertEquals("department.name", dept.getDocumentField());
    }

    @Test
    void shouldThrow_whenClassLevelJoinedFieldOmitsName() {
      assertThrows(
          IllegalArgumentException.class,
          () -> SearchAnnotationScanner.scan(InvalidClassLevelJoinedDto.class));
    }
  }

  @Nested
  class RegistryFactoryAndBuilderTests {

    @Test
    void shouldCreateRegistryViaFactoryMethods() {
      SearchFieldRegistry registryFromClass = SearchFieldRegistry.from(UserDto.class);
      assertNotNull(registryFromClass.resolve("username"));
      assertNotNull(registryFromClass.resolve("countryName"));

      SearchFieldRegistry registryFromMap =
          SearchFieldRegistry.of(Map.of("name", FieldMapping.of("name", String.class, SearchOperation.EQUALS)));
      assertEquals("name", registryFromMap.resolve("name").getDocumentField());
    }

    @Test
    void shouldBuildRegistryWithFluentBuilder() {
      SimpleSearchFieldRegistry registry =
          SimpleSearchFieldRegistry.builder()
              .scan(UserDto.class)
              .register("customField", FieldMapping.of("custom_path", Long.class, SearchOperation.EQUALS))
              .build();

      assertNotNull(registry.resolve("username"));
      assertNotNull(registry.resolve("customField"));
      assertEquals("custom_path", registry.resolve("customField").getDocumentField());
    }
  }

  // Sample Nested Array DTOs
  static class MaterialDto {
    @SearchableField
    private String jobType;

    @SearchableField
    private String status;
  }

  static class DiagnosticDto {
    @SearchableField
    private String actionType;

    @SearchableField
    private List<MaterialDto> materials;

    @SearchableField(elementClass = MaterialDto.class)
    private Set<Object> explicitMaterials;
  }

  @Nested
  class ArrayAndNestedScanningTests {

    @Test
    void shouldScanArrayFieldsWithGenericType() {
      Map<String, FieldMapping> mappings = SearchAnnotationScanner.scan(DiagnosticDto.class);

      FieldMapping materials = mappings.get("materials");
      assertNotNull(materials);
      assertTrue(materials.isArrayField());
      assertNotNull(materials.getArrayElement());
      assertTrue(materials.getAllowedOperations().contains(SearchOperation.ELEM_MATCH));
      assertTrue(materials.getAllowedOperations().contains(SearchOperation.SIZE));

      // Check inner fields
      FieldMapping jobType = materials.getArrayElement().resolveElementField("jobType");
      assertNotNull(jobType);
      assertEquals("jobType", jobType.getDocumentField());

      FieldMapping status = materials.getArrayElement().resolveElementField("status");
      assertNotNull(status);
      assertEquals("status", status.getDocumentField());
    }

    @Test
    void shouldScanArrayFieldsWithExplicitElementClass() {
      Map<String, FieldMapping> mappings = SearchAnnotationScanner.scan(DiagnosticDto.class);

      FieldMapping explicitMaterials = mappings.get("explicitMaterials");
      assertNotNull(explicitMaterials);
      assertTrue(explicitMaterials.isArrayField());
      assertNotNull(explicitMaterials.getArrayElement());
      assertTrue(explicitMaterials.getAllowedOperations().contains(SearchOperation.ELEM_MATCH));

      FieldMapping jobType = explicitMaterials.getArrayElement().resolveElementField("jobType");
      assertNotNull(jobType);
      assertEquals("jobType", jobType.getDocumentField());
    }
  }
}

