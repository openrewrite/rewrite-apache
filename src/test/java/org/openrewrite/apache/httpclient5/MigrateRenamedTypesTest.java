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

/**
 * Types and constants that HttpClient 5 kept under another name.
 */
class MigrateRenamedTypesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient-4", "httpcore-4", "httpmime-4", "httpclient5", "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @DocumentExample
    @Test
    void basicStatusLineToStatusLine() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpStatus;
              import org.apache.http.ProtocolVersion;
              import org.apache.http.StatusLine;
              import org.apache.http.message.BasicStatusLine;

              class A {
                  private static final StatusLine DEFAULT_STATUS = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK");

                  int notFound() {
                      BasicStatusLine status = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_NOT_FOUND, "Not Found");
                      return status.getStatusCode();
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.HttpStatus;
              import org.apache.hc.core5.http.ProtocolVersion;
              import org.apache.hc.core5.http.message.StatusLine;

              class A {
                  private static final StatusLine DEFAULT_STATUS = new StatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK");

                  int notFound() {
                      StatusLine status = new StatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_NOT_FOUND, "Not Found");
                      return status.getStatusCode();
                  }
              }
              """
          )
        );
    }

    @Test
    void laxRedirectStrategyToDefaultRedirectStrategy() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClients;
              import org.apache.http.impl.client.LaxRedirectStrategy;

              class A {
                  CloseableHttpClient create() {
                      return HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy()).build();
                  }

                  CloseableHttpClient shared() {
                      return HttpClients.custom().setRedirectStrategy(LaxRedirectStrategy.INSTANCE).build();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClients;

              class A {
                  CloseableHttpClient create() {
                      return HttpClients.custom().setRedirectStrategy(new DefaultRedirectStrategy()).build();
                  }

                  CloseableHttpClient shared() {
                      return HttpClients.custom().setRedirectStrategy(DefaultRedirectStrategy.INSTANCE).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void basicResponseHandlerToBasicHttpClientResponseHandler() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.methods.HttpGet;
              import org.apache.http.impl.client.BasicResponseHandler;
              import org.apache.http.impl.client.CloseableHttpClient;

              import java.io.IOException;

              class A {
                  String get(CloseableHttpClient client, String url) throws IOException {
                      return client.execute(new HttpGet(url), new BasicResponseHandler());
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.classic.methods.HttpGet;
              import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

              import java.io.IOException;

              class A {
                  String get(CloseableHttpClient client, String url) throws IOException {
                      return client.execute(new HttpGet(url), new BasicHttpClientResponseHandler());
                  }
              }
              """
          )
        );
    }

    @Test
    void multipartModeRfc6532ToExtended() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpEntity;
              import org.apache.http.entity.mime.HttpMultipartMode;
              import org.apache.http.entity.mime.MultipartEntityBuilder;

              import java.io.File;

              class A {
                  HttpEntity upload(File file) {
                      return MultipartEntityBuilder.create().addBinaryBody("media", file).setMode(HttpMultipartMode.RFC6532).build();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
              import org.apache.hc.core5.http.HttpEntity;
              import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;

              import java.io.File;

              class A {
                  HttpEntity upload(File file) {
                      return MultipartEntityBuilder.create().addBinaryBody("media", file).setMode(HttpMultipartMode.EXTENDED).build();
                  }
              }
              """
          )
        );
    }
}
