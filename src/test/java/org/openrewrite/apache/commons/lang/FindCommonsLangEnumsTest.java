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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FindCommonsLangEnumsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "commons-lang-2"))
          .recipe(new FindCommonsLangEnums());
    }

    @DocumentExample
    @Test
    void flagEnumsTypes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.enums.EnumUtils;
              import org.apache.commons.lang.enums.ValuedEnum;

              import java.util.List;

              class Priority extends ValuedEnum {
                  Priority(String name, int value) {
                      super(name, value);
                  }

                  static List<?> all() {
                      return EnumUtils.getEnumList(Priority.class);
                  }
              }
              """,
            """
              import org.apache.commons.lang.enums.EnumUtils;
              import org.apache.commons.lang.enums.ValuedEnum;

              import java.util.List;

              class Priority extends /*~~(Commons Lang 3 has no `enums` package; replace with a Java `enum`)~~>*/ValuedEnum {
                  Priority(String name, int value) {
                      super(name, value);
                  }

                  static List<?> all() {
                      return /*~~(Commons Lang 3 has no `enums` package; replace with a Java `enum`)~~>*/EnumUtils.getEnumList(Priority.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void flagFullyQualifiedReference() {
        rewriteRun(
          //language=java
          java(
            """
              class Color extends org.apache.commons.lang.enums.Enum {
                  Color(String name) {
                      super(name);
                  }
              }
              """,
            """
              class Color extends /*~~(Commons Lang 3 has no `enums` package; replace with a Java `enum`)~~>*/org.apache.commons.lang.enums.Enum {
                  Color(String name) {
                      super(name);
                  }
              }
              """
          )
        );
    }
}
