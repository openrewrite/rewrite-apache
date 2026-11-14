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

class HandleEntityUtilsParseExceptionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient-4", "httpcore-4", "httpclient5", "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @DocumentExample
    @Test
    void addToCatchOfIOException() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpEntity;
              import org.apache.http.client.methods.CloseableHttpResponse;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClients;
              import org.apache.http.util.EntityUtils;

              import java.io.IOException;

              class A {
                  String token(HttpPost post) {
                      String result;
                      try (CloseableHttpClient httpClient = HttpClients.createDefault();
                           CloseableHttpResponse response = httpClient.execute(post)) {
                          HttpEntity entity = response.getEntity();
                          result = (entity == null) ? null : EntityUtils.toString(entity);
                      } catch (IOException e) {
                          throw new IllegalStateException("Error making token request", e);
                      }
                      return result;
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
              import org.apache.hc.core5.http.ParseException;
              import org.apache.hc.core5.http.io.entity.EntityUtils;
              import org.apache.hc.core5.http.HttpEntity;
              import org.apache.hc.client5.http.classic.methods.HttpPost;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClients;

              import java.io.IOException;

              class A {
                  String token(HttpPost post) {
                      String result;
                      try (CloseableHttpClient httpClient = HttpClients.createDefault();
                           CloseableHttpResponse response = httpClient.execute(post)) {
                          HttpEntity entity = response.getEntity();
                          result = (entity == null) ? null : EntityUtils.toString(entity);
                      } catch (IOException | ParseException e) {
                          throw new IllegalStateException("Error making token request", e);
                      }
                      return result;
                  }
              }
              """
          )
        );
    }

    @Test
    void addToMultiCatchAndLaterCatchOfIOException() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpResponse;
              import org.apache.http.client.ClientProtocolException;
              import org.apache.http.util.EntityUtils;

              import java.io.IOException;
              import java.net.URISyntaxException;

              class A {
                  String first(HttpResponse response) {
                      try {
                          new java.net.URI("http://example.com");
                          return EntityUtils.toString(response.getEntity());
                      } catch (IOException | URISyntaxException e) {
                          e.printStackTrace();
                          return null;
                      }
                  }

                  String second(HttpResponse response) {
                      try {
                          return EntityUtils.toString(response.getEntity(), "UTF-8");
                      } catch (ClientProtocolException e) {
                          return "protocol";
                      } catch (IOException e) {
                          System.err.println(e.getMessage());
                          return null;
                      }
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ClassicHttpResponse;
              import org.apache.hc.core5.http.ParseException;
              import org.apache.hc.core5.http.io.entity.EntityUtils;
              import org.apache.hc.client5.http.ClientProtocolException;

              import java.io.IOException;
              import java.net.URISyntaxException;

              class A {
                  String first(ClassicHttpResponse response) {
                      try {
                          new java.net.URI("http://example.com");
                          return EntityUtils.toString(response.getEntity());
                      } catch (IOException | URISyntaxException | ParseException e) {
                          e.printStackTrace();
                          return null;
                      }
                  }

                  String second(ClassicHttpResponse response) {
                      try {
                          return EntityUtils.toString(response.getEntity(), "UTF-8");
                      } catch (ClientProtocolException e) {
                          return "protocol";
                      } catch (IOException | ParseException e) {
                          System.err.println(e.getMessage());
                          return null;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void addToThrowsOfResponseHandler() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpResponse;
              import org.apache.http.client.ClientProtocolException;
              import org.apache.http.client.ResponseHandler;
              import org.apache.http.util.EntityUtils;

              import java.io.IOException;

              class CustomResponseHandler implements ResponseHandler<String> {
                  @Override
                  public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                      return EntityUtils.toString(response.getEntity());
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ClassicHttpResponse;
              import org.apache.hc.core5.http.ParseException;
              import org.apache.hc.core5.http.io.HttpClientResponseHandler;
              import org.apache.hc.core5.http.io.entity.EntityUtils;
              import org.apache.hc.client5.http.ClientProtocolException;

              import java.io.IOException;

              class CustomResponseHandler implements HttpClientResponseHandler<String> {
                  @Override
                  public String handleResponse(ClassicHttpResponse response) throws ClientProtocolException, IOException, ParseException {
                      return EntityUtils.toString(response.getEntity());
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveAloneWhenHandledOrSignatureWouldChange() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpResponse;
              import org.apache.http.client.ResponseHandler;
              import org.apache.http.util.EntityUtils;

              import java.io.IOException;
              import java.io.UncheckedIOException;

              class A {
                  String caught(HttpResponse response) {
                      try {
                          return EntityUtils.toString(response.getEntity());
                      } catch (Exception e) {
                          return null;
                      }
                  }

                  String declared(HttpResponse response) throws Exception {
                      return EntityUtils.toString(response.getEntity());
                  }

                  String signatureWouldChange(HttpResponse response) throws IOException {
                      return EntityUtils.toString(response.getEntity());
                  }

                  String rethrown(HttpResponse response) throws IOException {
                      try {
                          return EntityUtils.toString(response.getEntity());
                      } catch (IOException e) {
                          throw e;
                      }
                  }

                  String wrappedAsIOException(HttpResponse response) {
                      try {
                          return EntityUtils.toString(response.getEntity());
                      } catch (IOException e) {
                          throw new UncheckedIOException(e);
                      }
                  }

                  ResponseHandler<String> lambda() {
                      return response -> EntityUtils.toString(response.getEntity());
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ClassicHttpResponse;
              import org.apache.hc.core5.http.io.HttpClientResponseHandler;
              import org.apache.hc.core5.http.io.entity.EntityUtils;

              import java.io.IOException;
              import java.io.UncheckedIOException;

              class A {
                  String caught(ClassicHttpResponse response) {
                      try {
                          return EntityUtils.toString(response.getEntity());
                      } catch (Exception e) {
                          return null;
                      }
                  }

                  String declared(ClassicHttpResponse response) throws Exception {
                      return EntityUtils.toString(response.getEntity());
                  }

                  String signatureWouldChange(ClassicHttpResponse response) throws IOException {
                      return EntityUtils.toString(response.getEntity());
                  }

                  String rethrown(ClassicHttpResponse response) throws IOException {
                      try {
                          return EntityUtils.toString(response.getEntity());
                      } catch (IOException e) {
                          throw e;
                      }
                  }

                  String wrappedAsIOException(ClassicHttpResponse response) {
                      try {
                          return EntityUtils.toString(response.getEntity());
                      } catch (IOException e) {
                          throw new UncheckedIOException(e);
                      }
                  }

                  HttpClientResponseHandler<String> lambda() {
                      return response -> EntityUtils.toString(response.getEntity());
                  }
              }
              """
          )
        );
    }
}
