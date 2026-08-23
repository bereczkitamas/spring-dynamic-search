# Spring Dynamic Search

[![Maven Central](https://img.shields.io/maven-central/v/io.github.bereczkitamas/spring-dynamic-search.svg)](https://central.sonatype.com/artifact/io.github.bereczkitamas/spring-dynamic-search)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)](https://spring.io/projects/spring-boot)

A high-performance, type-safe dynamic querying, filtering, and aggregation library for **Spring Data MongoDB** and **AI Agents**.

---

## 📦 Modules

| Module | Description | When to Use |
|---|---|---|
| [`spring-dynamic-search-core`](./spring-dynamic-search-core) | Core MongoDB dynamic querying engine, criteria translation, foreign joins, stage pushdown, and `@SearchableField` annotations. | Standard CRUD microservices, REST APIs, and database filtering without AI dependencies. |
| [`spring-dynamic-search-ai`](./spring-dynamic-search-ai) | AI Agent integration, Spring AI `@Tool` callbacks, Model Context Protocol (MCP) server endpoints, schema introspection, and LLM query auto-repair. | Natural language search, chatbot assistants, autonomous coding agents, and hybrid RAG. |

---

## 🌟 Architecture

```mermaid
graph TD
    subgraph Clients["Clients & AI Assistants"]
        REST["REST API Client"]
        Agent["Spring AI Agent / LLM"]
        MCP["MCP Client (Cursor, Claude, Antigravity)"]
    end

    subgraph SDSAI["spring-dynamic-search-ai"]
        AITool["DynamicSearchAiTool"]
        MCPHandler["DynamicSearchMcpHandler"]
        SchemaSvc["SearchSchemaService"]
        Feedback["SearchErrorFeedbackFormatter"]
    end

    subgraph SDSCore["spring-dynamic-search-core"]
        Registry["SearchFieldRegistry (@SearchableField)"]
        Validator["SearchCriteriaValidator"]
        QueryBuilder["MongoQueryBuilder"]
        Executor["PagedAggregationExecutor"]
    end

    subgraph DB["Database"]
        Mongo[("MongoDB ($match + $lookup + $facet)")]
    end

    REST --> Validator
    Agent --> AITool
    MCP --> MCPHandler

    AITool --> SchemaSvc
    AITool --> Feedback
    AITool --> Executor
    MCPHandler --> AITool

    Validator --> QueryBuilder
    QueryBuilder --> Executor
    Registry --> Validator
    Executor --> Mongo
```

---

## 🚀 Quick Start

### 1. Core Usage (MongoDB Search Engine)

```xml
<dependency>
    <groupId>io.github.bereczkitamas</groupId>
    <artifactId>spring-dynamic-search-core</artifactId>
    <version>0.0.1</version>
</dependency>
```

```java
@SearchableField(description = "Customer username", operations = {SearchOperation.EQUALS, SearchOperation.LIKE})
private String username;

@JoinedField(collection = "countries", localField = "country_id", as = "country", documentField = "country.name")
private String countryName;
```

---

### 2. AI Agent & MCP Usage (Natural Language Queries & Tools)

```xml
<dependency>
    <groupId>io.github.bereczkitamas</groupId>
    <artifactId>spring-dynamic-search-ai</artifactId>
    <version>0.0.1</version>
</dependency>
```

```java
@Bean
public DynamicSearchAiTool<CustomerDto, ?> customerSearchAiTool(PagedAggregationExecutor executor) {
  SearchFieldRegistry registry = SearchFieldRegistry.from(CustomerDto.class);
  return DynamicSearchAiTool.of(executor, registry, CustomerDto.class);
}
```

---

## 📚 Documentation & Guides

- [**Core Engine Guide & Query Reference**](./spring-dynamic-search-core/README.md)
- [**AI Agent & MCP Integration Reference**](./spring-dynamic-search-ai/README.md)
- [**Architecture & Deep-Dive Guide**](./docs/AI_AGENT_INTEGRATION_GUIDE.md)

---

## 📄 License
This project is licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
