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
package org.openrewrite.apache.commons.configuration;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeQualityValidation;
import org.openrewrite.test.RecipeQualityValidation.ParserTypes;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

class MigrateUntypedListGettersTest implements RewriteTest {

    /**
     * A configuration as the package move leaves one of 1.6: the calls keep the raw {@code List} that releases before
     * 1.7 returned, where the type tables hold 1.10, whose {@code getList} is generic already, and the class has the
     * typed {@code getList} of 2.x.
     */
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "commons-configuration2")
            //language=java
            .dependsOn(
              """
                package org.apache.commons.configuration2;
                public class LegacyConfiguration {
                    public java.util.List getList(String key) {
                        return null;
                    }
                    public java.util.List getList(String key, java.util.List defaultValue) {
                        return null;
                    }
                    public <T> java.util.List<T> getList(Class<T> cls, String key, java.util.List<T> defaultValue) {
                        return null;
                    }
                }
                """
            ))
          .recipe(new MigrateUntypedListGetters())
          // The calls keep the types of 1.x, where the code the recipe writes has those of 2.x
          .recipeQualityValidation(RecipeQualityValidation.builder().parserTypedTrees(ParserTypes.Off).build());
    }

    @DocumentExample
    @Test
    void listOfStrings() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.configuration2.LegacyConfiguration;

              import java.util.List;

              class Brokers {
                  List<String> brokers(LegacyConfiguration conf) {
                      List<String> brokers = conf.getList("kafka.brokers");
                      return brokers;
                  }
              }
              """,
            """
              import org.apache.commons.configuration2.LegacyConfiguration;

              import java.util.ArrayList;
              import java.util.List;

              class Brokers {
                  List<String> brokers(LegacyConfiguration conf) {
                      List<String> brokers = conf.getList(String.class, "kafka.brokers", new ArrayList<>());
                      return brokers;
                  }
              }
              """
          )
        );
    }

    @Test
    void assignedAndReturnedWithDefault() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.configuration2.LegacyConfiguration;

              import java.util.Collections;
              import java.util.List;

              class Brokers {
                  List<Integer> ports;

                  List<String> brokers(LegacyConfiguration conf) {
                      ports = conf.getList("kafka.ports");
                      return conf.getList("kafka.brokers", Collections.emptyList());
                  }
              }
              """,
            """
              import org.apache.commons.configuration2.LegacyConfiguration;

              import java.util.ArrayList;
              import java.util.Collections;
              import java.util.List;

              class Brokers {
                  List<Integer> ports;

                  List<String> brokers(LegacyConfiguration conf) {
                      ports = conf.getList(Integer.class, "kafka.ports", new ArrayList<>());
                      return conf.getList(String.class, "kafka.brokers", Collections.emptyList());
                  }
              }
              """
          )
        );
    }

    @Test
    void sourceBeforeJava8() {
        rewriteRun(
          //language=java
          version(
            java(
              """
                import org.apache.commons.configuration2.LegacyConfiguration;

                import java.util.List;

                class Brokers {
                    List<String> brokers(LegacyConfiguration conf) {
                        return conf.getList("kafka.brokers");
                    }
                }
                """,
              """
                import org.apache.commons.configuration2.LegacyConfiguration;

                import java.util.ArrayList;
                import java.util.List;

                class Brokers {
                    List<String> brokers(LegacyConfiguration conf) {
                        return conf.getList(String.class, "kafka.brokers", new ArrayList<String>());
                    }
                }
                """
            ),
            7
          )
        );
    }

    @Test
    void listOfObjectsIsLeftAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.configuration2.LegacyConfiguration;

              import java.util.List;

              class Brokers {
                  List<Object> brokers(LegacyConfiguration conf) {
                      List raw = conf.getList("kafka.brokers");
                      return conf.getList("kafka.brokers");
                  }
              }
              """
          )
        );
    }
}
