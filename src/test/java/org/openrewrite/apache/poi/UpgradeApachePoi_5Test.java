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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            spec -> spec.after(pom -> {
                Matcher version = Pattern.compile("5\\.\\d+\\.\\d+").matcher(pom);
                assertThat(version.find()).describedAs("Expected 5.x in %s", pom).isTrue();
                String poiVersion = version.group();
                //language=xml
                return """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.poi</groupId>
                              <artifactId>poi</artifactId>
                              <version>%1$s</version>
                          </dependency>
                          <dependency>
                              <groupId>org.apache.poi</groupId>
                              <artifactId>poi-ooxml</artifactId>
                              <version>%1$s</version>
                          </dependency>
                          <dependency>
                              <groupId>org.apache.poi</groupId>
                              <artifactId>poi-ooxml-lite</artifactId>
                              <version>%1$s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(poiVersion);
            })
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
            spec -> spec.after(pom -> {
                Matcher version = Pattern.compile("5\\.\\d+\\.\\d+").matcher(pom);
                assertThat(version.find()).describedAs("Expected 5.x in %s", pom).isTrue();
                String poiVersion = version.group();
                //language=xml
                return """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.poi</groupId>
                              <artifactId>poi-ooxml</artifactId>
                              <version>%1$s</version>
                          </dependency>
                          <dependency>
                              <groupId>org.apache.poi</groupId>
                              <artifactId>poi-ooxml-full</artifactId>
                              <version>%1$s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(poiVersion);
            })
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
            spec -> spec.after(pom -> {
                Matcher version = Pattern.compile("5\\.\\d+\\.\\d+").matcher(pom);
                assertThat(version.find()).describedAs("Expected 5.x in %s", pom).isTrue();
                String poiVersion = version.group();
                //language=xml
                return """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.poi</groupId>
                              <artifactId>poi-ooxml</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(poiVersion);
            })
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
            spec -> spec.after(pom -> {
                Matcher version = Pattern.compile("5\\.\\d+\\.\\d+").matcher(pom);
                assertThat(version.find()).describedAs("Expected 5.x in %s", pom).isTrue();
                //language=xml
                return """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.poi</groupId>
                              <artifactId>poi</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(version.group());
            })
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
            spec -> spec.after(pom -> {
                Matcher version = Pattern.compile("5\\.\\d+\\.\\d+").matcher(pom);
                assertThat(version.find()).describedAs("Expected 5.x in %s", pom).isTrue();
                //language=xml
                return """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.poi</groupId>
                              <artifactId>poi</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(version.group());
            })
          )
        );
    }
}
