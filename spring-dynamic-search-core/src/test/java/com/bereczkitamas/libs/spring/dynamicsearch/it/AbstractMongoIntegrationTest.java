package com.bereczkitamas.libs.spring.dynamicsearch.it;

import com.bereczkitamas.libs.spring.dynamicsearch.data.PagedAggregationExecutor;
import com.bereczkitamas.libs.spring.dynamicsearch.testdomain.MongoTestDataSeeder;
import de.flapdoodle.embed.mongo.commands.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.StateID;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.Transitions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

public abstract class AbstractMongoIntegrationTest {

  private static TransitionWalker.ReachedState<RunningMongodProcess> mongodProcess;
  private static MongoClient mongoClient;
  protected static MongoTemplate mongoTemplate;
  protected PagedAggregationExecutor executor;

  @BeforeAll
  static synchronized void startEmbeddedMongo() {
    if (mongodProcess == null) {
      Transitions transitions = Mongod.instance().transitions(Version.Main.V7_0);
      mongodProcess = transitions.walker().initState(StateID.of(RunningMongodProcess.class));
      ServerAddress serverAddress = mongodProcess.current().getServerAddress();
      String connectionString = "mongodb://" + serverAddress.getHost() + ":" + serverAddress.getPort();
      mongoClient = MongoClients.create(connectionString);

      SimpleMongoClientDatabaseFactory dbFactory =
          new SimpleMongoClientDatabaseFactory(mongoClient, "spring_dynamic_search_it");

      org.springframework.data.mongodb.core.convert.MongoCustomConversions conversions =
          new org.springframework.data.mongodb.core.convert.MongoCustomConversions(java.util.Collections.emptyList());

      MongoMappingContext mappingContext = new MongoMappingContext();
      mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
      mappingContext.afterPropertiesSet();

      DefaultDbRefResolver dbRefResolver = new DefaultDbRefResolver(dbFactory);
      MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mappingContext);
      converter.setCustomConversions(conversions);
      converter.afterPropertiesSet();

      mongoTemplate = new MongoTemplate(dbFactory, converter);

      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      if (mongoClient != null) {
                        mongoClient.close();
                      }
                      if (mongodProcess != null) {
                        mongodProcess.close();
                      }
                    } catch (Exception ignored) {
                    }
                  }));
    }
  }

  @BeforeEach
  void setUpTest() {
    executor = new PagedAggregationExecutor(mongoTemplate);
    MongoTestDataSeeder.seedData(mongoTemplate);
  }
}
