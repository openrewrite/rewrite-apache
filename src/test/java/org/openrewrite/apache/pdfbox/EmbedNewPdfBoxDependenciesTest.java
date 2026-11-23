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
package org.openrewrite.apache.pdfbox;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class EmbedNewPdfBoxDependenciesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new EmbedNewPdfBoxDependencies());
    }

    @DocumentExample
    @Test
    void embedTheArtifactsThatNeedThem() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>bundle</artifactId>
                  <version>1.0.0</version>
                  <packaging>bundle</packaging>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.felix</groupId>
                              <artifactId>maven-bundle-plugin</artifactId>
                              <configuration>
                                  <instructions>
                                      <Embed-Dependency>*;scope=compile;artifactId=parsers|
                                        commons-io|
                                        pdfbox|
                                        pdfbox-tools|
                                        fontbox</Embed-Dependency>
                                  </instructions>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>bundle</artifactId>
                  <version>1.0.0</version>
                  <packaging>bundle</packaging>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.felix</groupId>
                              <artifactId>maven-bundle-plugin</artifactId>
                              <configuration>
                                  <instructions>
                                      <Embed-Dependency>*;scope=compile;artifactId=parsers|
                                        commons-io|
                                        pdfbox|
                                        pdfbox-io|
                                        pdfbox-tools|
                                        picocli|
                                        fontbox</Embed-Dependency>
                                  </instructions>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void embedAfterTheLastArtifact() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>bundle</artifactId>
                  <version>1.0.0</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.felix</groupId>
                              <artifactId>maven-bundle-plugin</artifactId>
                              <configuration>
                                  <instructions>
                                      <Embed-Dependency>artifactId=fontbox|pdfbox;inline=false</Embed-Dependency>
                                  </instructions>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>bundle</artifactId>
                  <version>1.0.0</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.felix</groupId>
                              <artifactId>maven-bundle-plugin</artifactId>
                              <configuration>
                                  <instructions>
                                      <Embed-Dependency>artifactId=fontbox|pdfbox|pdfbox-io;inline=false</Embed-Dependency>
                                  </instructions>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void embedAsClauseOfItsOwn() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>bundle</artifactId>
                  <version>1.0.0</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.felix</groupId>
                              <artifactId>maven-bundle-plugin</artifactId>
                              <configuration>
                                  <instructions>
                                      <Embed-Dependency>
                                        parsers;inline=true,
                                        pdfbox,fontbox
                                      </Embed-Dependency>
                                  </instructions>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>bundle</artifactId>
                  <version>1.0.0</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.felix</groupId>
                              <artifactId>maven-bundle-plugin</artifactId>
                              <configuration>
                                  <instructions>
                                      <Embed-Dependency>
                                        parsers;inline=true,
                                        pdfbox,pdfbox-io,fontbox
                                      </Embed-Dependency>
                                  </instructions>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void leaveBundleThatEmbedsPdfBoxIoOrMatchesIt() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>bundle</artifactId>
                  <version>1.0.0</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.felix</groupId>
                              <artifactId>maven-bundle-plugin</artifactId>
                              <executions>
                                  <execution>
                                      <id>embedded</id>
                                      <configuration>
                                          <instructions>
                                              <Embed-Dependency>artifactId=pdfbox|pdfbox-io</Embed-Dependency>
                                          </instructions>
                                      </configuration>
                                  </execution>
                                  <execution>
                                      <id>matched</id>
                                      <configuration>
                                          <instructions>
                                              <Embed-Dependency>groupId=org.apache.pdfbox;artifactId=pdfbox*</Embed-Dependency>
                                          </instructions>
                                      </configuration>
                                  </execution>
                              </executions>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }
}
