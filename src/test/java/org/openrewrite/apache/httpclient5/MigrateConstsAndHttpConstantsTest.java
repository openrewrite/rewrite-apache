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

class MigrateConstsAndHttpConstantsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient-4", "httpcore-4", "httpclient5", "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @DocumentExample
    @Test
    void constsCharsetsToStandardCharsets() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.Consts;
              import org.apache.http.HttpEntity;
              import org.apache.http.util.EntityUtils;

              import java.io.IOException;
              import java.nio.charset.Charset;

              class A {
                  String body(HttpEntity entity) throws IOException {
                      return EntityUtils.toString(entity, Consts.UTF_8);
                  }

                  Charset[] all() {
                      return new Charset[]{Consts.UTF_8, Consts.ASCII, Consts.ISO_8859_1};
                  }

                  boolean lineEnd(int b) {
                      return b == Consts.CR || b == Consts.LF;
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.Chars;
              import org.apache.hc.core5.http.io.entity.EntityUtils;
              import org.apache.hc.core5.http.HttpEntity;

              import java.io.IOException;
              import java.nio.charset.Charset;
              import java.nio.charset.StandardCharsets;

              class A {
                  String body(HttpEntity entity) throws IOException {
                      return EntityUtils.toString(entity, StandardCharsets.UTF_8);
                  }

                  Charset[] all() {
                      return new Charset[]{StandardCharsets.UTF_8, StandardCharsets.US_ASCII, StandardCharsets.ISO_8859_1};
                  }

                  boolean lineEnd(int b) {
                      return b == Chars.CR || b == Chars.LF;
                  }
              }
              """
          )
        );
    }

    @Test
    void httpHeaderNamesAndValues() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.protocol.HTTP;

              class A {
                  void headers(HttpPost post) {
                      post.setHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);
                      post.setHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
                      post.setHeader(HTTP.TRANSFER_ENCODING, HTTP.CHUNK_CODING);
                      post.setHeader(HTTP.CONTENT_TYPE, HTTP.PLAIN_TEXT_TYPE + HTTP.CHARSET_PARAM + HTTP.UTF_8);
                      post.setHeader(HTTP.TARGET_HOST, "example.com");
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.classic.methods.HttpPost;
              import org.apache.hc.core5.http.HeaderElements;
              import org.apache.hc.core5.http.HttpHeaders;

              class A {
                  void headers(HttpPost post) {
                      post.setHeader(HttpHeaders.CONNECTION, "Close");
                      post.setHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
                      post.setHeader(HttpHeaders.TRANSFER_ENCODING, HeaderElements.CHUNKED_ENCODING);
                      post.setHeader(HttpHeaders.CONTENT_TYPE, "text/plain" + "; charset=" + "UTF-8");
                      post.setHeader(HttpHeaders.HOST, "example.com");
                  }
              }
              """
          )
        );
    }

    @Test
    void httpCharsetsAndCharacters() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.entity.StringEntity;
              import org.apache.http.protocol.HTTP;

              import java.nio.charset.Charset;

              class A {
                  StringEntity entity(String body) {
                      return new StringEntity(body, HTTP.DEF_CONTENT_CHARSET);
                  }

                  Charset protocol() {
                      return HTTP.DEF_PROTOCOL_CHARSET;
                  }

                  boolean space(int b) {
                      return b == HTTP.SP || b == HTTP.HT;
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.Chars;
              import org.apache.hc.core5.http.io.entity.StringEntity;

              import java.nio.charset.Charset;
              import java.nio.charset.StandardCharsets;

              class A {
                  StringEntity entity(String body) {
                      return new StringEntity(body, StandardCharsets.ISO_8859_1);
                  }

                  Charset protocol() {
                      return StandardCharsets.US_ASCII;
                  }

                  boolean space(int b) {
                      return b == Chars.SP || b == Chars.HT;
                  }
              }
              """
          )
        );
    }
}
