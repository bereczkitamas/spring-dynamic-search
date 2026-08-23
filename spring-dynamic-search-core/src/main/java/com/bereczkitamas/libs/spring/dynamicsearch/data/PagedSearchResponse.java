package com.bereczkitamas.libs.spring.dynamicsearch.data;

import java.util.List;

public record PagedSearchResponse<E>(List<E> data, long total) {
}
