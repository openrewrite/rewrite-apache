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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
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
        if (!"4.4".equals(version)) {
            return Collections.singletonList(Paths.get(System.getProperty("commonsCollections" + version.replace(".", "") + "Jar")));
        }
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
