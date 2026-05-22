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
package org.openrewrite.apache.commons.lang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;

class DefaultIfBlankToJdkTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "commons-lang3"))
          .recipe(new DefaultIfBlankToJdk())
          .allSources(s -> s.markers(javaVersion(21)));
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
                  String test(String input) {
                      return StringUtils.defaultIfBlank(input, "default");
                  }
              }
              """,
            """
              class A {
                  String test(String input) {
                      return input == null || input.isBlank() ? "default" : input;
                  }
              }
              """
          ));
    }

    @CsvSource(delimiter = '#', commentCharacter = '\0', textBlock = """
      org.apache.commons.lang3.StringUtils # StringUtils.defaultIfBlank(first, "fallback") # first == null || first.isBlank() ? "fallback" : first
      org.apache.commons.lang3.StringUtils # StringUtils.defaultIfBlank(field, "fallback") # field == null || field.isBlank() ? "fallback" : field
      """)
    @ParameterizedTest
    void replaceDirectUse(String classname, String beforeLine, String afterLine) {
        rewriteRun(
          java(
            """
              import %s;

              class A {
                  String field = "foo";
                  String test(String first) {
                      return %s;
                  }
              }
              """.formatted(classname, beforeLine),
            """
              class A {
                  String field = "foo";
                  String test(String first) {
                      return %s;
                  }
              }
              """.formatted(afterLine)));
    }

    @CsvSource(delimiter = '#', commentCharacter = '\0', textBlock = """
      org.apache.commons.lang3.StringUtils # StringUtils.defaultIfBlank(foo(), "fallback")
      org.apache.commons.lang3.StringUtils # StringUtils.defaultIfBlank(first + second, "fallback")
      """)
    @ParameterizedTest
    void retainComplexUse(String classname, String beforeLine) {
        rewriteRun(
          java(
            """
              import %s;

              class A {
                  String test(String first, String second) {
                      return %s;
                  }
                  private String foo() {
                      return "foo";
                  }
              }
              """.formatted(classname, beforeLine)));
    }
}
