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
package org.openrewrite.apache.poi;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeApachePoi_5Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.apache.poi.UpgradeApachePoi_5");
    }

    @DocumentExample
    @Test
    void upgradePoiOoxmlSchemasDependency() {
        rewriteRun(
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
                          <groupId>org.apache.poi</groupId>
                          <artifactId>poi</artifactId>
                          <version>4.1.2</version>
                      </dependency>
                      <dependency>
                          <groupId>org.apache.poi</groupId>
                          <artifactId>poi-ooxml</artifactId>
                          <version>4.1.2</version>
                      </dependency>
                      <dependency>
                          <groupId>org.apache.poi</groupId>
                          <artifactId>poi-ooxml-schemas</artifactId>
                          <version>4.1.2</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> assertThat(pom)
              .contains("<artifactId>poi</artifactId>")
              .contains("<artifactId>poi-ooxml</artifactId>")
              .contains("<artifactId>poi-ooxml-lite</artifactId>")
              .doesNotContain("<artifactId>poi-ooxml-schemas</artifactId>")
              .containsPattern("<version>5\\.\\d+\\.\\d+</version>")
              .actual())
          )
        );
    }

    @Test
    void upgradeOoxmlSchemasDependency() {
        rewriteRun(
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
                          <groupId>org.apache.poi</groupId>
                          <artifactId>poi-ooxml</artifactId>
                          <version>4.1.2</version>
                      </dependency>
                      <dependency>
                          <groupId>org.apache.poi</groupId>
                          <artifactId>ooxml-schemas</artifactId>
                          <version>1.4</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> assertThat(pom)
              .contains("<artifactId>poi-ooxml</artifactId>")
              .contains("<artifactId>poi-ooxml-full</artifactId>")
              .doesNotContain("<artifactId>ooxml-schemas</artifactId>")
              .containsPattern("<version>5\\.\\d+\\.\\d+</version>")
              .actual())
          )
        );
    }

    @Test
    void removeOoxmlSecurityDependency() {
        rewriteRun(
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
                          <groupId>org.apache.poi</groupId>
                          <artifactId>poi-ooxml</artifactId>
                          <version>4.1.2</version>
                      </dependency>
                      <dependency>
                          <groupId>org.apache.poi</groupId>
                          <artifactId>ooxml-security</artifactId>
                          <version>1.1</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> assertThat(pom)
              .contains("<artifactId>poi-ooxml</artifactId>")
              .doesNotContain("<artifactId>ooxml-security</artifactId>")
              .containsPattern("<version>5\\.\\d+\\.\\d+</version>")
              .actual())
          )
        );
    }

    @Test
    void upgradeVersionOnly() {
        rewriteRun(
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
                          <groupId>org.apache.poi</groupId>
                          <artifactId>poi</artifactId>
                          <version>4.1.2</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> assertThat(pom)
              .contains("<artifactId>poi</artifactId>")
              .containsPattern("<version>5\\.\\d+\\.\\d+</version>")
              .actual())
          )
        );
    }

    @Test
    void upgradeFromOlderPoi5() {
        rewriteRun(
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
                          <groupId>org.apache.poi</groupId>
                          <artifactId>poi</artifactId>
                          <version>5.2.3</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> assertThat(pom)
              .contains("<artifactId>poi</artifactId>")
              .containsPattern("<version>5\\.\\d+\\.\\d+</version>")
              .actual())
          )
        );
    }
}
