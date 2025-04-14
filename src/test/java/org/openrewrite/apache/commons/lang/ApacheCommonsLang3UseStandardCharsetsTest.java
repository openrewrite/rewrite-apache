package org.openrewrite.apache.commons.lang;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ApacheCommonsLang3UseStandardCharsetsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("commons-lang3"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite")
            .build()
            .activateRecipes("org.openrewrite.apache.commons.lang3.UseStandardCharsets"));
    }

    @SuppressWarnings({"UnusedAssignment", "deprecation"})
    @Test
    @DocumentExample
    void foo() {
        // language=java
        rewriteRun(
          java(
            """
              import org.apache.commons.lang3.CharEncoding;

              class A {
                  String test() {
                      String encoding = CharEncoding.ISO_8859_1;
                      encoding = CharEncoding.US_ASCII;
                      encoding = CharEncoding.UTF_16;
                      encoding = CharEncoding.UTF_16BE;
                      encoding = CharEncoding.UTF_16LE;
                      encoding = CharEncoding.UTF_8;
                      return encoding;
                  }
              }
              """,
            """
              import java.nio.charset.StandardCharsets;

              class A {
                  String test() {
                      String encoding = StandardCharsets.ISO_8859_1.name();
                      encoding = StandardCharsets.US_ASCII.name();
                      encoding = StandardCharsets.UTF_16.name();
                      encoding = StandardCharsets.UTF_16BE.name();
                      encoding = StandardCharsets.UTF_16LE.name();
                      encoding = StandardCharsets.UTF_8.name();
                      return encoding;
                  }
              }
              """));
    }
}
