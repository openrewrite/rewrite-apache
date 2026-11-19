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

class AddHttpExceptionToResponseHandlersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddHttpExceptionToResponseHandlers())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "httpcore5"));
    }

    @DocumentExample
    @Test
    void declaresHttpExceptionOnHandlersThatReadTheEntity() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.core5.http.ClassicHttpResponse;
              import org.apache.hc.core5.http.io.HttpClientResponseHandler;
              import org.apache.hc.core5.http.io.entity.EntityUtils;

              import java.io.IOException;

              class BodyHandler implements HttpClientResponseHandler<String> {
                  @Override
                  public String handleResponse(ClassicHttpResponse response) throws IOException {
                      return EntityUtils.toString(response.getEntity());
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ClassicHttpResponse;
              import org.apache.hc.core5.http.HttpException;
              import org.apache.hc.core5.http.io.HttpClientResponseHandler;
              import org.apache.hc.core5.http.io.entity.EntityUtils;

              import java.io.IOException;

              class BodyHandler implements HttpClientResponseHandler<String> {
                  @Override
                  public String handleResponse(ClassicHttpResponse response) throws IOException, HttpException {
                      return EntityUtils.toString(response.getEntity());
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.apache.hc.core5.http.ClassicHttpResponse;
              import org.apache.hc.core5.http.io.HttpClientResponseHandler;
              import org.apache.hc.core5.http.io.entity.EntityUtils;

              import java.io.IOException;

              class Handlers {
                  HttpClientResponseHandler<String> bodyOrNull() {
                      return new HttpClientResponseHandler<String>() {
                          @Override
                          public String handleResponse(ClassicHttpResponse response) {
                              try {
                                  return EntityUtils.toString(response.getEntity());
                              } catch (IOException e) {
                                  return null;
                              }
                          }
                      };
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ClassicHttpResponse;
              import org.apache.hc.core5.http.HttpException;
              import org.apache.hc.core5.http.io.HttpClientResponseHandler;
              import org.apache.hc.core5.http.io.entity.EntityUtils;

              import java.io.IOException;

              class Handlers {
                  HttpClientResponseHandler<String> bodyOrNull() {
                      return new HttpClientResponseHandler<String>() {
                          @Override
                          public String handleResponse(ClassicHttpResponse response) throws HttpException {
                              try {
                                  return EntityUtils.toString(response.getEntity());
                              } catch (IOException e) {
                                  return null;
                              }
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesMethodsThatNeedNoHttpExceptionDeclared() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.core5.http.ClassicHttpResponse;
              import org.apache.hc.core5.http.HttpEntity;
              import org.apache.hc.core5.http.ParseException;
              import org.apache.hc.core5.http.io.HttpClientResponseHandler;
              import org.apache.hc.core5.http.io.entity.EntityUtils;

              import java.io.IOException;

              class StatusHandler implements HttpClientResponseHandler<Integer> {
                  @Override
                  public Integer handleResponse(ClassicHttpResponse response) {
                      return response.getCode();
                  }

                  String body(HttpEntity entity) throws IOException, ParseException {
                      return EntityUtils.toString(entity);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.apache.hc.core5.http.ClassicHttpResponse;
              import org.apache.hc.core5.http.HttpException;
              import org.apache.hc.core5.http.io.HttpClientResponseHandler;
              import org.apache.hc.core5.http.io.entity.EntityUtils;

              import java.io.IOException;

              class DeclaredHandler implements HttpClientResponseHandler<String> {
                  @Override
                  public String handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
                      return EntityUtils.toString(response.getEntity());
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.apache.hc.core5.http.ClassicHttpResponse;
              import org.apache.hc.core5.http.io.entity.EntityUtils;

              import java.io.IOException;

              class NotAHandler {
                  String handleResponse(ClassicHttpResponse response) throws IOException {
                      return EntityUtils.toString(response.getEntity());
                  }
              }
              """
          )
        );
    }
}
