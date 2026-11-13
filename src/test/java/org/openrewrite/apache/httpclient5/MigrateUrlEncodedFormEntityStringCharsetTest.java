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
import org.openrewrite.test.RecipeQualityValidation;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateUrlEncodedFormEntityStringCharsetTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient-4", "httpcore-4", "httpclient5", "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @DocumentExample
    @Test
    void literalAndConstantCharsetNames() {
        rewriteRun(
          // ChangePackage and ChangeType keep HttpClient 4's members on the types they rename, such as UrlEncodedFormEntity(List, String)
          spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder().parserTypedTrees(false).build()),
          //language=java
          java(
            """
              import org.apache.http.NameValuePair;
              import org.apache.http.client.entity.UrlEncodedFormEntity;
              import org.apache.http.client.methods.HttpPost;

              import java.io.UnsupportedEncodingException;
              import java.util.List;

              class A {
                  private static final String UTF8 = "UTF-8";

                  void post(HttpPost httpPost, List<NameValuePair> params) throws UnsupportedEncodingException {
                      httpPost.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
                      httpPost.setEntity(new UrlEncodedFormEntity(params, "GBK"));
                      httpPost.setEntity(new UrlEncodedFormEntity(params, UTF8));
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.NameValuePair;
              import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
              import org.apache.hc.client5.http.classic.methods.HttpPost;

              import java.io.UnsupportedEncodingException;
              import java.nio.charset.Charset;
              import java.nio.charset.StandardCharsets;
              import java.util.List;

              class A {
                  private static final String UTF8 = "UTF-8";

                  void post(HttpPost httpPost, List<NameValuePair> params) throws UnsupportedEncodingException {
                      httpPost.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
                      httpPost.setEntity(new UrlEncodedFormEntity(params, Charset.forName("GBK")));
                      httpPost.setEntity(new UrlEncodedFormEntity(params, Charset.forName(UTF8)));
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveArgumentsThatMayBeNull() {
        rewriteRun(
          spec -> spec.recipe(new MigrateUrlEncodedFormEntityStringCharset()),
          //language=java
          java(
            """
              import org.apache.http.NameValuePair;
              import org.apache.http.client.entity.UrlEncodedFormEntity;

              import java.io.UnsupportedEncodingException;
              import java.util.List;

              class A {
                  static final String UNSET = null;

                  void post(List<NameValuePair> params, String encoding) throws UnsupportedEncodingException {
                      new UrlEncodedFormEntity(params, encoding);
                      new UrlEncodedFormEntity(params, UNSET);
                      new UrlEncodedFormEntity(params, "no-such-charset");
                      new UrlEncodedFormEntity(params);
                  }
              }
              """
          )
        );
    }
}
