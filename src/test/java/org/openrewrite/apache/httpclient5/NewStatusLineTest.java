/*
 * Copyright 2025 the original author or authors.
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

class NewStatusLineTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "httpclient-4", "httpcore-4"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @DocumentExample
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
              import org.apache.hc.core5.http.message.StatusLine;
              import org.apache.hc.core5.http.HttpStatus;
              import org.apache.hc.client5.http.classic.methods.HttpGet;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
              import org.apache.hc.core5.http.ProtocolVersion;

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
}
