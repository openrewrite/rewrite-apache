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

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeApachePdfBox_3Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        PdfBoxTestParsers.pdfBox2To3(spec).recipeFromResources("org.openrewrite.apache.pdfbox.UpgradeApachePdfBox_3");
    }

    //language=java
    private static final String WRITE_TITLE_2 = """
      import org.apache.pdfbox.pdmodel.PDDocument;
      import org.apache.pdfbox.pdmodel.PDPageContentStream;
      import org.apache.pdfbox.pdmodel.font.PDType1Font;

      import java.io.File;
      import java.io.IOException;

      class Title {
          void write(File in, File out, String title) throws IOException {
              try (PDDocument document = PDDocument.load(in)) {
                  PDPageContentStream stream = new PDPageContentStream(document, document.getPage(0), true, true);
                  stream.beginText();
                  stream.setFont(PDType1Font.HELVETICA_BOLD, 18);
                  stream.moveTextPositionByAmount(72, 720);
                  stream.drawString(title);
                  stream.endText();
                  stream.close();
                  document.save(out);
              }
          }
      }
      """;

    @DocumentExample
    @Test
    void migrateMavenProject() {
        rewriteRun(
          mavenProject("example",
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
                            <groupId>org.apache.pdfbox</groupId>
                            <artifactId>pdfbox</artifactId>
                            <version>2.0.30</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .containsPattern("<version>3\\.0\\.\\d+</version>")
                .doesNotContain("2.0.30")
                .actual())
            ),
            srcMainJava(
              java(
                WRITE_TITLE_2,
                """
                  import org.apache.pdfbox.Loader;
                  import org.apache.pdfbox.pdmodel.PDDocument;
                  import org.apache.pdfbox.pdmodel.PDPageContentStream;
                  import org.apache.pdfbox.pdmodel.font.PDType1Font;
                  import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

                  import java.io.File;
                  import java.io.IOException;

                  class Title {
                      void write(File in, File out, String title) throws IOException {
                          try (PDDocument document = Loader.loadPDF(in)) {
                              PDPageContentStream stream = new PDPageContentStream(document, document.getPage(0), PDPageContentStream.AppendMode.APPEND, true);
                              stream.beginText();
                              stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
                              stream.newLineAtOffset(72, 720);
                              stream.showText(title);
                              stream.endText();
                              stream.close();
                              document.save(out);
                          }
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void leavePdfBox1Alone() {
        rewriteRun(
          mavenProject("example",
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
                            <groupId>org.apache.pdfbox</groupId>
                            <artifactId>pdfbox</artifactId>
                            <version>1.8.17</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainJava(
              java(WRITE_TITLE_2)
            )
          )
        );
    }

    @Test
    void leaveRepositoryWithPdfBox1InAnotherModule() {
        rewriteRun(
          mavenProject("current",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>current</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.pdfbox</groupId>
                            <artifactId>pdfbox</artifactId>
                            <version>2.0.30</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainJava(
              java(WRITE_TITLE_2)
            )
          ),
          mavenProject("legacy",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>legacy</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.pdfbox</groupId>
                            <artifactId>pdfbox</artifactId>
                            <version>1.8.17</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void leaveTransitivePdfBox2Alone() {
        rewriteRun(
          mavenProject("example",
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
                            <groupId>com.openhtmltopdf</groupId>
                            <artifactId>openhtmltopdf-pdfbox</artifactId>
                            <version>1.0.10</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainJava(
              java(WRITE_TITLE_2)
            )
          )
        );
    }

    @Test
    void leaveRepositoryWhoseLibraryNeedsPdfBox2() {
        rewriteRun(
          mavenProject("example",
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
                            <groupId>org.apache.pdfbox</groupId>
                            <artifactId>pdfbox</artifactId>
                            <version>2.0.30</version>
                        </dependency>
                        <dependency>
                            <groupId>com.openhtmltopdf</groupId>
                            <artifactId>openhtmltopdf-pdfbox</artifactId>
                            <version>1.0.10</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainJava(
              java(WRITE_TITLE_2)
            )
          )
        );
    }

    @Test
    void migrateRepositoryWhoseOwnModuleNeedsPdfBox2() {
        rewriteRun(
          mavenProject("library",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>library</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.pdfbox</groupId>
                            <artifactId>pdfbox</artifactId>
                            <version>2.0.30</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .containsPattern("<version>3\\.0\\.\\d+</version>")
                .actual())
            )
          ),
          mavenProject("application",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>application</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>library</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }
}
