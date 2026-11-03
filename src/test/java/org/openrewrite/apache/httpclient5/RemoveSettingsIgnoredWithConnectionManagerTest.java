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
package org.openrewrite.apache.httpclient5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveSettingsIgnoredWithConnectionManagerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient-4", "httpcore-4", "httpclient5", "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @DocumentExample
    @Test
    void removeSocketConfigNextToConnectionManager() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.config.RequestConfig;
              import org.apache.http.config.SocketConfig;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClients;
              import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

              class A {
                  CloseableHttpClient open(PoolingHttpClientConnectionManager connectionManager, RequestConfig requestConfig) {
                      SocketConfig socketConfig = SocketConfig.custom().setSoKeepAlive(true).build();
                      return HttpClients.custom().setConnectionManager(connectionManager).setDefaultSocketConfig(socketConfig)
                          .setDefaultRequestConfig(requestConfig).build();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.config.RequestConfig;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClients;
              import org.apache.hc.core5.http.io.SocketConfig;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;

              class A {
                  CloseableHttpClient open(PoolingHttpClientConnectionManager connectionManager, RequestConfig requestConfig) {
                      SocketConfig socketConfig = SocketConfig.custom().setSoKeepAlive(true).build();
                      return HttpClients.custom().setConnectionManager(connectionManager)
                          .setDefaultRequestConfig(requestConfig).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void removePoolSettingsSetBeforeConnectionManager() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.conn.HttpClientConnectionManager;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClientBuilder;

              class A {
                  CloseableHttpClient open(HttpClientConnectionManager connectionManager) {
                      return HttpClientBuilder.create()
                          .setMaxConnTotal(20)
                          .setMaxConnPerRoute(5)
                          .setConnectionManager(connectionManager)
                          .build();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
              import org.apache.hc.client5.http.io.HttpClientConnectionManager;

              class A {
                  CloseableHttpClient open(HttpClientConnectionManager connectionManager) {
                      return HttpClientBuilder.create()
                          .setConnectionManager(connectionManager)
                          .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepSocketConfigWithoutConnectionManager() {
        rewriteRun(
          spec -> spec.recipe(new RemoveSettingsIgnoredWithConnectionManager()),
          //language=java
          java(
            """
              import org.apache.http.config.SocketConfig;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClientBuilder;
              import org.apache.http.impl.client.HttpClients;
              import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

              class A {
                  CloseableHttpClient open(SocketConfig socketConfig) {
                      return HttpClients.custom().setDefaultSocketConfig(socketConfig).build();
                  }

                  CloseableHttpClient separateStatements(PoolingHttpClientConnectionManager cm, SocketConfig socketConfig) {
                      HttpClientBuilder builder = HttpClients.custom().setDefaultSocketConfig(socketConfig);
                      builder.setConnectionManager(cm);
                      return builder.build();
                  }

                  CloseableHttpClient nullManager(SocketConfig socketConfig) {
                      return HttpClients.custom().setConnectionManager(null).setDefaultSocketConfig(socketConfig).build();
                  }
              }
              """
          )
        );
    }
}
