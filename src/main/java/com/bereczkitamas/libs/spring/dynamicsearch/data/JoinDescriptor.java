package com.bereczkitamas.libs.spring.dynamicsearch.data;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Represents the details necessary to define a join operation in a database query. This class
 * encapsulates the configuration for joining two collections, including the source collection, the
 * fields that define the relationship, and the type of result expected.
 *
 * <p>Fields:
 * <li>`collectionName`: The name of the collection to join with.
 * <li>`localField`: The local field in the source collection that is used for the join.
 * <li>`foreignField`: The field in the target collection that is used for the join.
 * <li>`as`: The alias name for the output of the join operation.
 * <li>`singleResult`: A flag indicating whether the join should result in a single unwrapped
 *     document (`true`) or an array of documents (`false`).
 */
@Data
@AllArgsConstructor
public class JoinDescriptor {
  private final String collectionName; // e.g., "countries"
  private final String localField; // e.g., "country_id"
  private final String foreignField; // e.g., "_id"
  private final String as; // alias, e.g., "country"
  private final boolean singleResult; // true = $unwind, false = keep array
}
