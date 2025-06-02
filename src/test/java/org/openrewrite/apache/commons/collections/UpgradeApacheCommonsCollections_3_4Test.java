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

import static org.openrewrite.java.Assertions.java;

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
                      IdentityHashMap identityHashMap = new IdentityHashMap();
                      Map emptyMap = Collections.emptyMap();
                      CopyOnWriteArrayList fastList = new CopyOnWriteArrayList(100);
                      List emptyList = Collections.emptyList();
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-apache/issues/55")
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
}
