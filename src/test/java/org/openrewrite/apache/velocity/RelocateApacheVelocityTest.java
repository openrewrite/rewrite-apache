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
package org.openrewrite.apache.velocity;

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

class RelocateApacheVelocityTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.apache.velocity.RelocateApacheVelocity");
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
                            <groupId>velocity</groupId>
                            <artifactId>velocity</artifactId>
                            <version>1.5</version>
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
                            <groupId>org.apache.velocity</groupId>
                            <artifactId>velocity</artifactId>
                            <version>1.5</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.3", "1.3.1", "1.4-rc1", "1.4"})
    void leaveVersionsOlderThan1_5(String version) {
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
                            <groupId>velocity</groupId>
                            <artifactId>velocity</artifactId>
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
                        <velocity.version>1.5</velocity.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>velocity</groupId>
                            <artifactId>velocity</artifactId>
                            <version>${velocity.version}</version>
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
                        <velocity.version>1.5</velocity.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.velocity</groupId>
                            <artifactId>velocity</artifactId>
                            <version>${velocity.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void leaveVersionPropertyOlderThan1_5() {
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
                        <velocity.version>1.4</velocity.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>velocity</groupId>
                            <artifactId>velocity</artifactId>
                            <version>${velocity.version}</version>
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
                                <groupId>velocity</groupId>
                                <artifactId>velocity</artifactId>
                                <version>1.5</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>velocity</groupId>
                            <artifactId>velocity</artifactId>
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
                                <groupId>velocity</groupId>
                                <artifactId>velocity</artifactId>
                                <version>1.5</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.velocity</groupId>
                            <artifactId>velocity</artifactId>
                            <version>1.5</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    /**
     * Shaped like apache/cocoon: nothing declares Velocity, but a managed entry holds a transitive
     * {@code velocity:velocity} at 1.5. A transitive dependency keeps its coordinates whatever this POM says, so
     * moving the entry would release it back to the 1.4 that plexus-velocity asks for. Its exclusion stays as it is as
     * well, as a new one for {@code org.apache.velocity:velocity} is only called for where something moved.
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
                                <groupId>velocity</groupId>
                                <artifactId>velocity</artifactId>
                                <version>1.5</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-velocity</artifactId>
                            <version>1.1.2</version>
                        </dependency>
                        <dependency>
                            <groupId>commons-configuration</groupId>
                            <artifactId>commons-configuration</artifactId>
                            <version>1.10</version>
                            <exclusions>
                                <exclusion>
                                    <groupId>velocity</groupId>
                                    <artifactId>velocity</artifactId>
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
    void leaveExclusionWhereNothingDeclaresVelocity() {
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
                                    <groupId>velocity</groupId>
                                    <artifactId>velocity</artifactId>
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
                                <groupId>velocity</groupId>
                                <artifactId>velocity</artifactId>
                                <version>1.5</version>
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
                              <groupId>velocity</groupId>
                              <artifactId>velocity</artifactId>
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
                              <groupId>org.apache.velocity</groupId>
                              <artifactId>velocity</artifactId>
                              <version>1.5</version>
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
                                <groupId>velocity</groupId>
                                <artifactId>velocity</artifactId>
                                <version>1.4</version>
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
                              <groupId>velocity</groupId>
                              <artifactId>velocity</artifactId>
                          </dependency>
                      </dependencies>
                  </project>
                  """
              )
            )
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.4-rc1", "1.4"})
    void leaveOlderVersionBesideRelocatedModule(String version) {
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
                            <groupId>velocity</groupId>
                            <artifactId>velocity</artifactId>
                            <version>1.5</version>
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
                            <groupId>org.apache.velocity</groupId>
                            <artifactId>velocity</artifactId>
                            <version>1.5</version>
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
                            <groupId>velocity</groupId>
                            <artifactId>velocity</artifactId>
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
    void leaveVelocityDepBesideRelocatedDependency() {
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
                            <groupId>velocity</groupId>
                            <artifactId>velocity</artifactId>
                            <version>1.5</version>
                        </dependency>
                        <dependency>
                            <groupId>velocity</groupId>
                            <artifactId>velocity-dep</artifactId>
                            <version>1.4</version>
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
                            <groupId>org.apache.velocity</groupId>
                            <artifactId>velocity</artifactId>
                            <version>1.5</version>
                        </dependency>
                        <dependency>
                            <groupId>velocity</groupId>
                            <artifactId>velocity-dep</artifactId>
                            <version>1.4</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void leaveModuleAlreadyOnApacheVelocity() {
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
                            <groupId>velocity</groupId>
                            <artifactId>velocity</artifactId>
                            <version>1.5</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.velocity</groupId>
                            <artifactId>velocity</artifactId>
                            <version>1.7</version>
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
                    implementation 'velocity:velocity:1.5'
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
                    implementation 'org.apache.velocity:velocity:1.5'
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
                    implementation 'velocity:velocity:1.4'
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
              dependency = { module = "velocity:velocity", version = "1.5" }
              """,
            """
              [libraries]
              dependency = { module = "org.apache.velocity:velocity", version = "1.5" }
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
