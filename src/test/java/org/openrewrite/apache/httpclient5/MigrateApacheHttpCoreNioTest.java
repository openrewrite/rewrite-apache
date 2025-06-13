/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.apache.httpclient5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateApacheHttpCoreNioTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "httpcore-4", "httpcore-nio-4", "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpCore_5_NioClassMapping");
    }

    @Test
    @DocumentExample
    void migratesIOReactorConfig() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.nio.reactor.IOReactorConfig;
              import org.apache.http.impl.nio.reactor.IOReactorConfig.Builder;

              class A {
                  void method() {
                      IOReactorConfig.Builder builder = new IOReactorConfig.Builder();
                      builder.setSelectInterval(500);
                      builder.setShutdownGracePeriod(400);
                      builder.setInterestOpQueued(true);
                      builder.setSoTimeout(300);
                      builder.setSoLinger(200);
                      builder.setConnectTimeout(100);
                      IOReactorConfig ioReactorConfig = builder.build();
                      long selectInterval = ioReactorConfig.getSelectInterval();
                      ioReactorConfig.getShutdownGracePeriod();
                      ioReactorConfig.isInterestOpQueued();
                      int soTimeout = ioReactorConfig.getSoTimeout();
                      int soLinger = ioReactorConfig.getSoLinger();
                      ioReactorConfig.getConnectTimeout();
                  }
              }
              """,
            """
              import org.apache.hc.core5.reactor.IOReactorConfig;
              import org.apache.hc.core5.reactor.IOReactorConfig.Builder;
              import org.apache.hc.core5.util.TimeValue;
              import org.apache.hc.core5.util.Timeout;

              import java.util.concurrent.TimeUnit;

              class A {
                  void method() {
                      IOReactorConfig.Builder builder = new IOReactorConfig.Builder();
                      builder.setSelectInterval(TimeValue.of(500, TimeUnit.MILLISECONDS));
                      builder.setSoTimeout(300, TimeUnit.MILLISECONDS);
                      builder.setSoLinger(200, TimeUnit.MILLISECONDS);
                      IOReactorConfig ioReactorConfig = builder.build();
                      TimeValue selectInterval = ioReactorConfig.getSelectInterval();
                      Timeout soTimeout = ioReactorConfig.getSoTimeout();
                      TimeValue soLinger = ioReactorConfig.getSoLinger();
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateDependencies() {
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.httpcomponents</groupId>
                          <artifactId>httpcore-nio</artifactId>
                          <version>4.4.16</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher version = Pattern.compile("5\\.3\\.\\d+").matcher(pom);
                assertThat(version.find()).describedAs("Expected 5.3.x in %s", pom).isTrue();
                //language=xml
                return """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.httpcomponents.core5</groupId>
                              <artifactId>httpcore5</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(version.group(0));
            })
          )
        );
    }
}
