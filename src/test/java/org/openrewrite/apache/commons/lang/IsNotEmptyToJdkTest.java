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
package org.openrewrite.apache.commons.lang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class IsNotEmptyToJdkTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("commons-lang3", "plexus-utils", "maven-shared-utils"))
          .recipe(new IsNotEmptyToJdk());
    }

    @DocumentExample
    @Test
    void exampleUse() {
        rewriteRun(
          // language=java
          java(
                """
            import org.apache.commons.lang3.StringUtils;

            class A {
                boolean test(String first) {
                    return StringUtils.isEmpty(first);
                }
            }
            """,
          """
            class A {
                boolean test(String first) {
                    return first == null || first.isEmpty();
                }
            }
            """
          ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "org.apache.commons.lang3.StringUtils",
      "org.apache.maven.shared.utils.StringUtils",
      "org.codehaus.plexus.util.StringUtils"
    })
    void trim(String import_) {
        // language=java
        rewriteRun(
          java(
            """
              import %s;
                            
              class A {
                  boolean test(String first) {
                      boolean a = StringUtils.isEmpty(first.trim());
                      boolean b = !StringUtils.isEmpty(first.trim());
                      boolean c = StringUtils.isNotEmpty(first.trim());
                      boolean d = !StringUtils.isNotEmpty(first.trim()); // yeah, this is weird, but not worth cleaning up
                      boolean e = StringUtils.isEmpty(foo().trim()); // Not guaranteed safe to replace
                  }
                  String foo() {
                      return "foo";
                  }
              }
              """.formatted(import_),
            """
              import %s;
                            
              class A {
                  boolean test(String first) {
                      boolean a = first.trim().isEmpty();
                      boolean b = !first.trim().isEmpty();
                      boolean c = !first.trim().isEmpty();
                      boolean d = !!first.trim().isEmpty(); // yeah, this is weird, but not worth cleaning up
                      boolean e = StringUtils.isEmpty(foo().trim()); // Not guaranteed safe to replace
                  }
                  String foo() {
                      return "foo";
                  }
              }
              """.formatted(import_)
          )
        );
    }

    @CsvSource(delimiter = '#', textBlock = """
      org.apache.commons.lang3.StringUtils # StringUtils.isEmpty(first) # first == null || first.isEmpty()
      org.apache.commons.lang3.StringUtils # StringUtils.isEmpty(field) # field == null || field.isEmpty()
      org.apache.commons.lang3.StringUtils # StringUtils.isEmpty(this.field) # this.field == null || this.field.isEmpty()
      org.apache.commons.lang3.StringUtils # StringUtils.isNotEmpty(first) # first != null && !first.isEmpty()
      org.apache.maven.shared.utils.StringUtils # StringUtils.isEmpty(first) # first == null || first.isEmpty()
      org.apache.maven.shared.utils.StringUtils # StringUtils.isNotEmpty(first) # first != null && !first.isEmpty()
      org.codehaus.plexus.util.StringUtils # StringUtils.isEmpty(first) # first == null || first.isEmpty()
      org.codehaus.plexus.util.StringUtils # StringUtils.isNotEmpty(first) # first != null && !first.isEmpty()
      """)
    @ParameterizedTest
    void replaceDirectUse(String classname, String beforeLine, String afterLine) {
        // language=java
        rewriteRun(
          java(
            """
              import %s;

              class A {
                  String field = "foo";
                  boolean test(String first) {
                      return %s;
                  }
              }
              """.formatted(classname, beforeLine),
            """
              class A {
                  String field = "foo";
                  boolean test(String first) {
                      return %s;
                  }
              }
              """.formatted(afterLine)));
    }

    @CsvSource(delimiter = '#', textBlock = """
      org.apache.commons.lang3.StringUtils # !StringUtils.isEmpty(first) # !(first == null || first.isEmpty())
      org.apache.commons.lang3.StringUtils # !StringUtils.isNotEmpty(first) # !(first != null && !first.isEmpty())
      org.apache.commons.lang3.StringUtils # !(StringUtils.isEmpty(first)) # !(first == null || first.isEmpty())
      org.apache.commons.lang3.StringUtils # !(StringUtils.isNotEmpty(first)) # !(first != null && !first.isEmpty())
      org.apache.maven.shared.utils.StringUtils # !StringUtils.isEmpty(first) # !(first == null || first.isEmpty())
      org.apache.maven.shared.utils.StringUtils # !StringUtils.isNotEmpty(first) # !(first != null && !first.isEmpty())
      org.codehaus.plexus.util.StringUtils # !StringUtils.isEmpty(first) # !(first == null || first.isEmpty())
      org.codehaus.plexus.util.StringUtils # !StringUtils.isNotEmpty(first) # !(first != null && !first.isEmpty())
      """)
    @ParameterizedTest
    void replaceNegated(String classname, String beforeLine, String afterLine) {
        // language=java
        rewriteRun(
          java(
            """
              import %s;

              class A {
                  boolean test(String first) {
                      return %s;
                  }
              }
              """.formatted(classname, beforeLine),
            """
              class A {
                  boolean test(String first) {
                      return %s;
                  }
              }
              """.formatted(afterLine)));
    }

    @Test
    void convertSimpleGetters() {
        // language=java
        rewriteRun(
          java(
            """
              class B {
                  String field;
                  String getField() {
                      return field;
                  }
              }
              """
          ),
          java(
            """
              import org.apache.commons.lang3.StringUtils;
                            
              class A {
                  boolean test(B b) {
                      return StringUtils.isEmpty(b.getField());
                  }
              }
              """,
            """
              class A {
                  boolean test(B b) {
                      return b.getField() == null || b.getField().isEmpty();
                  }
              }
              """
          )
        );
    }

    @CsvSource(delimiter = '#', textBlock = """
      org.apache.commons.lang3.StringUtils # StringUtils.isEmpty(foo())
      org.apache.commons.lang3.StringUtils # StringUtils.isEmpty(first + second)
      org.apache.commons.lang3.StringUtils # StringUtils.isNotEmpty(foo())
      org.apache.commons.lang3.StringUtils # StringUtils.isNotEmpty(first + second)
      org.apache.maven.shared.utils.StringUtils # StringUtils.isEmpty(foo())
      org.codehaus.plexus.util.StringUtils # StringUtils.isEmpty(foo())
      """)
    @ParameterizedTest
    void retainComplexUse(String classname, String beforeLine) {
        // language=java
        rewriteRun(
          java(
            """
              import %s;

              class A {
                  boolean test(String first, String second) {
                      return %s;
                  }
                  private String foo() {
                      return "foo";
                  }
              }
              """.formatted(classname, beforeLine)));
    }
}
