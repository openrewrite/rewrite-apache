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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeQualityValidation;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MovePoolSettingsToConnectionManagerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient-4",
            "httpcore-4",
            "httpclient5",
            "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5")
          // ChangePackage and ChangeType keep HttpClient 4's members on the types they rename
          .recipeQualityValidation(RecipeQualityValidation.builder().parserTypedTrees(false).build());
    }

    @DocumentExample
    @Test
    void separateStatements() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClientBuilder;

              class A {
                  CloseableHttpClient open() {
                      HttpClientBuilder builder = HttpClientBuilder.create();
                      builder.setMaxConnPerRoute(50);
                      builder.setMaxConnTotal(100);
                      return builder.build();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;

              class A {
                  CloseableHttpClient open() {
                      HttpClientBuilder builder = HttpClientBuilder.create();
                      builder.setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().setMaxConnPerRoute(50).setMaxConnTotal(100).build());
                      return builder.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void fluentChain() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.config.RequestConfig;
              import org.apache.http.config.SocketConfig;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClients;

              class A {
                  CloseableHttpClient open(SocketConfig socketConfig, RequestConfig requestConfig) {
                      return HttpClients.custom()
                          .setMaxConnTotal(100)
                          .setDefaultRequestConfig(requestConfig)
                          .setDefaultSocketConfig(socketConfig)
                          .build();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.config.RequestConfig;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClients;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
              import org.apache.hc.core5.http.io.SocketConfig;

              class A {
                  CloseableHttpClient open(SocketConfig socketConfig, RequestConfig requestConfig) {
                      return HttpClients.custom()
                          .setDefaultRequestConfig(requestConfig)
                          .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().setMaxConnTotal(100).setDefaultSocketConfig(socketConfig).build())
                          .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void separateStatementsBesideOtherSettings() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.config.RequestConfig;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClientBuilder;
              import org.apache.http.impl.client.HttpClients;

              class A {
                  CloseableHttpClient open(RequestConfig requestConfig) {
                      HttpClientBuilder builder = HttpClients.custom().setUserAgent("agent");
                      builder.setMaxConnTotal(100);
                      builder.setDefaultRequestConfig(requestConfig);
                      CloseableHttpClient client = builder.build();
                      return client;
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.config.RequestConfig;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
              import org.apache.hc.client5.http.impl.classic.HttpClients;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;

              class A {
                  CloseableHttpClient open(RequestConfig requestConfig) {
                      HttpClientBuilder builder = HttpClients.custom().setUserAgent("agent");
                      builder.setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().setMaxConnTotal(100).build());
                      builder.setDefaultRequestConfig(requestConfig);
                      CloseableHttpClient client = builder.build();
                      return client;
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceAllowAllHostnameVerifier() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.net.ssl.SSLContext;

              import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

              class A {
                  SSLConnectionSocketFactory socketFactory(SSLContext sslContext) {
                      return new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
                  }
              }
              """,
            """
              import javax.net.ssl.SSLContext;

              import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
              import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;

              class A {
                  SSLConnectionSocketFactory socketFactory(SSLContext sslContext) {
                      return new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void builderWithTlsSettings() {
            rewriteRun(
              // The pool settings stay, so the output still has to be finished by hand
              spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder().parserTypedTrees(false).compilableOutput(false).build()),
              //language=java
              java(
                """
                  import javax.net.ssl.SSLContext;

                  import org.apache.http.impl.client.CloseableHttpClient;
                  import org.apache.http.impl.client.HttpClientBuilder;

                  class A {
                      CloseableHttpClient fluent(SSLContext sslContext) {
                          return HttpClientBuilder.create().setMaxConnTotal(100).setSSLContext(sslContext).build();
                      }

                      CloseableHttpClient statements(SSLContext sslContext) {
                          HttpClientBuilder builder = HttpClientBuilder.create().setSSLContext(sslContext);
                          builder.setMaxConnTotal(100);
                          return builder.build();
                      }

                      CloseableHttpClient assigned(SSLContext sslContext) {
                          HttpClientBuilder builder;
                          builder = HttpClientBuilder.create().setSSLContext(sslContext);
                          builder.setMaxConnTotal(100);
                          return builder.build();
                      }
                  }
                  """,
                """
                  import javax.net.ssl.SSLContext;

                  import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
                  import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

                  class A {
                      CloseableHttpClient fluent(SSLContext sslContext) {
                          return HttpClientBuilder.create().setMaxConnTotal(100).setSSLContext(sslContext).build();
                      }

                      CloseableHttpClient statements(SSLContext sslContext) {
                          HttpClientBuilder builder = HttpClientBuilder.create().setSSLContext(sslContext);
                          builder.setMaxConnTotal(100);
                          return builder.build();
                      }

                      CloseableHttpClient assigned(SSLContext sslContext) {
                          HttpClientBuilder builder;
                          builder = HttpClientBuilder.create().setSSLContext(sslContext);
                          builder.setMaxConnTotal(100);
                          return builder.build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void builderNotCreatedAndBuiltInTheSameScope() {
            rewriteRun(
              // The pool settings stay, so the output still has to be finished by hand
              spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder().parserTypedTrees(false).compilableOutput(false).build()),
              //language=java
              java(
                """
                  import org.apache.http.impl.client.CloseableHttpClient;
                  import org.apache.http.impl.client.HttpClientBuilder;

                  class A {
                      HttpClientBuilder newBuilder() {
                          return HttpClientBuilder.create();
                      }

                      void configure(HttpClientBuilder builder) {
                          builder.useSystemProperties();
                      }

                      HttpClientBuilder returnedChain() {
                          return HttpClientBuilder.create().setMaxConnTotal(100);
                      }

                      HttpClientBuilder returnedVariable() {
                          HttpClientBuilder builder = HttpClientBuilder.create();
                          builder.setMaxConnTotal(100);
                          return builder;
                      }

                      void parameter(HttpClientBuilder builder) {
                          builder.setMaxConnTotal(100);
                      }

                      CloseableHttpClient fromOtherMethodChain() {
                          return newBuilder().setMaxConnTotal(100).build();
                      }

                      CloseableHttpClient fromOtherMethodVariable() {
                          HttpClientBuilder builder = newBuilder();
                          builder.setMaxConnTotal(100);
                          return builder.build();
                      }

                      CloseableHttpClient passedToOtherMethod() {
                          HttpClientBuilder builder = HttpClientBuilder.create();
                          configure(builder);
                          builder.setMaxConnTotal(100);
                          return builder.build();
                      }
                  }
                  """,
                """
                  import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
                  import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

                  class A {
                      HttpClientBuilder newBuilder() {
                          return HttpClientBuilder.create();
                      }

                      void configure(HttpClientBuilder builder) {
                          builder.useSystemProperties();
                      }

                      HttpClientBuilder returnedChain() {
                          return HttpClientBuilder.create().setMaxConnTotal(100);
                      }

                      HttpClientBuilder returnedVariable() {
                          HttpClientBuilder builder = HttpClientBuilder.create();
                          builder.setMaxConnTotal(100);
                          return builder;
                      }

                      void parameter(HttpClientBuilder builder) {
                          builder.setMaxConnTotal(100);
                      }

                      CloseableHttpClient fromOtherMethodChain() {
                          return newBuilder().setMaxConnTotal(100).build();
                      }

                      CloseableHttpClient fromOtherMethodVariable() {
                          HttpClientBuilder builder = newBuilder();
                          builder.setMaxConnTotal(100);
                          return builder.build();
                      }

                      CloseableHttpClient passedToOtherMethod() {
                          HttpClientBuilder builder = HttpClientBuilder.create();
                          configure(builder);
                          builder.setMaxConnTotal(100);
                          return builder.build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void poolSettingsOutsideTheSeparateStatements() {
            rewriteRun(
              // The pool settings stay, so the output still has to be finished by hand
              spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder().parserTypedTrees(false).compilableOutput(false).build()),
              //language=java
              java(
                """
                  import org.apache.http.impl.client.CloseableHttpClient;
                  import org.apache.http.impl.client.HttpClientBuilder;

                  class A {
                      CloseableHttpClient initializer() {
                          HttpClientBuilder builder = HttpClientBuilder.create().setMaxConnTotal(100);
                          builder.setMaxConnPerRoute(5);
                          return builder.build();
                      }

                      CloseableHttpClient nested(boolean perRoute) {
                          HttpClientBuilder builder = HttpClientBuilder.create();
                          builder.setMaxConnTotal(100);
                          if (perRoute) {
                              builder.setMaxConnPerRoute(5);
                          }
                          return builder.build();
                      }
                  }
                  """,
                """
                  import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
                  import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

                  class A {
                      CloseableHttpClient initializer() {
                          HttpClientBuilder builder = HttpClientBuilder.create().setMaxConnTotal(100);
                          builder.setMaxConnPerRoute(5);
                          return builder.build();
                      }

                      CloseableHttpClient nested(boolean perRoute) {
                          HttpClientBuilder builder = HttpClientBuilder.create();
                          builder.setMaxConnTotal(100);
                          if (perRoute) {
                              builder.setMaxConnPerRoute(5);
                          }
                          return builder.build();
                      }
                  }
                  """
              )
            );
        }
    }
}
