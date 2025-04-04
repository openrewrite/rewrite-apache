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
package org.openrewrite.apache.httpclient4;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateDefaultHttpClientTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("httpclient", "httpcore"))
          .recipe(new MigrateDefaultHttpClient());
    }

    @DocumentExample
    @Test
    void noArgsDefaultHttpClient() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpResponse;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.impl.client.DefaultHttpClient;

              import java.io.IOException;

              class A {
                  void method() throws IOException {
                      DefaultHttpClient httpClient = new DefaultHttpClient();
                      HttpPost httpPost = new HttpPost("https://moderne.io");
                      HttpResponse httpResponse = httpClient.execute(httpPost);
                  }
              }
              """, """
              import org.apache.http.HttpResponse;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClients;

              import java.io.IOException;

              class A {
                  void method() throws IOException {
                      CloseableHttpClient httpClient = HttpClients.createDefault();
                      HttpPost httpPost = new HttpPost("https://moderne.io");
                      HttpResponse httpResponse = httpClient.execute(httpPost);
                  }
              }
              """
          )
        );
    }
}
