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

import static org.openrewrite.java.Assertions.java;

class MigrateThinClientConfigurationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateThinClientConfiguration())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "cache-api", "ignite-core-2"));
    }

    @DocumentExample
    @Test
    void configurationVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignition;
              import org.apache.ignite.client.ClientRetryNonePolicy;
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.configuration.ClientConfiguration;

              class Clients {
                  IgniteClient connect(String password) {
                      ClientConfiguration cfg = new ClientConfiguration()
                              .setAddresses("10.0.0.1:10800", "10.0.0.2:10800")
                              .setPartitionAwarenessEnabled(true);
                      cfg.setTimeout(3000);
                      cfg.setUserName("app");
                      cfg.setUserPassword(password);
                      cfg.setRetryPolicy(new ClientRetryNonePolicy());
                      return Ignition.startClient(cfg);
                  }
              }
              """,
            """
              import org.apache.ignite.client.BasicAuthenticator;
              import org.apache.ignite.client.IgniteClient;

              class Clients {
                  IgniteClient connect(String password) {
                      return IgniteClient.builder()
                              .addresses("10.0.0.1:10800", "10.0.0.2:10800")
                              .connectTimeout(3000)
                              .operationTimeout(3000)
                              .authenticator(BasicAuthenticator.builder().username("app").password(password).build())
                              .retryPolicy(null)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineConfigurationInTryWithResources() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignition;
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.configuration.ClientConfiguration;

              class Clients {
                  void run() throws Exception {
                      try (IgniteClient client = Ignition.startClient(new ClientConfiguration().setAddresses("127.0.0.1:10800"))) {
                          System.out.println(client);
                      }
                  }
              }
              """,
            """
              import org.apache.ignite.client.IgniteClient;

              class Clients {
                  void run() throws Exception {
                      try (IgniteClient client = IgniteClient.builder().addresses("127.0.0.1:10800").build()) {
                          System.out.println(client);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void retryAllPolicyKeepsRetryingWrites() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignition;
              import org.apache.ignite.client.ClientRetryAllPolicy;
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.configuration.ClientConfiguration;

              class Clients {
                  static final String[] ADDRESSES = {"127.0.0.1:10800"};

                  IgniteClient connect() {
                      return Ignition.startClient(new ClientConfiguration()
                              .setAddresses(ADDRESSES)
                              .setRetryPolicy(new ClientRetryAllPolicy()));
                  }
              }
              """,
            """
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.client.RetryLimitPolicy;

              class Clients {
                  static final String[] ADDRESSES = {"127.0.0.1:10800"};

                  IgniteClient connect() {
                      return IgniteClient.builder()
                              .addresses(ADDRESSES)
                              .retryPolicy(new RetryLimitPolicy())
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void unsupportedSettingGetsComment() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignition;
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.client.SslMode;
              import org.apache.ignite.configuration.ClientConfiguration;

              class Clients {
                  IgniteClient connect() {
                      return Ignition.startClient(new ClientConfiguration()
                              .setAddresses("127.0.0.1:10800")
                              .setSslMode(SslMode.REQUIRED));
                  }
              }
              """,
            """
              import org.apache.ignite.Ignition;
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.client.SslMode;
              import org.apache.ignite.configuration.ClientConfiguration;

              class Clients {
                  IgniteClient connect() {
                      // TODO Ignite 3: build the client with IgniteClient.builder()...build(). Some of the ClientConfiguration settings used here have no Ignite 3 equivalent, or are set where this recipe cannot follow them.
                      return Ignition.startClient(new ClientConfiguration()
                              .setAddresses("127.0.0.1:10800")
                              .setSslMode(SslMode.REQUIRED));
                  }
              }
              """
          )
        );
    }

    @Test
    void portRangeGetsComment() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignition;
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.configuration.ClientConfiguration;

              class Clients {
                  IgniteClient connect() {
                      return Ignition.startClient(new ClientConfiguration().setAddresses("127.0.0.1:10800..10805"));
                  }
              }
              """,
            """
              import org.apache.ignite.Ignition;
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.configuration.ClientConfiguration;

              class Clients {
                  IgniteClient connect() {
                      // TODO Ignite 3: build the client with IgniteClient.builder()...build(). Some of the ClientConfiguration settings used here have no Ignite 3 equivalent, or are set where this recipe cannot follow them.
                      return Ignition.startClient(new ClientConfiguration().setAddresses("127.0.0.1:10800..10805"));
                  }
              }
              """
          )
        );
    }

    @Test
    void configurationFromElsewhereGetsComment() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignition;
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.configuration.ClientConfiguration;

              class Clients {
                  private final ClientConfiguration cfg;

                  Clients(ClientConfiguration cfg) {
                      this.cfg = cfg;
                  }

                  IgniteClient connect() {
                      return Ignition.startClient(cfg);
                  }
              }
              """,
            """
              import org.apache.ignite.Ignition;
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.configuration.ClientConfiguration;

              class Clients {
                  private final ClientConfiguration cfg;

                  Clients(ClientConfiguration cfg) {
                      this.cfg = cfg;
                  }

                  IgniteClient connect() {
                      // TODO Ignite 3: build the client with IgniteClient.builder()...build(). Some of the ClientConfiguration settings used here have no Ignite 3 equivalent, or are set where this recipe cannot follow them.
                      return Ignition.startClient(cfg);
                  }
              }
              """
          )
        );
    }

    @Test
    void configurationUsedTwiceGetsComment() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignition;
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.configuration.ClientConfiguration;

              class Clients {
                  IgniteClient connect() {
                      ClientConfiguration cfg = new ClientConfiguration().setAddresses("127.0.0.1:10800");
                      System.out.println(cfg);
                      return Ignition.startClient(cfg);
                  }
              }
              """,
            """
              import org.apache.ignite.Ignition;
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.configuration.ClientConfiguration;

              class Clients {
                  IgniteClient connect() {
                      ClientConfiguration cfg = new ClientConfiguration().setAddresses("127.0.0.1:10800");
                      System.out.println(cfg);
                      // TODO Ignite 3: build the client with IgniteClient.builder()...build(). Some of the ClientConfiguration settings used here have no Ignite 3 equivalent, or are set where this recipe cannot follow them.
                      return Ignition.startClient(cfg);
                  }
              }
              """
          )
        );
    }
}
