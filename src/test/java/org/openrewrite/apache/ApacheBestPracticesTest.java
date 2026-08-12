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
package org.openrewrite.apache;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class ApacheBestPracticesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.apache.ApacheBestPractices");
    }

    @DocumentExample
    @Test
    void migrateHttpClient() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient-4", "httpcore-4", "httpclient5", "httpcore5")),
          //language=java
          java(
            """
              import org.apache.http.client.methods.HttpGet;
              import org.apache.http.client.methods.HttpUriRequest;

              class A {
                  HttpUriRequest get(String url) {
                      return new HttpGet(url);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.classic.methods.HttpGet;
              import org.apache.hc.client5.http.classic.methods.HttpUriRequest;

              class A {
                  HttpUriRequest get(String url) {
                      return new HttpGet(url);
                  }
              }
              """
          )
        );
    }

    @Test
    void upgradeApachePoiDependencies() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.poi</groupId>
                          <artifactId>poi-ooxml-schemas</artifactId>
                          <version>4.1.2</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> {
                assertThat(pom).contains("<artifactId>poi-ooxml-lite</artifactId>");
                return pom;
            })
          )
        );
    }

    @Test
    void includesApacheCommonsBestPractices() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("commons-lang", "commons-lang3")),
          //language=java
          java(
            """
              import org.apache.commons.lang.RandomStringUtils;

              class Test {
                  String random() {
                      return RandomStringUtils.random(10);
                  }
              }
              """,
            """
              import org.apache.commons.lang3.RandomStringUtils;

              class Test {
                  String random() {
                      return RandomStringUtils.random(10);
                  }
              }
              """
          )
        );
    }
}
