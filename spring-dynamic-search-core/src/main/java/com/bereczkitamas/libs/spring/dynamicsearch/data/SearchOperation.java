package com.bereczkitamas.libs.spring.dynamicsearch.data;

public enum SearchOperation {
  EQUALS,
  NOT_EQUALS,
  LIKE,
  STARTS_WITH,
  ENDS_WITH,
  GREATER_THAN,
  LESS_THAN,
  GREATER_THAN_OR_EQUAL,
  LESS_THAN_OR_EQUAL,
  IN,
  NOT_IN,
  IS_NULL,
  IS_NOT_NULL,
  REGEX,
  BETWEEN,
  NOT_BETWEEN,
  EXISTS,
  DOES_NOT_EXIST,
  IS_EMPTY,
  IS_NOT_EMPTY,
  CONTAINS_ALL,
  ELEM_MATCH,
  SIZE
}

