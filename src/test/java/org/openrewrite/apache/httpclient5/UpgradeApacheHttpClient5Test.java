/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeApacheHttpClient5Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath(
            "httpclient", "httpcore",
            "httpclient5", "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @Test
    void migrateDependencies() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.httpcomponents</groupId>
                          <artifactId>httpclient</artifactId>
                          <version>4.5.14</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher version = Pattern.compile("5\\.4\\.\\d+").matcher(pom);
                assertThat(version.find()).describedAs("Expected 5.4.x in %s", pom).isTrue();
                return """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.httpcomponents.client5</groupId>
                              <artifactId>httpclient5</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(version.group(0));
            })));
    }

    @DocumentExample
    @Test
    void importReplacementsInGroupsWithSomeSpecificMappings() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpEntity;
              import org.apache.http.client.methods.HttpGet;
              import org.apache.http.client.methods.HttpUriRequest;
              import org.apache.http.util.EntityUtils;

              class A {
                  void method(HttpEntity entity, String urlStr) throws Exception {
                      HttpUriRequest getRequest = new HttpGet(urlStr);
                      EntityUtils.consume(entity);
                  }
              }
              """, """
              import org.apache.hc.core5.http.io.entity.EntityUtils;
              import org.apache.hc.core5.http.HttpEntity;
              import org.apache.hc.client5.http.classic.methods.HttpGet;
              import org.apache.hc.client5.http.classic.methods.HttpUriRequest;

              class A {
                  void method(HttpEntity entity, String urlStr) throws Exception {
                      HttpUriRequest getRequest = new HttpGet(urlStr);
                      EntityUtils.consume(entity);
                  }
              }
              """
          )
        );
    }

    @Test
    void addTimeUnitsToTimeoutAndDurationMethods() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.config.RequestConfig;
              import org.apache.http.config.SocketConfig;

              class A {
                  void method() {
                      int connectTimeout = 500;
                      RequestConfig.custom()
                          .setConnectionRequestTimeout(300)
                          .setConnectTimeout(connectTimeout)
                          .setSocketTimeout(connectTimeout * 10);

                      long linger = 420;
                      SocketConfig.custom()
                          .setSoTimeout(1000)
                          .setSoLinger((int) linger);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.config.RequestConfig;
              import org.apache.hc.core5.http.io.SocketConfig;

              import java.util.concurrent.TimeUnit;

              class A {
                  void method() {
                      int connectTimeout = 500;
                      RequestConfig.custom()
                          .setConnectionRequestTimeout(300, TimeUnit.MILLISECONDS)
                          .setConnectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                          .setResponseTimeout(connectTimeout * 10, TimeUnit.MILLISECONDS);

                      long linger = 420;
                      SocketConfig.custom()
                          .setSoTimeout(1000, TimeUnit.MILLISECONDS)
                          .setSoLinger((int) linger, TimeUnit.MILLISECONDS);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeStatusLineHttpResponse() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpStatus;
              import org.apache.http.client.methods.CloseableHttpResponse;
              import org.apache.http.client.methods.HttpGet;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClientBuilder;
              import org.apache.http.ProtocolVersion;

              import java.io.IOException;

              class A {
                  void method() throws IOException {
                      HttpGet httpGet = new HttpGet("https://moderne.io");
                      CloseableHttpClient instance = HttpClientBuilder.create().build();
                      CloseableHttpResponse response = instance.execute(httpGet);

                      System.out.println("response.getStatusLine() :: " + response.getStatusLine());
                      int statusCode = response.getStatusLine().getStatusCode();
                      String reason = response.getStatusLine().getReasonPhrase();
                      ProtocolVersion version = response.getStatusLine().getProtocolVersion();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
              import org.apache.hc.core5.http.HttpStatus;
              import org.apache.hc.client5.http.classic.methods.HttpGet;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
              import org.apache.hc.core5.http.ProtocolVersion;
              import org.apache.hc.core5.http.message.StatusLine;

              import java.io.IOException;

              class A {
                  void method() throws IOException {
                      HttpGet httpGet = new HttpGet("https://moderne.io");
                      CloseableHttpClient instance = HttpClientBuilder.create().build();
                      CloseableHttpResponse response = instance.execute(httpGet);

                      System.out.println("response.getStatusLine() :: " + new StatusLine(response));
                      int statusCode = response.getCode();
                      String reason = response.getReasonPhrase();
                      ProtocolVersion version = response.getVersion();
                  }
              }
              """
          )
        );
    }

    @Test
    void convertRequestBuilderToClassicRequestBuilder() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.methods.CloseableHttpResponse;
              import org.apache.http.client.methods.HttpUriRequest;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClientBuilder;
              import org.apache.http.client.methods.RequestBuilder;

              import java.io.IOException;

              class A {
                  void method() throws IOException {
                      RequestBuilder requestBuilder = RequestBuilder.get("https://moderne.io");
                      HttpUriRequest request = requestBuilder.build();
                      CloseableHttpClient instance = HttpClientBuilder.create().build();
                      CloseableHttpResponse response = instance.execute(request);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
              import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

              import java.io.IOException;

              class A {
                  void method() throws IOException {
                      ClassicRequestBuilder requestBuilder = ClassicRequestBuilder.get("https://moderne.io");
                      HttpUriRequest request = requestBuilder.build();
                      CloseableHttpClient instance = HttpClientBuilder.create().build();
                      CloseableHttpResponse response = instance.execute(request);
                  }
              }
              """
          )
        );
    }

    @Test
    void changeUsernamePasswordCredentials() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.auth.UsernamePasswordCredentials;

              class Example {
                  void method() {
                      UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("username", "password");
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;

              class Example {
                  void method() {
                      UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("username", "password".toCharArray());
                  }
              }
              """
          )
        );
    }

    // For `setStaleConnectionCheckEnabled(true)`, keep unchanged, need another PR to migrate it
    // Packages are changed because of other recipes
    @Test
    void setStaleConnectionCheckEnabledTrue() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.config.RequestConfig;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClientBuilder;

              class Example {
                  CloseableHttpClient client() {
                      RequestConfig requestConfig = RequestConfig.custom().setStaleConnectionCheckEnabled(true).build();

                      return HttpClientBuilder.create()
                          .setDefaultRequestConfig(requestConfig)
                          .build();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.config.RequestConfig;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

              class Example {
                  CloseableHttpClient client() {
                      RequestConfig requestConfig = RequestConfig.custom().setStaleConnectionCheckEnabled(true).build();

                      return HttpClientBuilder.create()
                          .setDefaultRequestConfig(requestConfig)
                          .build();
                  }
              }
              """
          )
        );
    }

    // For `setStaleConnectionCheckEnabled(false)`, with an existing `connManager`, just call `connManager.setValidateAfterInactivity(TimeValue.NEG_ONE_MILLISECOND);`
    @Test
    void setStaleConnectionCheckEnabledFalseWithConnManager() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.config.RequestConfig;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClientBuilder;
              import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

              class Example {
                  CloseableHttpClient client() {
                      RequestConfig requestConfig = RequestConfig.custom().setStaleConnectionCheckEnabled(false).build();

                      PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();

                      return HttpClientBuilder.create()
                          .setConnectionManager(connManager)
                          .setDefaultRequestConfig(requestConfig)
                          .build();
                  }
              }
              """,
            """
              import org.apache.hc.core5.util.TimeValue;
              import org.apache.hc.client5.http.config.RequestConfig;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;

              class Example {
                  CloseableHttpClient client() {
                      RequestConfig requestConfig = RequestConfig.custom().build();

                      PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();

                      connManager.setValidateAfterInactivity(TimeValue.NEG_ONE_MILLISECOND);

                      return HttpClientBuilder.create()
                          .setConnectionManager(connManager)
                          .setDefaultRequestConfig(requestConfig)
                          .build();
                  }
              }
              """
          )
        );
    }

    // For `setStaleConnectionCheckEnabled(false)`, without an existing `connManager`, have to create one first, then call `setConnectionManager(connManager)`
    @Test
    void setStaleConnectionCheckEnabledFalseWithoutConnManager() {
        rewriteRun(
          // We call `setConnectionManager` on a HC4 client builder, with a HC5 connection manager argument
          spec -> spec.afterTypeValidationOptions(TypeValidation.all().methodInvocations(false)),
          //language=java
          java(
            """
              import org.apache.http.client.config.RequestConfig;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClientBuilder;

              class Example {
                  CloseableHttpClient client() {
                      RequestConfig requestConfig = RequestConfig.custom().setStaleConnectionCheckEnabled(false).build();

                      return HttpClientBuilder.create()
                          .setDefaultRequestConfig(requestConfig)
                          .build();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.apache.hc.core5.util.TimeValue;
              import org.apache.hc.client5.http.config.RequestConfig;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

              class Example {
                  CloseableHttpClient client() {
                      PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager();
                      poolingHttpClientConnectionManager.setValidateAfterInactivity(TimeValue.NEG_ONE_MILLISECOND);
                      RequestConfig requestConfig = RequestConfig.custom().build();

                      return HttpClientBuilder.create()
                              .setDefaultRequestConfig(requestConfig).setConnectionManager(poolingHttpClientConnectionManager)
                          .build();
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-apache/issues/58")
    void setRetryHandlerToSetRetryStrategy() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.HttpRequestRetryHandler;
              import org.apache.http.impl.client.HttpClientBuilder;

              class A {
                  void method(HttpRequestRetryHandler retryHandler) throws Exception {
                      HttpClientBuilder builder = HttpClientBuilder.create()
                          .setRetryHandler(retryHandler);
                  }
              }
              """, """
              import org.apache.hc.client5.http.HttpRequestRetryStrategy;
              import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

              class A {
                  void method(HttpRequestRetryStrategy retryHandler) throws Exception {
                      HttpClientBuilder builder = HttpClientBuilder.create()
                          .setRetryStrategy(retryHandler);
                  }
              }
              """
          )
        );
    }
}
