# Spring Dynamic Search Core (`spring-dynamic-search-core`)

[![Maven Central](https://img.shields.io/maven-central/v/io.github.bereczkitamas/spring-dynamic-search.svg)](https://central.sonatype.com/artifact/io.github.bereczkitamas/spring-dynamic-search-core)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://openjdk.org/)

`spring-dynamic-search-core` is a lightweight, type-safe, dynamic querying and aggregation library for **Spring Data MongoDB**.

It translates abstract `SearchRequest` DTOs (criteria, logical operators, nested groups, projections, foreign `$lookup` joins, and pagination) into MongoDB `Criteria` and `Aggregation` pipelines with automatic stage pushdown and whitelisting.

---

## 🌟 Key Features

- **Declarative Mapping Annotations**: Use `@SearchableField` and `@JoinedField` on entity/DTO classes to auto-generate search registries without manual boilerplate.
- **Strict Whitelist & Security**: Only whitelisted fields and permitted operators can be queried, preventing unauthorized database scanning.
- **Pre-Join Stage Pushdown**: Filters local fields *before* `$lookup` + `$unwind` stages, dramatically reducing RAM consumption and aggregation latency.
- **Extended Search Operations**: Supports `EQUALS`, `NOT_EQUALS`, `LIKE`, `STARTS_WITH`, `ENDS_WITH`, `REGEX`, `GREATER_THAN`, `LESS_THAN`, `BETWEEN`, `NOT_BETWEEN`, `IN`, `NOT_IN`, `EXISTS`, `DOES_NOT_EXIST`, `IS_EMPTY`, `IS_NOT_EMPTY`, `CONTAINS_ALL`, `IS_NULL`, `IS_NOT_NULL`.
- **Foreign Joins**: Declarative left joins via MongoDB `$lookup` and `$unwind`.
- **Disk Spilling & Query Options**: Full control over MongoDB `AggregationOptions` (`allowDiskUse(true)`, `maxTime`, `collation`, hints).

---

## 📦 Maven Dependency

```xml
<dependency>
    <groupId>io.github.bereczkitamas</groupId>
    <artifactId>spring-dynamic-search-core</artifactId>
    <version>0.0.1</version>
</dependency>
```

---

## 🚀 Usage

### 1. Declarative Domain Entity

```java
import com.bereczkitamas.libs.spring.dynamicsearch.annotation.JoinedField;
import com.bereczkitamas.libs.spring.dynamicsearch.annotation.SearchableField;
import com.bereczkitamas.libs.spring.dynamicsearch.data.SearchOperation;
import java.time.LocalDate;

public class UserDto {

  @SearchableField(operations = {SearchOperation.EQUALS, SearchOperation.LIKE})
  private String username;

  @SearchableField(documentField = "user_age")
  private int age;

  @SearchableField
  private LocalDate birthDate;

  @JoinedField(
      collection = "countries",
      localField = "country_id",
      foreignField = "_id",
      as = "country",
      documentField = "country.name",
      operations = {SearchOperation.EQUALS, SearchOperation.LIKE}
  )
  private String countryName;
}
```

---

### 2. Execute Dynamic Searches

```java
import com.bereczkitamas.libs.spring.dynamicsearch.data.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class UserService {

  private final PagedAggregationExecutor executor;
  private final SearchFieldRegistry registry = SearchFieldRegistry.from(UserDto.class);

  public UserService(MongoTemplate mongoTemplate) {
    this.executor = new PagedAggregationExecutor(mongoTemplate);
  }

  public PagedSearchResponse<UserDto> searchUsers(SearchRequest request, int page, int size) {
    return executor.executeSearch(
        request,
        registry,
        PageRequest.of(page, size),
        User.class,
        UserPagedResult.class
    );
  }
}
```

---

## 📄 License
Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
