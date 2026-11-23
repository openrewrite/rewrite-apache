/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.apache.ignite;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeApacheIgnite_3Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.apache.ignite.UpgradeApacheIgnite_3")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "cache-api", "ignite-core-2"));
    }

    @DocumentExample
    @Test
    void embeddedNodeApplication() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.IgniteCache;
              import org.apache.ignite.IgniteException;
              import org.apache.ignite.Ignition;
              import org.apache.ignite.configuration.CacheConfiguration;
              import org.apache.ignite.configuration.IgniteConfiguration;
              import org.apache.ignite.transactions.Transaction;

              class Bank {
                  private final Ignite ignite = Ignition.start(new IgniteConfiguration());
                  private final IgniteCache<Long, Double> accounts = ignite.getOrCreateCache("accounts");
                  private final IgniteCache<Long, String> owners = ignite.getOrCreateCache(new CacheConfiguration<Long, String>("owners"));

                  void transfer(Long from, Long to, double amount) {
                      try (Transaction tx = ignite.transactions().txStart()) {
                          accounts.put(from, accounts.get(from) - amount);
                          accounts.put(to, accounts.get(to) + amount);
                          tx.commit();
                      } catch (IgniteException e) {
                          throw new IllegalStateException(e);
                      }
                  }

                  int count() {
                      return accounts.size();
                  }

                  void greet() {
                      ignite.compute().broadcast(() -> System.out.println("hello"));
                  }
              }
              """,
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.Ignition;
              import org.apache.ignite.configuration.CacheConfiguration;
              import org.apache.ignite.configuration.IgniteConfiguration;
              import org.apache.ignite.lang.IgniteException;
              import org.apache.ignite.lang.util.IgniteNameUtils;
              import org.apache.ignite.table.KeyValueView;
              import org.apache.ignite.tx.Transaction;

              class Bank {
                  // TODO Ignite 3: there is no node to start from an Ignite 2 configuration. Connect to an Ignite 3 cluster with IgniteClient.builder().addresses(...).build(), or start a node with IgniteServer.start(name, configPath, workDir) from ignite-runner; the migration-tools configuration-converter turns Ignite 2 XML configuration into Ignite 3 HOCON.
                  // TODO Ignite 3: nodes are configured with HOCON rather than IgniteConfiguration; the migration-tools configuration-converter turns Ignite 2 configuration into node and cluster configuration.
                  private final Ignite ignite = Ignition.start(new IgniteConfiguration());
                  // TODO Ignite 3: tables are not created on demand. Create the table with CREATE TABLE through ignite.sql() or with ignite.catalog().createTable(...), for example from the DDL that the migration-tools sql-ddl-generator writes for the cache configuration, and read it with ignite.tables().table(name).
                  private final KeyValueView<Long, Double> accounts = ignite.tables().table(IgniteNameUtils.quoteIfNeeded("accounts")).keyValueView(Long.class, Double.class);
                  // TODO Ignite 3: tables are not created on demand. Create the table with CREATE TABLE through ignite.sql() or with ignite.catalog().createTable(...), for example from the DDL that the migration-tools sql-ddl-generator writes for the cache configuration, and read it with ignite.tables().table(name).
                  // TODO Ignite 3: define a table with CREATE TABLE or ignite.catalog() instead of a CacheConfiguration; the migration-tools sql-ddl-generator writes the DDL for existing cache configurations.
                  private final KeyValueView<Long, String> owners = ignite.getOrCreateCache(new CacheConfiguration<Long, String>("owners"));

                  void transfer(Long from, Long to, double amount) {
                      try {
                          Transaction tx = ignite.transactions().begin();
                          try {
                              accounts.put(tx, from, accounts.get(tx, from) - amount);
                              accounts.put(tx, to, accounts.get(tx, to) + amount);
                              tx.commit();
                          } finally {
                              tx.rollback();
                          }
                      } catch (IgniteException e) {
                          throw new IllegalStateException(e);
                      }
                  }

                  int count() {
                      // TODO Ignite 3: KeyValueView has no size(); count the rows with SELECT COUNT(*) through ignite.sql().
                      return accounts.size();
                  }

                  void greet() {
                      // TODO Ignite 3: compute jobs are ComputeJob<T, R> classes deployed to the cluster and run with ignite.compute().execute(JobTarget, JobDescriptor, arg); closures, lambdas and peer class loading are gone.
                      ignite.compute().broadcast(() -> System.out.println("hello"));
                  }
              }
              """
          )
        );
    }

    @Test
    void coreBecomesThinClient() {
        rewriteRun(
          mavenProject("bank",
            //language=java
            srcMainJava(
              java(
                """
                  import org.apache.ignite.IgniteException;

                  class Errors {
                      boolean isIgnite(Exception e) {
                          return e instanceof IgniteException;
                      }
                  }
                  """,
                """
                  import org.apache.ignite.lang.IgniteException;

                  class Errors {
                      boolean isIgnite(Exception e) {
                          return e instanceof IgniteException;
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.acme</groupId>
                    <artifactId>bank</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.ignite</groupId>
                            <artifactId>ignite-core</artifactId>
                            <version>2.17.0</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.ignite</groupId>
                            <artifactId>ignite-indexing</artifactId>
                            <version>2.17.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>ignite-client</artifactId>")
                .containsPattern("<version>3\\.\\d+\\.\\d+</version>")
                .doesNotContain("<artifactId>ignite-core</artifactId>")
                .doesNotContain("<artifactId>ignite-indexing</artifactId>")
                .doesNotContain("2.17.0")
                .actual())
            )
          )
        );
    }

    @Test
    void springModuleIsReplacedByThinClient() {
        rewriteRun(
          mavenProject("cache",
            //language=java
            srcMainJava(
              java(
                """
                  import org.apache.ignite.IgniteCache;

                  class Store {
                      String find(IgniteCache<String, String> cache, String key) {
                          return cache.get(key);
                      }
                  }
                  """,
                """
                  import org.apache.ignite.table.KeyValueView;

                  class Store {
                      String find(KeyValueView<String, String> cache, String key) {
                          return cache.get(null, key);
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.acme</groupId>
                    <artifactId>cache</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <ignite.version>2.17.0</ignite.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.ignite</groupId>
                            <artifactId>ignite-spring</artifactId>
                            <version>${ignite.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>ignite-client</artifactId>")
                .containsPattern("<version>3\\.\\d+\\.\\d+</version>")
                .doesNotContain("<artifactId>ignite-spring</artifactId>")
                .actual())
            )
          )
        );
    }

    @Test
    void ignite3ModuleGetsNoThinClient() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "ignite-api-3")),
          mavenProject("accounts",
            //language=java
            srcMainJava(
              java(
                """
                  import org.apache.ignite.table.Table;

                  class Accounts {
                      Table table;
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.acme</groupId>
                    <artifactId>accounts</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.ignite</groupId>
                            <artifactId>ignite-api</artifactId>
                            <version>3.1.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }
}
