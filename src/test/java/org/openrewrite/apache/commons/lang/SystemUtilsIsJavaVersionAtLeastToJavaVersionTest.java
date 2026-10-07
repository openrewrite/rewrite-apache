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
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SystemUtilsIsJavaVersionAtLeastToJavaVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("commons-lang", "commons-lang3"))
          .recipe(new SystemUtilsIsJavaVersionAtLeastToJavaVersion());
    }

    @DocumentExample
    @Test
    void replaceFloatArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.SystemUtils;

              class A {
                  boolean atLeast14() {
                      return SystemUtils.isJavaVersionAtLeast(1.4f);
                  }
              }
              """,
            """
              import org.apache.commons.lang.SystemUtils;
              import org.apache.commons.lang3.JavaVersion;

              class A {
                  boolean atLeast14() {
                      return SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_1_4);
                  }
              }
              """
          )
        );
    }

    @CsvSource(delimiter = '#', commentCharacter = '\0', textBlock = """
      1.1f # JAVA_1_1
      1.8f # JAVA_1_8
      1.9f # JAVA_9
      110  # JAVA_1_1
      150  # JAVA_1_5
      190  # JAVA_9
      """)
    @ParameterizedTest
    void replaceAllKnownVersions(String before, String constant) {
        // language=java
        rewriteRun(
          java(
            """
              import org.apache.commons.lang.SystemUtils;

              class A {
                  boolean test() {
                      return SystemUtils.isJavaVersionAtLeast(%s);
                  }
              }
              """.formatted(before),
            """
              import org.apache.commons.lang.SystemUtils;
              import org.apache.commons.lang3.JavaVersion;

              class A {
                  boolean test() {
                      return SystemUtils.isJavaVersionAtLeast(JavaVersion.%s);
                  }
              }
              """.formatted(constant)));
    }

    @ValueSource(strings = {"131", "1.35f", "required", "200"})
    @ParameterizedTest
    void retainUnmappableArgument(String before) {
        // language=java
        rewriteRun(
          java(
            """
              import org.apache.commons.lang.SystemUtils;

              class A {
                  boolean test(int required) {
                      return SystemUtils.isJavaVersionAtLeast(%s);
                  }
              }
              """.formatted(before)));
    }
}
