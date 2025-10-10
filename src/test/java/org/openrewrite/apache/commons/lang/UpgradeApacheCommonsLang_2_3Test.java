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
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;


class UpgradeApacheCommonsLang_2_3Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("commons-lang", "commons-lang3"))
          .recipeFromResources("org.openrewrite.apache.commons.lang.UpgradeApacheCommonsLang_2_3");
    }

    @DocumentExample
    @Test
    void apacheCommonsLang() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.RandomStringUtils;
              import org.apache.commons.lang.StringUtils;

              import java.util.Map;

              class Test {
                  static void helloApacheLang() {
                     String aaa = StringUtils.repeat("a", 20);
                     String randomString = RandomStringUtils.random(10);
                  }
              }
              """,
            """
              import org.apache.commons.lang3.RandomStringUtils;
              import org.apache.commons.lang3.StringUtils;

              import java.util.Map;

              class Test {
                  static void helloApacheLang() {
                     String aaa = StringUtils.repeat("a", 20);
                     String randomString = RandomStringUtils.random(10);
                  }
              }
              """
          )
        );
    }

    @Test
    void exceptionUtilsGetFullStackTraceToGetStackTrace() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.exception.ExceptionUtils;

              class A {
                  String doSomething(Throwable t) {
                     return ExceptionUtils.getFullStackTrace(t);
                  }
              }
              """,
            """
              import org.apache.commons.lang3.exception.ExceptionUtils;

              class A {
                  String doSomething(Throwable t) {
                     return ExceptionUtils.getStackTrace(t);
                  }
              }
              """
          )
        );
    }

    @Test
    void nullArgumentExceptionChangesToNullPointerException() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.NullArgumentException;

              class A {
                  boolean doSomething(Throwable t) {
                     return t instanceof NullArgumentException;
                  }
              }
              """,
            """
              class A {
                  boolean doSomething(Throwable t) {
                     return t instanceof NullPointerException;
                  }
              }
              """
          )
        );
    }
}
