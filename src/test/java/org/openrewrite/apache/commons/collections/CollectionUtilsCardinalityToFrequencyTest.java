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
package org.openrewrite.apache.commons.collections;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;

class CollectionUtilsCardinalityToFrequencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "commons-collections4"))
          // Tests-only checkpoint: prove that the existing composition lacks this migration.
          .recipeFromResources("org.openrewrite.apache.commons.ApacheCommonsBestPractices")
          .allSources(source -> source.markers(javaVersion(21)));
    }

    @Test
    void reorderArguments() {
        rewriteRun(
          spec -> spec.cycles(3).expectedCyclesThatMakeChanges(1),
          java(
            """
              import org.apache.commons.collections4.CollectionUtils;

              class A {
                  int count(String value, Iterable<String> values) {
                      return CollectionUtils.cardinality(value, values);
                  }
              }
              """,
            """
              import org.apache.commons.collections4.IterableUtils;

              import java.util.Objects;

              class A {
                  int count(String value, Iterable<String> values) {
                      return IterableUtils.frequency(Objects.requireNonNull(values), value);
                  }
              }
              """,
            source -> source.beforeRecipe(cu -> new JavaIsoVisitor<Integer>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                    if ("cardinality".equals(method.getSimpleName())) {
                        assertThat(method.getMethodType()).isNotNull();
                        assertThat(method.getMethodType().getAnnotations())
                          .extracting(JavaType.FullyQualified::getFullyQualifiedName)
                          .contains("java.lang.Deprecated");
                    }
                    return super.visitMethodInvocation(method, p);
                }
            }.visit(cu, 0))
          )
        );
    }

    @Test
    void nullElement() {
        rewriteRun(
          java(
            """
              import org.apache.commons.collections4.CollectionUtils;

              class A {
                  int count(Iterable<String> values) {
                      return CollectionUtils.cardinality(null, values);
                  }
              }
              """,
            """
              import org.apache.commons.collections4.IterableUtils;

              import java.util.Objects;

              class A {
                  int count(Iterable<String> values) {
                      return IterableUtils.frequency(Objects.requireNonNull(values), null);
                  }
              }
              """
          )
        );
    }
}
