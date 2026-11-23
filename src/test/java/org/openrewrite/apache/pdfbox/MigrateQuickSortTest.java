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
package org.openrewrite.apache.pdfbox;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateQuickSortTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        PdfBoxTestParsers.pdfBox2To3(spec).recipe(new MigrateQuickSort());
    }

    @DocumentExample
    @Test
    void sortWithIterativeMergeSort() {
        rewriteRun(
          // QuickSort was removed in a PDFBox 2.0 release before the one the tests parse with
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "pdfbox-2")
            //language=java
            .dependsOn(
              """
                package org.apache.pdfbox.util;

                import java.util.Comparator;
                import java.util.List;

                public final class QuickSort {
                    public static <T> void sort(List<T> list, Comparator<T> cmp) {
                    }

                    public static <T extends Comparable<? super T>> void sort(List<T> list) {
                    }
                }
                """
            )),
          //language=java
          java(
            """
              import org.apache.pdfbox.util.QuickSort;

              import java.util.Comparator;
              import java.util.List;

              class A {
                  void sort(List<String> words) {
                      QuickSort.sort(words, Comparator.reverseOrder());
                      QuickSort.sort(words);
                  }
              }
              """,
            """
              import org.apache.pdfbox.util.IterativeMergeSort;

              import java.util.Comparator;
              import java.util.List;

              class A {
                  void sort(List<String> words) {
                      IterativeMergeSort.sort(words, Comparator.reverseOrder());
                      IterativeMergeSort.sort(words, Comparator.naturalOrder());
                  }
              }
              """
          )
        );
    }
}
