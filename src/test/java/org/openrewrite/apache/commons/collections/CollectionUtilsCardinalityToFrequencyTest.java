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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;

class CollectionUtilsCardinalityToFrequencyTest implements RewriteTest {
    private static final JavaSourceSet SOURCE_SET = JavaSourceSet.build("main",
      JavaParser.dependenciesFromClasspath("commons-collections4"));

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "commons-collections4-4.4"))
          .recipe(new CollectionUtilsCardinalityToFrequency())
          .allSources(source -> source.markers(javaVersion(21), SOURCE_SET));
    }

    @DocumentExample
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

    @Test
    void includedInApacheCommonsBestPractices() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.apache.commons.ApacheCommonsBestPractices"),
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
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"java.util.List<Object>", "java.util.Set<Object>", "org.apache.commons.collections4.Bag<Object>",
      "Iterable<? super String>", "Iterable"})
    void iterableTypes(String iterableType) {
        rewriteRun(java(
          """
            import org.apache.commons.collections4.CollectionUtils;

            class A {
                int count(String value, %s values) {
                    return CollectionUtils.cardinality(value, values);
                }
            }
            """.formatted(iterableType),
          """
            import org.apache.commons.collections4.IterableUtils;

            import java.util.Objects;

            class A {
                int count(String value, %s values) {
                    return IterableUtils.frequency(Objects.requireNonNull(values), value);
                }
            }
            """.formatted(iterableType)
        ));
    }

    @Test
    void staticImportAndOtherCollectionUtilsMethod() {
        rewriteRun(java(
          """
            import org.apache.commons.collections4.CollectionUtils;

            import java.util.List;

            import static org.apache.commons.collections4.CollectionUtils.cardinality;

            class A {
                int count(String value, List<String> values) {
                    return CollectionUtils.isEmpty(values) ? 0 : cardinality(value, values);
                }
            }
            """,
          """
            import org.apache.commons.collections4.CollectionUtils;
            import org.apache.commons.collections4.IterableUtils;

            import java.util.List;
            import java.util.Objects;

            class A {
                int count(String value, List<String> values) {
                    return CollectionUtils.isEmpty(values) ? 0 : IterableUtils.frequency(Objects.requireNonNull(values), value);
                }
            }
            """
        ));
    }

    @Test
    void fullyQualifiedCall() {
        rewriteRun(java(
          """
            class A {
                int count(String value, Iterable<String> values) {
                    return org.apache.commons.collections4.CollectionUtils.cardinality(value, values);
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
            """
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "CollectionUtils.cardinality(next(), values)",
      "CollectionUtils.cardinality(i++, java.util.Collections.singletonList(i))",
      "CollectionUtils.cardinality(array[0], (Iterable<Object>) notIterable)",
      "CollectionUtils.cardinality(field, values)",
      "CollectionUtils.cardinality(value, getValues())",
      "CollectionUtils.cardinality((String) notIterable, values)",
      "qualifier().cardinality(value, values)",
      "instance.cardinality(value, values)",
      "CollectionUtils.<Object>cardinality(value, values)"
    })
    void retainUnsupportedEvaluationOrTyping(String invocation) {
        rewriteRun(java("""
          import org.apache.commons.collections4.CollectionUtils;

          class A {
              Object field;
              CollectionUtils instance;
              int count(Object value, Iterable<Object> values, int i, Object[] array, Object notIterable) {
                  return %s;
              }
              Object next() { return new Object(); }
              Iterable<Object> getValues() { return null; }
              CollectionUtils qualifier() { throw new IllegalStateException(); }
          }
          """.formatted(invocation)));
    }

    @Test
    void retainClassNameShadowedByLocalVariable() {
        rewriteRun(java("""
          import org.apache.commons.collections4.CollectionUtils;

          class A {
              int count(Object value, Iterable<Object> values) {
                  CollectionUtils CollectionUtils = null;
                  return CollectionUtils.cardinality(value, values);
              }
          }
          """));
    }

    @Test
    void retainMemberReferencesIncludingSameTypedParameters() {
        rewriteRun(java("""
          import org.apache.commons.collections4.CollectionUtils;

          import java.util.function.ToIntBiFunction;

          class A {
              ToIntBiFunction<String, Iterable<? super String>> distinct = CollectionUtils::cardinality;
              ToIntBiFunction<Iterable<Object>, Iterable<Object>> same = CollectionUtils::cardinality;
          }
          """));
    }

    @Test
    void retainUnrelatedOverloadAndExistingFrequency() {
        rewriteRun(java("""
          import org.apache.commons.collections4.IterableUtils;

          class A {
              int cardinality(Object value, Iterable<Object> values) { return 42; }
              int count(Object value, Iterable<Object> values) {
                  return cardinality(value, values) + IterableUtils.frequency(values, value);
              }
          }
          """));
    }

    @Test
    void retainCommonsCollections3() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("commons-collections")),
          java("""
            import org.apache.commons.collections.CollectionUtils;

            class A {
                int count(String value, java.util.Collection<String> values) {
                    return CollectionUtils.cardinality(value, values);
                }
            }
            """)
        );
    }

    @ParameterizedTest
    @CsvSource({
      "nested, Objects", "nested, IterableUtils",
      "parameter, Objects", "parameter, IterableUtils",
      "local, Objects", "local, IterableUtils",
      "imported, Objects", "imported, IterableUtils"
    })
    void targetNamesRemainBoundToTheUtilityClasses(String scope, String name) {
        String importConflict = "imported".equals(scope) ? "import binding.Owner." + name + ";\n\n" : "";
        String nested = "nested".equals(scope) ? "    static class " + name + " {}\n\n" : "";
        String parameter = "parameter".equals(scope) ? ", Object " + name : "";
        String argument = "parameter".equals(scope) ? ", null" : "";
        String local = "local".equals(scope) ? "        Object " + name + " = null;\n" : "";
        String owner = "imported".equals(scope) ? "\nclass Owner {\n    static class " + name + " {}\n}\n" : "";
        String before = """
          package binding;

          %simport org.apache.commons.collections4.CollectionUtils;

          public class RuntimeExample {
          %s    public static String exercise() {
                  Iterable<Object> values = java.util.Arrays.<Object>asList("x", "x", null);
                  return Integer.toString(count("x", values%s));
              }

              static int count(Object value, Iterable<Object> values%s) {
          %s        return CollectionUtils.cardinality(value, values);
              }
          }
          %s""".formatted(importConflict, nested, argument, parameter, local, owner);
        String iterableUtils = "IterableUtils".equals(name) ? "org.apache.commons.collections4.IterableUtils" : "IterableUtils";
        String objects = "Objects".equals(name) ? "java.util.Objects" : "Objects";
        String imports = "IterableUtils".equals(name) ? "import java.util.Objects;\n" :
          "import org.apache.commons.collections4.IterableUtils;\n";
        String expected = before.replace("import org.apache.commons.collections4.CollectionUtils;\n", imports)
          .replace("CollectionUtils.cardinality(value, values)",
            iterableUtils + ".frequency(" + objects + ".requireNonNull(values), value)")
          .replace("import binding.Owner.Objects;\n\nimport org.", "import binding.Owner.Objects;\nimport org.");
        // Compile the actual printed result before asserting its text, including Java name resolution.
        rewriteRun(spec -> spec.cycles(3).expectedCyclesThatMakeChanges(1), java(before,
          source -> source.after(actual -> {
              CardinalityRuntimeSupport.assertEquivalent(
                "binding-" + scope + "-" + name, "binding.RuntimeExample", before, actual, "2");
              return expected;
          })));

    }

    @ParameterizedTest
    @CsvSource({"parameter, java", "parameter, org", "nested, java", "nested, org"})
    void retainShadowedPackageRoot(String scope, String name) {
        String nested = "nested".equals(scope) ? "    static class " + name + " {}\n\n" : "";
        String parameter = "parameter".equals(scope) ? ", Object " + name : "";
        String argument = "parameter".equals(scope) ? ", null" : "";
        String before = """
          import org.apache.commons.collections4.CollectionUtils;

          public class RuntimeExample {
          %s    public static String exercise() {
                  Iterable<Object> values = java.util.Arrays.<Object>asList("x", "x");
                  return Integer.toString(count("x", values%s));
              }

              static int count(Object value, Iterable<Object> values%s) {
                  return CollectionUtils.cardinality(value, values);
              }
          }
          """.formatted(nested, argument, parameter);
        // The nested java type would shadow java.util in exercise too, so import the input's Arrays type.
        before = before.replace("import org.apache.commons.collections4.CollectionUtils;",
          "import org.apache.commons.collections4.CollectionUtils;\n\nimport java.util.Arrays;")
          .replace("java.util.Arrays.<Object>", "Arrays.<Object>");
        String input = before;
        rewriteRun(java(input, source -> source.afterRecipe(cu -> CardinalityRuntimeSupport.assertEquivalent(
          "package-root-" + scope + "-" + name, "RuntimeExample", input, cu.printAll(), "2"))));
    }

    @Test
    void actualOutputCompilesAndPreservesRuntimeBehavior() {
        String before = """
          import org.apache.commons.collections4.CollectionUtils;

          import java.util.Arrays;
          import java.util.Collections;

          public class RuntimeExample {
              private static String trace = "";

              public static String exercise() {
                  Iterable<Object> values = Arrays.<Object>asList("x", null, "x");
                  int duplicates = CollectionUtils.cardinality("x", values);
                  int nulls = CollectionUtils.cardinality(null, values);
                  Iterable<Object> missing = null;
                  String nullFailure;
                  try {
                      CollectionUtils.cardinality("x", missing);
                      nullFailure = "missing";
                  } catch (NullPointerException e) {
                      nullFailure = e.getClass().getSimpleName();
                  }
                  trace = "";
                  CollectionUtils.cardinality(needle(), haystack());
                  String effects = trace;
                  Object[] empty = {};
                  Object wrong = "not iterable";
                  String readFailure;
                  try {
                      CollectionUtils.cardinality(empty[0], (Iterable<Object>) wrong);
                      readFailure = "missing";
                  } catch (RuntimeException e) {
                      readFailure = e.getClass().getSimpleName();
                  }
                  String qualifierFailure;
                  try {
                      qualifier().cardinality("x", values);
                      qualifierFailure = "missing";
                  } catch (IllegalArgumentException e) {
                      qualifierFailure = e.getMessage();
                  }
                  return duplicates + "|" + nulls + "|" + nullFailure + "|" + effects + "|" + readFailure + "|" + qualifierFailure;
              }

              private static Object needle() {
                  trace += "N";
                  return "x";
              }

              private static Iterable<Object> haystack() {
                  trace += "H";
                  return Collections.<Object>singletonList("x");
              }

              private static CollectionUtils qualifier() {
                  throw new IllegalArgumentException("qualifier");
              }
          }
          """;
        String expected = before
          .replace("import org.apache.commons.collections4.CollectionUtils;", "import org.apache.commons.collections4.CollectionUtils;\nimport org.apache.commons.collections4.IterableUtils;")
          .replace("import java.util.Collections;", "import java.util.Collections;\nimport java.util.Objects;")
          .replace("CollectionUtils.cardinality(\"x\", values)", "IterableUtils.frequency(Objects.requireNonNull(values), \"x\")")
          .replace("CollectionUtils.cardinality(null, values)", "IterableUtils.frequency(Objects.requireNonNull(values), null)")
          .replace("CollectionUtils.cardinality(\"x\", missing)", "IterableUtils.frequency(Objects.requireNonNull(missing), \"x\")");
        rewriteRun(java(before, expected,
          source -> source.afterRecipe(cu -> CardinalityRuntimeSupport.assertEquivalent(before, cu.printAll()))));
    }
}
