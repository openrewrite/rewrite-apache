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
package org.openrewrite.apache.xerces;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.Tree;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.toml.Assertions.toml;

class RelocateApacheXercesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.apache.xerces.RelocateApacheXerces");
    }

    @DocumentExample
    @Test
    void relocateDependency() {
        rewriteRun(
          mavenProject("my-app",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xerces</artifactId>
                            <version>2.4.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xercesImpl</artifactId>
                            <version>2.4.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.0.0", "2.0.2", "2.2.1", "2.3.0"})
    void relocateVersionsPublishedUnderNewCoordinates(String version) {
        rewriteRun(
          mavenProject("my-app",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xerces</artifactId>
                            <version>%s</version>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(version),
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xercesImpl</artifactId>
                            <version>%s</version>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(version)
            )
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.2.3", "1.4.0", "1.4.4", "2.0.1"})
    void leaveVersionsNotPublishedUnderNewCoordinates(String version) {
        rewriteRun(
          mavenProject("my-app",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xerces</artifactId>
                            <version>%s</version>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(version)
            )
          )
        );
    }

    @Test
    void keepVersionProperty() {
        rewriteRun(
          mavenProject("my-app",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <properties>
                        <xerces.version>2.4.0</xerces.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xerces</artifactId>
                            <version>${xerces.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <properties>
                        <xerces.version>2.4.0</xerces.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xercesImpl</artifactId>
                            <version>${xerces.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void leaveVersionPropertyNotPublishedUnderNewCoordinates() {
        rewriteRun(
          mavenProject("my-app",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <properties>
                        <xerces.version>1.4.4</xerces.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xerces</artifactId>
                            <version>${xerces.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void relocateVersionlessDependencyAndKeepManagedEntry() {
        rewriteRun(
          mavenProject("my-app",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>xerces</groupId>
                                <artifactId>xerces</artifactId>
                                <version>2.4.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xerces</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>xerces</groupId>
                                <artifactId>xerces</artifactId>
                                <version>2.4.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xercesImpl</artifactId>
                            <version>2.4.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    /**
     * Nothing here declares Xerces, but the managed entry holds the {@code xerces:xerces} that commons-jelly brings
     * along at 2.4.0. That entry and the exclusion are not this module's declarations to move.
     */
    @Test
    void leaveManagedEntryPinningTransitiveDependency() {
        rewriteRun(
          mavenProject("my-app",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>xerces</groupId>
                                <artifactId>xerces</artifactId>
                                <version>2.4.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>commons-jelly</groupId>
                            <artifactId>commons-jelly</artifactId>
                            <version>1.0</version>
                        </dependency>
                        <dependency>
                            <groupId>commons-configuration</groupId>
                            <artifactId>commons-configuration</artifactId>
                            <version>1.10</version>
                            <exclusions>
                                <exclusion>
                                    <groupId>xerces</groupId>
                                    <artifactId>xerces</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void leaveExclusionWhereNothingDeclaresXerces() {
        rewriteRun(
          mavenProject("my-app",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>commons-configuration</groupId>
                            <artifactId>commons-configuration</artifactId>
                            <version>1.10</version>
                            <exclusions>
                                <exclusion>
                                    <groupId>xerces</groupId>
                                    <artifactId>xerces</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void relocateChildAndKeepEntryManagedInParent() {
        rewriteRun(
          mavenProject("parent",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>child</module>
                    </modules>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>xerces</groupId>
                                <artifactId>xerces</artifactId>
                                <version>2.4.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """
            ),
            mavenProject("child",
              pomXml(
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <parent>
                          <groupId>com.mycompany.app</groupId>
                          <artifactId>parent</artifactId>
                          <version>1</version>
                      </parent>
                      <artifactId>child</artifactId>
                      <dependencies>
                          <dependency>
                              <groupId>xerces</groupId>
                              <artifactId>xerces</artifactId>
                          </dependency>
                      </dependencies>
                  </project>
                  """,
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <parent>
                          <groupId>com.mycompany.app</groupId>
                          <artifactId>parent</artifactId>
                          <version>1</version>
                      </parent>
                      <artifactId>child</artifactId>
                      <dependencies>
                          <dependency>
                              <groupId>xerces</groupId>
                              <artifactId>xercesImpl</artifactId>
                              <version>2.4.0</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """
              )
            )
          )
        );
    }

    @Test
    void leaveOlderVersionManagedInParent() {
        rewriteRun(
          mavenProject("parent",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>child</module>
                    </modules>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>xerces</groupId>
                                <artifactId>xerces</artifactId>
                                <version>2.0.1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """
            ),
            mavenProject("child",
              pomXml(
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <parent>
                          <groupId>com.mycompany.app</groupId>
                          <artifactId>parent</artifactId>
                          <version>1</version>
                      </parent>
                      <artifactId>child</artifactId>
                      <dependencies>
                          <dependency>
                              <groupId>xerces</groupId>
                              <artifactId>xerces</artifactId>
                          </dependency>
                      </dependencies>
                  </project>
                  """
              )
            )
          )
        );
    }

    @Test
    void leaveOlderVersionBesideRelocatedModule() {
        rewriteRun(
          mavenProject("relocated",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>relocated</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xerces</artifactId>
                            <version>2.4.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>relocated</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xercesImpl</artifactId>
                            <version>2.4.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          ),
          mavenProject("older",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>older</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xerces</artifactId>
                            <version>1.4.4</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void leaveModuleAlreadyDeclaringXercesImpl() {
        rewriteRun(
          mavenProject("my-app",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xerces</artifactId>
                            <version>2.4.0</version>
                        </dependency>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xercesImpl</artifactId>
                            <version>2.12.2</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    /**
     * Only a declared {@code xerces:xercesImpl} is in the way. This one arrives through xom, and since {@code xerces:xerces}
     * relocates to it, the build already mediates the two as one artifact.
     */
    @Test
    void relocateBesideTransitiveXercesImpl() {
        rewriteRun(
          mavenProject("my-app",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xerces</artifactId>
                            <version>2.4.0</version>
                        </dependency>
                        <dependency>
                            <groupId>xom</groupId>
                            <artifactId>xom</artifactId>
                            <version>1.2.5</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>xerces</groupId>
                            <artifactId>xercesImpl</artifactId>
                            <version>2.4.0</version>
                        </dependency>
                        <dependency>
                            <groupId>xom</groupId>
                            <artifactId>xom</artifactId>
                            <version>1.2.5</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void relocateGradleDependency() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("my-app",
            buildGradle(
              """
                plugins {
                    id 'java'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'xerces:xerces:2.3.0'
                }
                """,
              """
                plugins {
                    id 'java'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'xerces:xercesImpl:2.3.0'
                }
                """
            )
          )
        );
    }

    @Test
    void leaveGradleVersionNotPublishedUnderNewCoordinates() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("my-app",
            buildGradle(
              """
                plugins {
                    id 'java'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'xerces:xerces:1.4.4'
                }
                """
            )
          )
        );
    }

    @Test
    void leaveGradleModuleAlreadyDeclaringNewCoordinates() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("my-app",
            buildGradle(
              """
                plugins {
                    id 'java'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'xerces:xerces:2.4.0'
                    implementation 'xerces:xercesImpl:2.12.2'
                }
                """
            )
          )
        );
    }

    @Test
    void relocateVersionCatalogEntry() {
        JavaProject project = new JavaProject(Tree.randomId(), "my-app", null);
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()).allSources(source -> source.markers(project)),
          toml(
            """
              [libraries]
              dependency = { module = "xerces:xerces", version = "2.4.0" }
              """,
            """
              [libraries]
              dependency = { module = "xerces:xercesImpl", version = "2.4.0" }
              """,
            spec -> spec.path("gradle/libs.versions.toml")
          ),
          buildGradle(
            """
              plugins {
                  id 'java'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  implementation libs.dependency
              }
              """
          )
        );
    }
}
