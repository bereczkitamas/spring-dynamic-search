# Enriched Search API for Spring Data MongoDB

A flexible, secure, and reusable search framework for building rich REST search endpoints on top of MongoDB. 
Supports multi-field filtering, AND/OR operators, nested logic, cross-collection joins, pagination, sorting, and DTO ↔ document field mapping.

---

## Table of Contents

1. [Features](#features)
2. [Architecture Overview](#architecture-overview)
3. [Setup](#setup)
4. [Quick Start](#quick-start)
5. [Core Components](#core-components)
6. [Field Registry Configuration](#field-registry-configuration)
7. [Supported Search Operations](#supported-search-operations)
8. [Search Request Format](#search-request-format)
9. [Cross-Collection Search (Joins)](#cross-collection-search-joins)
10. [Sorting & Pagination](#sorting--pagination)
11. [Error Handling](#error-handling)
12. [Security Considerations](#security-considerations)
13. [Performance Tuning](#performance-tuning)
14. [Adding a New Searchable Resource](#adding-a-new-searchable-resource)
15. [Testing](#testing)
16. [FAQ](#faq)

---

## Features

- 🔍 **Multi-field search** with any combination of fields
- 🔗 **AND/OR operators** at request and group level
- 🌳 **Nested logic groups** for complex expressions like `(A AND B) OR (C AND D)`
- 🔒 **Field & operation whitelisting** to prevent unsafe queries
- 🎭 **DTO ↔ Document field mapping** — API stays decoupled from schema
- 🔀 **Cross-collection joins** via MongoDB `$lookup`
- ⚡ **Optimized pagination** with `$facet` (single round-trip)
- 📄 **Self-documenting schema endpoint**
- 🧪 **Type-safe & fully testable**

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    REST Controller                          │
│                (accepts SearchRequest DTO)                  │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   Search Service                            │
│   (orchestrates validation, transformation, execution)      │
└──────┬──────────────────────────────┬───────────────────────┘
       │                              │
       ▼                              ▼
┌─────────────────┐          ┌────────────────────────┐
│ FieldRegistry   │          │ CriteriaValidator      │
│ (whitelist +    │          │ (checks fields/ops,    │
│  DTO→doc map)   │          │  converts types)       │
└─────────────────┘          └────────┬───────────────┘
                                      │
                                      ▼
                          ┌────────────────────────┐
                          │ MongoQueryBuilder      │
                          │ (builds Criteria/Query)│
                          └────────┬───────────────┘
                                   │
                                   ▼
                     ┌─────────────────────────────┐
                     │ AggregationPipelineBuilder  │
                     │ ($lookup + $match + $facet) │
                     └────────┬────────────────────┘
                              │
                              ▼
                     ┌─────────────────────┐
                     │   MongoTemplate     │
                     └─────────────────────┘
```

---

## Setup

### 1. Dependencies

Add to `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
    <!-- Optional: MapStruct for DTO mapping -->
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>1.5.5.Final</version>
    </dependency>
</dependencies>
```

### 2. Configuration

`application.yml`:

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/mydb

search:
  # Global defaults (optional)
  max-page-size: 100
  default-page-size: 20
  query-timeout-ms: 5000
```

### 3. Component Scan

Ensure the search framework's package is picked up:

```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.myapp", "com.myapp.search"})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

---

## Quick Start

### Step 1: Define your document

```java
@Document("users")
@Data
public class User {
    @Id private String id;
    @Field("first_name") private String firstName;
    @Field("email_address") private String emailAddress;
    private UserType type;
}
```

### Step 2: Define your DTO

```java
@Data
public class UserDTO {
    private String id;
    private String firstName;
    private String email;      // maps to emailAddress
    private UserType userType; // maps to type
}
```

### Step 3: Create a field registry

```java
@Component
public class UserSearchFieldRegistry implements SearchFieldRegistry {

    private static final Map<String, FieldMapping> MAPPINGS = Map.of(
        "firstName", FieldMapping.of("first_name", String.class, 
                        EQUALS, LIKE, STARTS_WITH),
        "email",     FieldMapping.of("email_address", String.class, 
                        EQUALS, LIKE),
        "userType",  FieldMapping.of("type", UserType.class, 
                        EQUALS, IN)
    );

    @Override
    public Map<String, FieldMapping> getMappings() { return MAPPINGS; }
}
```

### Step 4: Create the service

```java
@Service
@RequiredArgsConstructor
public class UserSearchService {
    private final MongoTemplate mongoTemplate;
    private final MongoQueryBuilder queryBuilder;
    private final UserSearchFieldRegistry registry;
    private final UserMapper mapper;

    public Page<UserDTO> search(SearchRequest request) {
        Criteria criteria = queryBuilder.build(request, registry);
        Query query = new Query(criteria).with(
            PageRequest.of(request.getPage(), request.getSize()));
        
        long total = mongoTemplate.count(query, User.class);
        List<UserDTO> results = mongoTemplate.find(query, User.class)
                .stream().map(mapper::toDTO).toList();
        
        return new PageImpl<>(results, query.getPageable(), total);
    }
}
```

### Step 5: Expose an endpoint

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserSearchService service;

    @PostMapping("/search")
    public Page<UserDTO> search(@RequestBody SearchRequest request) {
        return service.search(request);
    }
}
```

### Step 6: Send a request

```bash
curl -X POST http://localhost:8080/api/users/search \
  -H "Content-Type: application/json" \
  -d '{
    "operator": "AND",
    "criteria": [
      { "field": "firstName", "operation": "STARTS_WITH", "value": "John" },
      { "field": "userType", "operation": "EQUALS", "value": "ADMIN" }
    ],
    "page": 0,
    "size": 20
  }'
```

---

## Core Components

| Component | Responsibility |
|-----------|---------------|
| `SearchRequest` | Client-facing DTO carrying criteria, groups, pagination, sorting |
| `SearchCriteria` | Single condition: field + operation + value |
| `SearchGroup` | Nested group of criteria with its own AND/OR operator |
| `SearchFieldRegistry` | Whitelist mapping DTO fields → document fields |
| `FieldMapping` | Metadata for a searchable field (type, allowed ops, join info) |
| `SearchCriteriaValidator` | Validates and transforms client criteria |
| `MongoCriteriaBuilder` | Converts `SearchCriteria` → MongoDB `Criteria` |
| `MongoQueryBuilder` | Builds full query with AND/OR and nested groups |
| `JoinResolver` | Detects which collections need `$lookup` |
| `AggregationPipelineBuilder` | Constructs full aggregation for cross-collection search |
| `FacetedSearchExecutor` | Reusable helper executing `$facet` pagination |

---

## Field Registry Configuration

The registry is the **single source of truth** for what can be searched.

### Local Fields

```java
"firstName", FieldMapping.of(
    "first_name",           // document field name
    String.class,           // expected value type
    EQUALS, LIKE, STARTS_WITH  // allowed operations
)
```

### Nested Document Fields

Use dot notation:

```java
"city", FieldMapping.of("address.city", String.class, EQUALS, LIKE)
"zipCode", FieldMapping.of("address.zip_code", String.class, EQUALS)
```

### Joined Fields (Cross-Collection)

```java
private static final JoinDescriptor COUNTRY_JOIN = new JoinDescriptor(
    "countries", "country_id", "_id", "country", true);

"countryName", FieldMapping.joined("country.name", String.class,
                    COUNTRY_JOIN, EQUALS, LIKE)
```

### Enum Fields

```java
"userType", FieldMapping.of("type", UserType.class, EQUALS, IN)
```

Values sent as strings (`"ADMIN"`) are auto-converted to enums.

### Date Fields

```java
"createdAt", FieldMapping.of("created_at", LocalDateTime.class,
    GREATER_THAN, LESS_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL)
```

ISO-8601 strings from JSON are converted automatically.

---

## Supported Search Operations

| Operation | Applicable Types | MongoDB Equivalent | Example |
|-----------|-----------------|---------------------|---------|
| `EQUALS` | Any | `$eq` | `name = "John"` |
| `NOT_EQUALS` | Any | `$ne` | `name != "John"` |
| `LIKE` | String | `$regex` (contains, case-insensitive) | `name contains "oh"` |
| `STARTS_WITH` | String | `$regex` (prefix) | `name starts with "Jo"` |
| `ENDS_WITH` | String | `$regex` (suffix) | `name ends with "hn"` |
| `REGEX` | String | `$regex` (raw pattern) | Custom pattern |
| `GREATER_THAN` | Numbers, Dates | `$gt` | `age > 18` |
| `LESS_THAN` | Numbers, Dates | `$lt` | `age < 65` |
| `GREATER_THAN_OR_EQUAL` | Numbers, Dates | `$gte` | `age >= 18` |
| `LESS_THAN_OR_EQUAL` | Numbers, Dates | `$lte` | `age <= 65` |
| `IN` | Any (as list) | `$in` | `status IN ["A","B"]` |
| `NOT_IN` | Any (as list) | `$nin` | `status NOT IN ["X"]` |
| `IS_NULL` | Any | `$eq: null` | `deletedAt IS NULL` |
| `IS_NOT_NULL` | Any | `$ne: null` | `deletedAt IS NOT NULL` |

> **Note:** Operations are only allowed if listed in the field's `FieldMapping`. This is enforced at validation time.

---

## Search Request Format

### Basic Structure

```json
{
  "operator": "AND",          // "AND" | "OR" (default: AND)
  "criteria": [...],          // flat list of conditions
  "groups": [...],            // optional nested groups
  "page": 0,                  // 0-based (default: 0)
  "size": 20,                 // max results (default: 20)
  "sortBy": "firstName",      // DTO field name
  "sortDirection": "ASC"      // "ASC" | "DESC"
}
```

### Simple AND

```json
{
  "operator": "AND",
  "criteria": [
    { "field": "firstName", "operation": "STARTS_WITH", "value": "John" },
    { "field": "userType", "operation": "EQUALS", "value": "ADMIN" }
  ]
}
```

### Simple OR

```json
{
  "operator": "OR",
  "criteria": [
    { "field": "email", "operation": "LIKE", "value": "@gmail" },
    { "field": "phone", "operation": "STARTS_WITH", "value": "555" }
  ]
}
```

### IN List

```json
{
  "criteria": [
    { "field": "userType", "operation": "IN", "value": ["ADMIN", "MANAGER"] }
  ]
}
```

### Complex Nested Logic: `(A AND B) OR (C AND D)`

```json
{
  "operator": "OR",
  "groups": [
    {
      "operator": "AND",
      "criteria": [
        { "field": "firstName", "operation": "EQUALS", "value": "John" },
        { "field": "userType", "operation": "EQUALS", "value": "ADMIN" }
      ]
    },
    {
      "operator": "AND",
      "criteria": [
        { "field": "email", "operation": "LIKE", "value": "@gmail" },
        { "field": "userType", "operation": "EQUALS", "value": "USER" }
      ]
    }
  ]
}
```

### Combining Flat + Groups

```json
{
  "operator": "AND",
  "criteria": [
    { "field": "userType", "operation": "EQUALS", "value": "ADMIN" }
  ],
  "groups": [
    {
      "operator": "OR",
      "criteria": [
        { "field": "email", "operation": "LIKE", "value": "@gmail" },
        { "field": "email", "operation": "LIKE", "value": "@yahoo" }
      ]
    }
  ]
}
```
_Equivalent to: `userType=ADMIN AND (email~gmail OR email~yahoo)`_

---

## Cross-Collection Search (Joins)

When your DTO combines data from multiple collections (e.g., `User` + `Country`), configure the field registry with a `JoinDescriptor`.

### 1. Define the Join

```java
private static final JoinDescriptor COUNTRY_JOIN = new JoinDescriptor(
    "countries",   // foreign collection
    "country_id",  // local field in users
    "_id",         // foreign field in countries
    "country",     // alias (used as path prefix in queries)
    true           // singleResult=true → $unwind (1-to-1)
);
```

### 2. Register Joined Fields

```java
"countryCode", FieldMapping.joined("country.code", String.class,
                    COUNTRY_JOIN, EQUALS, IN),
"countryName", FieldMapping.joined("country.name", String.class,
                    COUNTRY_JOIN, EQUALS, LIKE),
"continent",   FieldMapping.joined("country.continent", String.class,
                    COUNTRY_JOIN, IN)
```

### 3. Use the Aggregation-Based Service

Switch the service to use `AggregationPipelineBuilder`:

```java
@Service
@RequiredArgsConstructor
public class UserSearchService {
    private final MongoTemplate mongoTemplate;
    private final AggregationPipelineBuilder pipelineBuilder;
    private final UserSearchFieldRegistry registry;
    private final UserMapper mapper;

    public Page<UserDTO> search(SearchRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        Aggregation aggregation = pipelineBuilder
                .buildSearchPipeline(request, registry, pageable);

        AggregationResults<UserWithCountryFacetResult> results = 
            mongoTemplate.aggregate(aggregation, "users", 
                UserWithCountryFacetResult.class);

        UserWithCountryFacetResult result = results.getUniqueMappedResult();
        if (result == null) return new PageImpl<>(List.of(), pageable, 0);

        return new PageImpl<>(
            result.getData().stream().map(mapper::toDTO).toList(),
            pageable,
            result.getTotal()
        );
    }
}
```

### 4. Query with Joined Fields

Clients don't need to know about the join — they just use DTO field names:

```json
{
  "operator": "AND",
  "criteria": [
    { "field": "firstName", "operation": "STARTS_WITH", "value": "John" },
    { "field": "continent", "operation": "IN", "value": ["Europe", "Asia"] }
  ],
  "sortBy": "countryName",
  "sortDirection": "ASC"
}
```

The framework automatically:
1. Detects that `continent` requires a country join
2. Adds `$lookup` + `$unwind` to the pipeline
3. Applies `$match` with joined field paths

### JoinDescriptor Options

| Parameter | Description |
|-----------|-------------|
| `collectionName` | Name of the foreign MongoDB collection |
| `localField` | Field in the current document holding the reference |
| `foreignField` | Field in the foreign document to match (usually `_id`) |
| `as` | Alias — becomes the path prefix (`country.name`) |
| `singleResult` | `true` for 1-to-1 (adds `$unwind`), `false` for 1-to-many |

---

## Sorting & Pagination

### Sorting

- Use DTO field names (not document names)
- Only registered fields can be sorted
- Joined fields can be sorted too — the framework handles it

```json
{
  "sortBy": "countryName",
  "sortDirection": "DESC"
}
```

### Pagination

- 0-based page numbers
- Default page size: 20
- Response is wrapped in Spring's `Page`:

```json
{
  "content": [...],
  "totalElements": 142,
  "totalPages": 8,
  "number": 0,
  "size": 20
}
```

### Enforcing Limits

Add a config to prevent runaway queries:

```java
@Value("${search.max-page-size:100}")
private int maxPageSize;

public Page<UserDTO> search(SearchRequest request) {
    if (request.getSize() > maxPageSize) {
        throw new IllegalArgumentException(
            "Page size cannot exceed " + maxPageSize);
    }
    // ...
}
```

---

## Error Handling

The framework throws two main exceptions:

| Exception | HTTP Status | Cause |
|-----------|-------------|-------|
| `InvalidSearchFieldException` | 400 | Field not in registry |
| `InvalidSearchOperationException` | 400 | Operation not allowed for field |

Registered via `@RestControllerAdvice`:

```java
@RestControllerAdvice
public class SearchExceptionHandler {

    @ExceptionHandler(InvalidSearchFieldException.class)
    public ResponseEntity<ErrorResponse> handleInvalidField(
            InvalidSearchFieldException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_SEARCH_FIELD", ex.getMessage()));
    }

    @ExceptionHandler(InvalidSearchOperationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOperation(
            InvalidSearchOperationException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_SEARCH_OPERATION", ex.getMessage()));
    }
}
```

Example error response:

```json
{
  "code": "INVALID_SEARCH_FIELD",
  "message": "Field 'password' is not searchable"
}
```

---

## Security Considerations

1. ✅ **Whitelist-only search** — the registry prevents users from querying arbitrary fields (like `password`, `internalNotes`)
2. ✅ **Regex escaping** — user input for `LIKE`, `STARTS_WITH`, `ENDS_WITH` is escaped via `Pattern.quote()` to prevent ReDoS
3. ✅ **Type conversion** — string inputs are converted to expected types, preventing type-based injection
4. ✅ **Operation whitelisting** — expensive operations (like `REGEX`) can be disallowed per field
5. ⚠️ **Rate limit** the search endpoint (recommended)
6. ⚠️ **Enforce max page size** to prevent memory exhaustion
7. ⚠️ **Set query timeouts** with `query.maxTimeMsec(5000)`

### Applying Query Timeout

```java
Query query = new Query(criteria);
query.maxTimeMsec(5000); // 5-second server-side timeout
```

---

## Performance Tuning

### Indexing

Create indexes matching your common query patterns:

```java
@Document("users")
@CompoundIndexes({
    @CompoundIndex(name = "type_created", def = "{'type': 1, 'created_at': -1}"),
    @CompoundIndex(name = "country_type", def = "{'country_id': 1, 'type': 1}")
})
public class User {
    @Indexed(unique = true)
    @Field("email_address")
    private String emailAddress;
    // ...
}
```

### Match Before Lookup

For AND queries, the framework can filter local fields **before** `$lookup`, dramatically reducing lookup workload. This is automatic when the top-level operator is `AND`.

### Use `$facet` for Combined Data + Count

The framework's aggregation-based service uses `$facet` to return paginated data and total count in **a single database round-trip** instead of two separate queries.

### Denormalize Hot Paths

For frequently-searched joined fields on high-traffic endpoints, consider denormalizing (embedding a snapshot):

```java
@Document("users")
public class User {
    @Field("country_id") private String countryId;
    @Field("country_snapshot") private CountrySnapshot countrySnapshot;
}
```

Update snapshots via MongoDB Change Streams or application events.

### Monitor Slow Queries

Enable MongoDB's slow-query log:
```javascript
db.setProfilingLevel(1, { slowms: 100 })
```

Use `explain("executionStats")` on suspect pipelines.

---

## Adding a New Searchable Resource

Follow this checklist:

### 1. Create the Document

```java
@Document("products")
@Data
public class Product {
    @Id private String id;
    private String name;
    private BigDecimal price;
    private String categoryId;
}
```

### 2. Create the DTO

```java
@Data
public class ProductDTO {
    private String id;
    private String name;
    private BigDecimal price;
    private CategoryDTO category;
}
```

### 3. Create the Field Registry

```java
@Component
public class ProductSearchFieldRegistry implements SearchFieldRegistry {
    private static final Map<String, FieldMapping> MAPPINGS = Map.of(
        "name",  FieldMapping.of("name", String.class, EQUALS, LIKE),
        "price", FieldMapping.of("price", BigDecimal.class, 
                    GREATER_THAN, LESS_THAN, GREATER_THAN_OR_EQUAL)
    );
    @Override
    public Map<String, FieldMapping> getMappings() { return MAPPINGS; }
}
```

### 4. Create the Facet Result Class

```java
public class ProductFacetResult extends FacetResult<Product> {}
```

### 5. Create the Search Service

Copy `UserSearchService` and adjust generics/types.

### 6. Expose the Controller

```java
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductSearchService service;

    @PostMapping("/search")
    public Page<ProductDTO> search(@RequestBody SearchRequest request) {
        return service.search(request);
    }
}
```

Done! You've added a fully-featured searchable resource in ~15 minutes.

---

## Testing

### Unit Test the Registry

```java
@Test
void resolves_registered_field() {
    var registry = new UserSearchFieldRegistry();
    FieldMapping m = registry.resolve("firstName");
    assertEquals("first_name", m.getDocumentField());
}

@Test
void throws_for_unknown_field() {
    var registry = new UserSearchFieldRegistry();
    assertThrows(InvalidSearchFieldException.class, 
        () -> registry.resolve("password"));
}
```

### Unit Test the Criteria Builder

```java
@Test
void builds_equals_criteria() {
    var builder = new MongoCriteriaBuilder();
    var sc = new SearchCriteria("name", EQUALS, "John");
    Criteria c = builder.buildCriteria(sc);
    Document doc = c.getCriteriaObject();
    assertEquals("John", doc.get("name"));
}
```

### Integration Test the Endpoint

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserSearchIT {
    @Container static MongoDBContainer mongo = new MongoDBContainer("mongo:6");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired MockMvc mvc;

    @Test
    void searches_by_firstName() throws Exception {
        String body = """
            {
              "criteria": [
                {"field":"firstName","operation":"EQUALS","value":"John"}
              ]
            }""";
        
        mvc.perform(post("/api/users/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.content[0].firstName").value("John"));
    }
}
```

---

## FAQ

### Q: Why POST for search instead of GET?

Because search requests can be complex with nested groups. Encoding them as query parameters becomes unreadable and hits URL length limits. `POST /search` is a well-accepted REST convention for this case.

### Q: Can I use this with Spring Data JPA?

Yes — the concept translates directly. Replace `MongoTemplate`/`Criteria` with `JpaSpecificationExecutor`/`Specification`, and `$lookup` with JPA joins. See our JPA guide for details.

### Q: How do I expose available search fields to clients?

Add a schema endpoint:

```java
@GetMapping("/search/schema")
public Map<String, FieldSchema> schema() {
    return registry.getMappings().entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> new FieldSchema(
                e.getValue().getType().getSimpleName(),
                e.getValue().getAllowedOperations()
            )
        ));
}
```

### Q: Can I add custom operations?

Yes. Extend `SearchOperation` enum and add the handling logic in `MongoCriteriaBuilder`.

### Q: What about full-text search?

Register a field mapped to a MongoDB text index and use a custom operation, or use the `REGEX` operation. For advanced needs, consider integrating MongoDB Atlas Search or Elasticsearch.

### Q: How do I handle "OR across joins"?

The framework supports this — just place criteria referencing joined fields inside an OR group. However, note that "pre-filter before lookup" optimization only applies to top-level ANDs. Complex OR queries across joins may be slower.

### Q: Can I combine multiple joins?

Yes. Register different `JoinDescriptor` instances for each join. The `JoinResolver` collects all unique joins per request and adds them to the pipeline in order.

---

## License & Contribution

This framework is designed to be embedded directly into your application. Copy it, adapt it, extend it. Contributions welcome — especially for:

- Additional operations (geo queries, array operators)
- Alternative query languages (RSQL parser)
- Performance benchmarks
- More database adapters (JPA, R2DBC)

---

## Summary

You now have a **production-grade, secure, DTO-decoupled search framework** for Spring Data MongoDB that:

- 🎯 Keeps your API contract clean and independent from schema
- 🛡️ Prevents unsafe or accidental queries via whitelisting
- 🚀 Scales to complex cross-collection scenarios
- 📐 Follows a clear, reusable pattern for any new resource

Happy searching! 🔍