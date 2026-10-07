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
package org.openrewrite.apache.commons.collections;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeApacheCommonsCollections_3_4Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("commons-collections"))
          .recipeFromResources("org.openrewrite.apache.commons.collections.UpgradeApacheCommonsCollections_3_4");
    }

    @DocumentExample
    @Test
    void apacheCommonsCollections() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.collections.CollectionUtils;
              import org.apache.commons.collections.map.IdentityMap;
              import org.apache.commons.collections.ListUtils;
              import org.apache.commons.collections.MapUtils;
              import org.apache.commons.collections.FastArrayList;

              import java.util.List;
              import java.util.Map;

              class Test {
                  static void helloApacheCollections() {
                      Object[] input = new Object[] { "one", "two" };
                      CollectionUtils.reverseArray(input);
                      IdentityMap identityMap = new IdentityMap();
                      Map emptyMap = MapUtils.EMPTY_MAP;
                      FastArrayList fastList = new FastArrayList(100);
                      List emptyList = ListUtils.EMPTY_LIST;
                  }
              }
              """,
            """
              import org.apache.commons.collections4.CollectionUtils;

              import java.util.Collections;
              import java.util.IdentityHashMap;
              import java.util.List;
              import java.util.Map;
              import java.util.concurrent.CopyOnWriteArrayList;

              class Test {
                  static void helloApacheCollections() {
                      Object[] input = new Object[] { "one", "two" };
                      CollectionUtils.reverseArray(input);
                      IdentityHashMap identityMap = new IdentityHashMap();
                      Map emptyMap = Collections.emptyMap();
                      CopyOnWriteArrayList fastList = new CopyOnWriteArrayList();
                      List emptyList = Collections.emptyList();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-apache/issues/55")
    @Test
    void hashedMap() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.collections.map.HashedMap;
              class Test {}
              """,
            """
              import org.apache.commons.collections4.map.HashedMap;
              class Test {}
              """
          )
        );
    }

    @Test
    void migrateDependencies() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0.0</version>
                  <properties>
                      <commons-collections.version>3.2.2</commons-collections.version>
                  </properties>
                  <dependencies>
                      <dependency>
                          <groupId>commons-collections</groupId>
                          <artifactId>commons-collections</artifactId>
                          <version>${commons-collections.version}</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher version = Pattern.compile("<commons-collections\\.version>(4\\.\\d+\\.\\d+)</commons-collections\\.version>").matcher(pom);
                assertThat(version.find()).describedAs("Expected 4.x in %s", pom).isTrue();
                //language=xml
                return """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                      <properties>
                          <commons-collections.version>%s</commons-collections.version>
                      </properties>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.commons</groupId>
                              <artifactId>commons-collections4</artifactId>
                              <version>${commons-collections.version}</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(version.group(1));
            })
          )
        );
    }

    @Test
    void addCollections4WhenCollections3IsOnlyTransitive() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>commons-beanutils</groupId>
                            <artifactId>commons-beanutils</artifactId>
                            <version>1.9.4</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>commons-beanutils</artifactId>")
                .contains("<artifactId>commons-collections4</artifactId>")
                .containsPattern("<version>4\\.\\d+\\.\\d+</version>")
                .actual())
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.apache.commons.collections.CollectionUtils;

                  import java.util.List;

                  class A {
                      boolean empty(List<String> list) {
                          return CollectionUtils.isEmpty(list);
                      }
                  }
                  """,
                """
                  import org.apache.commons.collections4.CollectionUtils;

                  import java.util.List;

                  class A {
                      boolean empty(List<String> list) {
                          return CollectionUtils.isEmpty(list);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void addCollections4ToChildModuleWhenCollections3IsOnlyTransitive() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                      <module>child</module>
                  </modules>
              </project>
              """
          ),
          mavenProject("child",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>child</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>commons-beanutils</groupId>
                            <artifactId>commons-beanutils</artifactId>
                            <version>1.9.4</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>commons-collections4</artifactId>")
                .actual())
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.apache.commons.collections.CollectionUtils;

                  import java.util.List;

                  class A {
                      boolean empty(List<String> list) {
                          return CollectionUtils.isEmpty(list);
                      }
                  }
                  """,
                """
                  import org.apache.commons.collections4.CollectionUtils;

                  import java.util.List;

                  class A {
                      boolean empty(List<String> list) {
                          return CollectionUtils.isEmpty(list);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void doNotAddCollections4TwiceWhenCollections3IsDeclared() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>commons-collections</groupId>
                            <artifactId>commons-collections</artifactId>
                            <version>3.2.2</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> {
                  assertThat(pom)
                    .doesNotContain("<groupId>commons-collections</groupId>")
                    .containsOnlyOnce("<artifactId>commons-collections4</artifactId>");
                  return pom;
              })
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.apache.commons.collections.CollectionUtils;

                  import java.util.List;

                  class A {
                      boolean empty(List<String> list) {
                          return CollectionUtils.isEmpty(list);
                      }
                  }
                  """,
                """
                  import org.apache.commons.collections4.CollectionUtils;

                  import java.util.List;

                  class A {
                      boolean empty(List<String> list) {
                          return CollectionUtils.isEmpty(list);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void fastArrayListToCopyOnWriteArrayList() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.collections.FastArrayList;

              class A {
                  FastArrayList make() {
                      FastArrayList list = new FastArrayList(100);
                      list.setFast(true);
                      return list;
                  }
              }
              """,
            """
              import java.util.concurrent.CopyOnWriteArrayList;

              class A {
                  CopyOnWriteArrayList make() {
                      CopyOnWriteArrayList list = new CopyOnWriteArrayList();
                      return list;
                  }
              }
              """
          )
        );
    }

    @Test
    void referenceStrengthConstants() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.collections.map.AbstractReferenceMap;
              import org.apache.commons.collections.map.ReferenceMap;

              import java.util.Map;

              class A {
                  Map<String, Object> soft = new ReferenceMap(ReferenceMap.SOFT, ReferenceMap.SOFT);
                  Map<String, Object> weak = new ReferenceMap(AbstractReferenceMap.HARD, AbstractReferenceMap.WEAK, true);
              }
              """,
            """
              import org.apache.commons.collections4.map.ReferenceMap;
              import org.apache.commons.collections4.map.AbstractReferenceMap.ReferenceStrength;

              import java.util.Map;

              class A {
                  Map<String, Object> soft = new ReferenceMap(ReferenceStrength.SOFT, ReferenceStrength.SOFT);
                  Map<String, Object> weak = new ReferenceMap(ReferenceStrength.HARD, ReferenceStrength.WEAK, true);
              }
              """
          )
        );
    }

    @Test
    void deprecatedRootReferenceMap() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.collections.ReferenceMap;

              import java.util.Map;

              class A {
                  Map<String, Object> cache = new ReferenceMap(ReferenceMap.SOFT, ReferenceMap.SOFT);
              }
              """,
            """
              import org.apache.commons.collections4.map.AbstractReferenceMap.ReferenceStrength;
              import org.apache.commons.collections4.map.ReferenceMap;

              import java.util.Map;

              class A {
                  Map<String, Object> cache = new ReferenceMap(ReferenceStrength.SOFT, ReferenceStrength.SOFT);
              }
              """
          )
        );
    }

    @Test
    void renamedDecoratorFactories() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.collections.FactoryUtils;
              import org.apache.commons.collections.list.GrowthList;
              import org.apache.commons.collections.list.LazyList;
              import org.apache.commons.collections.map.TransformedMap;
              import org.apache.commons.collections.set.MapBackedSet;
              import org.apache.commons.collections.TransformerUtils;

              import java.util.ArrayList;
              import java.util.HashMap;
              import java.util.IdentityHashMap;
              import java.util.List;
              import java.util.Map;
              import java.util.Set;

              class A {
                  List<Object> list = GrowthList.decorate(LazyList.decorate(new ArrayList<>(), FactoryUtils.nullFactory()));
                  Set<Object> set = MapBackedSet.decorate(new IdentityHashMap<>());
                  Map<Object, Object> lazy = TransformedMap.decorate(new HashMap<>(), TransformerUtils.nopTransformer(), null);
                  Map<Object, Object> eager = TransformedMap.decorateTransform(new HashMap<>(), TransformerUtils.nopTransformer(), null);
              }
              """,
            """
              import org.apache.commons.collections4.FactoryUtils;
              import org.apache.commons.collections4.list.GrowthList;
              import org.apache.commons.collections4.list.LazyList;
              import org.apache.commons.collections4.map.TransformedMap;
              import org.apache.commons.collections4.set.MapBackedSet;
              import org.apache.commons.collections4.TransformerUtils;

              import java.util.ArrayList;
              import java.util.HashMap;
              import java.util.IdentityHashMap;
              import java.util.List;
              import java.util.Map;
              import java.util.Set;

              class A {
                  List<Object> list = GrowthList.growthList(LazyList.lazyList(new ArrayList<>(), FactoryUtils.nullFactory()));
                  Set<Object> set = MapBackedSet.mapBackedSet(new IdentityHashMap<>());
                  Map<Object, Object> lazy = TransformedMap.transformingMap(new HashMap<>(), TransformerUtils.nopTransformer(), null);
                  Map<Object, Object> eager = TransformedMap.transformedMap(new HashMap<>(), TransformerUtils.nopTransformer(), null);
              }
              """
          )
        );
    }

    @Test
    void renamedFunctorFactories() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.collections.Predicate;
              import org.apache.commons.collections.functors.InstanceofPredicate;
              import org.apache.commons.collections.functors.NOPTransformer;
              import org.apache.commons.collections.functors.NotPredicate;
              import org.apache.commons.collections.Transformer;

              class A {
                  Predicate notString = NotPredicate.getInstance(InstanceofPredicate.getInstance(String.class));
                  Transformer nop = NOPTransformer.getInstance();
              }
              """,
            """
              import org.apache.commons.collections4.Predicate;
              import org.apache.commons.collections4.functors.InstanceofPredicate;
              import org.apache.commons.collections4.functors.NOPTransformer;
              import org.apache.commons.collections4.functors.NotPredicate;
              import org.apache.commons.collections4.Transformer;

              class A {
                  Predicate notString = NotPredicate.notPredicate(InstanceofPredicate.instanceOfPredicate(String.class));
                  Transformer nop = NOPTransformer.nopTransformer();
              }
              """
          )
        );
    }

    @Test
    void synchronizedSetToJdk() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.collections.set.SynchronizedSet;

              import java.util.HashSet;
              import java.util.Set;

              class A {
                  Set<Object> set = SynchronizedSet.decorate(new HashSet<>());
              }
              """,
            """
              import java.util.Collections;
              import java.util.HashSet;
              import java.util.Set;

              class A {
                  Set<Object> set = Collections.synchronizedSet(new HashSet<>());
              }
              """
          )
        );
    }

    @Test
    void emptySet() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.collections.SetUtils;

              import java.util.Set;

              class A {
                  Set<String> none = SetUtils.EMPTY_SET;
              }
              """,
            """
              import java.util.Collections;
              import java.util.Set;

              class A {
                  Set<String> none = Collections.emptySet();
              }
              """
          )
        );
    }

    @Test
    void orderedMapIterator() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.collections.MapIterator;
              import org.apache.commons.collections.map.LinkedMap;

              class A {
                  MapIterator iterate(LinkedMap map) {
                      return map.orderedMapIterator();
                  }
              }
              """,
            """
              import org.apache.commons.collections4.MapIterator;
              import org.apache.commons.collections4.map.LinkedMap;

              class A {
                  MapIterator iterate(LinkedMap map) {
                      return map.mapIterator();
                  }
              }
              """
          )
        );
    }
}
