package com.bereczkitamas.libs.spring.dynamicsearch.data;

import jakarta.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;

public class JoinResolver {

  /** Scans all criteria and returns unique joins needed. */
  public Set<JoinDescriptor> resolveJoins(
      SearchRequest request, Pageable pageable, SearchFieldRegistry registry) {
    Set<JoinDescriptor> joins = new LinkedHashSet<>();
    collectFromCriteria(request.getCriteria(), registry, joins);
    collectFromGroups(request.getGroups(), registry, joins);

    // Also include join needed for sortBy field
    if (pageable.getSort().isSorted()) {
      pageable
          .getSort()
          .forEach(
              ordering -> {
                FieldMapping mapping = registry.resolve(ordering.getProperty());
                if (mapping.isJoined()) {
                  joins.add(mapping.getJoin());
                }
              });
    }
    return joins;
  }

  private void collectFromCriteria(
      @Nullable List<SearchCriteria> criteria,
      SearchFieldRegistry registry,
      Set<JoinDescriptor> joins) {
    if (criteria == null) {
      return;
    }

    for (SearchCriteria sc : criteria) {
      FieldMapping mapping = registry.resolve(sc.getField());
      if (mapping.isJoined()) {
        joins.add(mapping.getJoin());
      }
    }
  }

  private void collectFromGroups(
      @Nullable List<SearchGroup> groups, SearchFieldRegistry registry, Set<JoinDescriptor> joins) {
    if (groups == null) {
      return;
    }

    for (SearchGroup group : groups) {
      collectFromCriteria(group.getCriteria(), registry, joins);
      collectFromGroups(group.getGroups(), registry, joins);
    }
  }
}
