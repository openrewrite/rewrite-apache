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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeStringEntityContentEncodingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient-4",
            "httpcore-4",
            "httpmime-4",
            "httpclient5",
            "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-apache/issues/54")
    void movesAdjacentContentEncodingLiteralToStringEntityConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.entity.ContentType;
              import org.apache.http.entity.StringEntity;

              class A {
                  void foo() {
                      StringEntity se = new StringEntity("", ContentType.APPLICATION_JSON);
                      se.setContentEncoding("utf-8");
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ContentType;
              import org.apache.hc.core5.http.io.entity.StringEntity;

              class A {
                  void foo() {
                      StringEntity se = new StringEntity("", ContentType.APPLICATION_JSON, "utf-8", false);
                  }
              }
              """
          )
        );
    }

    @Test
    void movesIndependentPairsIncludingTypedNullEncoding() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.entity.ContentType;
              import org.apache.http.entity.StringEntity;

              class A {
                  void foo(StringBuilder body) {
                      StringEntity first = new StringEntity(body.toString(), ContentType.APPLICATION_JSON);
                      first.setContentEncoding("gzip");
                      StringEntity second = new StringEntity("second", ContentType.TEXT_PLAIN);
                      second.setContentEncoding((String) null);
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ContentType;
              import org.apache.hc.core5.http.io.entity.StringEntity;

              class A {
                  void foo(StringBuilder body) {
                      StringEntity first = new StringEntity(body.toString(), ContentType.APPLICATION_JSON, "gzip", false);
                      StringEntity second = new StringEntity("second", ContentType.TEXT_PLAIN, (String) null, false);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsUnsupportedLocalShapesWhileMigratingNamespaces() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.entity.ContentType;
              import org.apache.http.entity.StringEntity;

              class A {
                  void foo() {
                      StringEntity noSetter = new StringEntity("no setter", ContentType.APPLICATION_JSON);
                      StringEntity first = new StringEntity("first", ContentType.APPLICATION_JSON),
                        second = new StringEntity("second", ContentType.APPLICATION_JSON);
                      first.setContentEncoding("gzip");
                      StringEntity alias = noSetter;
                      alias.setContentEncoding("gzip");
                      StringEntity delayed = new StringEntity("delayed", ContentType.APPLICATION_JSON);
                      System.out.println("between");
                      delayed.setContentEncoding("gzip");
                      StringEntity computed = new StringEntity("computed", ContentType.APPLICATION_JSON);
                      computed.setContentEncoding(encoding());
                  }

                  String encoding() {
                      return "gzip";
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ContentType;
              import org.apache.hc.core5.http.io.entity.StringEntity;

              class A {
                  void foo() {
                      StringEntity noSetter = new StringEntity("no setter", ContentType.APPLICATION_JSON);
                      StringEntity first = new StringEntity("first", ContentType.APPLICATION_JSON),
                        second = new StringEntity("second", ContentType.APPLICATION_JSON);
                      first.setContentEncoding("gzip");
                      StringEntity alias = noSetter;
                      alias.setContentEncoding("gzip");
                      StringEntity delayed = new StringEntity("delayed", ContentType.APPLICATION_JSON);
                      System.out.println("between");
                      delayed.setContentEncoding("gzip");
                      StringEntity computed = new StringEntity("computed", ContentType.APPLICATION_JSON);
                      computed.setContentEncoding(encoding());
                  }

                  String encoding() {
                      return "gzip";
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsNonlocalAndOtherEntityTargets() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.entity.ContentType;
              import org.apache.http.entity.FileEntity;
              import org.apache.http.entity.StringEntity;

              import java.io.File;

              class A {
                  StringEntity field = new StringEntity("field", ContentType.APPLICATION_JSON);

                  void foo(StringEntity parameter, File file) {
                      field.setContentEncoding("gzip");
                      parameter.setContentEncoding("gzip");
                      FileEntity other = new FileEntity(file, ContentType.APPLICATION_JSON);
                      other.setContentEncoding("gzip");
                      CustomEntity custom = new CustomEntity();
                      custom.setContentEncoding("gzip");
                      StringEntity anonymous = new StringEntity("anonymous", ContentType.APPLICATION_JSON) {};
                      anonymous.setContentEncoding("gzip");
                  }

                  static class CustomEntity extends StringEntity {
                      CustomEntity() {
                          super("custom", ContentType.APPLICATION_JSON);
                      }
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ContentType;
              import org.apache.hc.core5.http.io.entity.FileEntity;
              import org.apache.hc.core5.http.io.entity.StringEntity;

              import java.io.File;

              class A {
                  StringEntity field = new StringEntity("field", ContentType.APPLICATION_JSON);

                  void foo(StringEntity parameter, File file) {
                      field.setContentEncoding("gzip");
                      parameter.setContentEncoding("gzip");
                      FileEntity other = new FileEntity(file, ContentType.APPLICATION_JSON);
                      other.setContentEncoding("gzip");
                      CustomEntity custom = new CustomEntity();
                      custom.setContentEncoding("gzip");
                      StringEntity anonymous = new StringEntity("anonymous", ContentType.APPLICATION_JSON) {};
                      anonymous.setContentEncoding("gzip");
                  }

                  static class CustomEntity extends StringEntity {
                      CustomEntity() {
                          super("custom", ContentType.APPLICATION_JSON);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsCommentedPairAndStillMigratesIndependentPair() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.entity.ContentType;
              import org.apache.http.entity.StringEntity;

              class A {
                  void foo() {
                      StringEntity commented = new StringEntity("commented", ContentType.APPLICATION_JSON);
                      // Preserve wire encoding.
                      commented.setContentEncoding(/* encoding */ "gzip");
                      StringEntity separate = new StringEntity("separate", ContentType.TEXT_PLAIN);
                      separate.setContentEncoding("gzip");
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ContentType;
              import org.apache.hc.core5.http.io.entity.StringEntity;

              class A {
                  void foo() {
                      StringEntity commented = new StringEntity("commented", ContentType.APPLICATION_JSON);
                      // Preserve wire encoding.
                      commented.setContentEncoding(/* encoding */ "gzip");
                      StringEntity separate = new StringEntity("separate", ContentType.TEXT_PLAIN, "gzip", false);
                  }
              }
              """
          )
        );
    }
}
