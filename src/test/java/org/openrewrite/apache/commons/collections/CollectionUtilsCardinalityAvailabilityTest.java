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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;

class CollectionUtilsCardinalityAvailabilityTest {
    private static final String BEFORE = """
      import org.apache.commons.collections4.CollectionUtils;

      class A {
          int count(String value, Iterable<String> values) {
              return CollectionUtils.cardinality(value, values);
          }
      }
      """;

    private static List<Path> classpath(String version) {
        return JavaParser.dependenciesFromResources(new InMemoryExecutionContext(), "commons-collections4-" + version);
    }

    @Nested
    class Commons40 implements RewriteTest {
        private final List<Path> classpath = classpath("4.0");
        private final JavaSourceSet sourceSet = JavaSourceSet.build("main", classpath);

        @Override
        public void defaults(RecipeSpec spec) {
            assertThat(sourceSet.getClasspath()).extracting(JavaType.FullyQualified::getFullyQualifiedName)
              .contains("org.apache.commons.collections4.CollectionUtils")
              .doesNotContain("org.apache.commons.collections4.IterableUtils");
            spec.recipe(new CollectionUtilsCardinalityToFrequency())
              .parser(JavaParser.fromJavaVersion().classpath(classpath))
              .allSources(source -> source.markers(javaVersion(21), sourceSet));
        }

        @Test
        void retainWithoutReplacementOnReal40Classpath() {
            rewriteRun(java(BEFORE));
        }
    }

    @Nested
    class Commons41 implements RewriteTest {
        private final List<Path> classpath = classpath("4.1");
        private final JavaSourceSet sourceSet = JavaSourceSet.build("main", classpath);

        @Override
        public void defaults(RecipeSpec spec) {
            assertThat(sourceSet.getClasspath()).extracting(JavaType.FullyQualified::getFullyQualifiedName)
              .contains("org.apache.commons.collections4.IterableUtils");
            spec.recipe(new CollectionUtilsCardinalityToFrequency())
              .parser(JavaParser.fromJavaVersion().classpath(classpath))
              .allSources(source -> source.markers(javaVersion(21), sourceSet));
        }

        @Test
        void migrateWithReal41TargetAvailable() {
            rewriteRun(java(BEFORE, """
              import org.apache.commons.collections4.IterableUtils;

              import java.util.Objects;

              class A {
                  int count(String value, Iterable<String> values) {
                      return IterableUtils.frequency(Objects.requireNonNull(values), value);
                  }
              }
              """));
        }
    }

    @Nested
    class NoSourceSet implements RewriteTest {
        @Override
        public void defaults(RecipeSpec spec) {
            spec.recipe(new CollectionUtilsCardinalityToFrequency())
              .parser(JavaParser.fromJavaVersion().classpath(classpath("4.4")))
              .allSources(source -> source.markers(javaVersion(21)));
        }

        @Test
        void missingAvailabilityEvidenceIsNotSuccess() {
            rewriteRun(java(BEFORE));
        }
    }

    @Nested
    class Java7 implements RewriteTest {
        @Override
        public void defaults(RecipeSpec spec) {
            List<Path> classpath = classpath("4.1");
            spec.recipe(new CollectionUtilsCardinalityToFrequency())
              .parser(JavaParser.fromJavaVersion().classpath(classpath))
              .allSources(source -> source.markers(javaVersion(7), JavaSourceSet.build("main", classpath)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"null", "(null)", "((null))"})
        void retainAlwaysNullIterableWhileMigratingNullNeedle(String iterable) {
            String before = """
              import org.apache.commons.collections4.CollectionUtils;

              import java.util.Arrays;

              public class RuntimeExample {
                  public static String exercise() {
                      Iterable<Object> values = Arrays.<Object>asList("x", null, "x");
                      int nulls = CollectionUtils.cardinality(null, values);
                      try {
                          CollectionUtils.cardinality("x", %s);
                          return "missing";
                      } catch (NullPointerException e) {
                          return nulls + "|" + e.getClass().getSimpleName();
                      }
                  }
              }
              """.formatted(iterable);
            String expected = before
              .replace("import org.apache.commons.collections4.CollectionUtils;",
                "import org.apache.commons.collections4.CollectionUtils;\nimport org.apache.commons.collections4.IterableUtils;")
              .replace("import java.util.Arrays;", "import java.util.Arrays;\nimport java.util.Objects;")
              .replace("CollectionUtils.cardinality(null, values)", "IterableUtils.frequency(Objects.requireNonNull(values), null)");
            rewriteRun(java(before, expected));
        }

        @ParameterizedTest
        @ValueSource(strings = {"value", "null"})
        void migrateTypedNullableIterableAtMinimumJavaVersion(String element) {
            String before = """
              import org.apache.commons.collections4.CollectionUtils;

              import java.util.Arrays;

              public class RuntimeExample {
                  public static String exercise() {
                      Iterable<Object> values = Arrays.<Object>asList("x", null, "x");
                      int matches = count("x", values);
                      try {
                          count("x", null);
                          return "missing";
                      } catch (NullPointerException e) {
                          return matches + "|" + e.getClass().getSimpleName();
                      }
                  }

                  static int count(Object value, Iterable<Object> values) {
                      return CollectionUtils.cardinality(%s, values);
                  }
              }
              """.formatted(element);
            String expected = before.replace("import org.apache.commons.collections4.CollectionUtils;",
                "import org.apache.commons.collections4.IterableUtils;")
              .replace("import java.util.Arrays;", "import java.util.Arrays;\nimport java.util.Objects;")
              .replace("CollectionUtils.cardinality(" + element + ", values)",
                "IterableUtils.frequency(Objects.requireNonNull(values), " + element + ")");
            rewriteRun(java(before, expected));
        }
    }

    @Nested
    class Java6 implements RewriteTest {
        @Override
        public void defaults(RecipeSpec spec) {
            List<Path> classpath = classpath("4.1");
            spec.recipe(new CollectionUtilsCardinalityToFrequency())
              .parser(JavaParser.fromJavaVersion().classpath(classpath))
              .allSources(source -> source.markers(javaVersion(6), JavaSourceSet.build("main", classpath)));
        }

        @Test
        void doNotIntroduceObjectsBeforeJava7() {
            rewriteRun(java(BEFORE));
        }
    }
}
