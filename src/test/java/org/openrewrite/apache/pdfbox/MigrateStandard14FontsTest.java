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

import static org.openrewrite.java.Assertions.java;

class MigrateStandard14FontsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        PdfBoxTestParsers.pdfBox2To3(spec).recipe(new MigrateStandard14Fonts());
    }

    @DocumentExample
    @Test
    void createFonts() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.PDPageContentStream;
              import org.apache.pdfbox.pdmodel.font.PDFont;
              import org.apache.pdfbox.pdmodel.font.PDType1Font;

              import java.io.IOException;

              class A {
                  private static final PDFont TITLE = PDType1Font.HELVETICA_BOLD;

                  void write(PDPageContentStream stream) throws IOException {
                      stream.setFont(PDType1Font.TIMES_ROMAN, 12);
                      stream.setFont(TITLE, 18);
                  }
              }
              """,
            """
              import org.apache.pdfbox.pdmodel.PDPageContentStream;
              import org.apache.pdfbox.pdmodel.font.PDFont;
              import org.apache.pdfbox.pdmodel.font.PDType1Font;
              import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

              import java.io.IOException;

              class A {
                  private static final PDFont TITLE = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

                  void write(PDPageContentStream stream) throws IOException {
                      stream.setFont(new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), 12);
                      stream.setFont(TITLE, 18);
                  }
              }
              """
          )
        );
    }

    @Test
    void staticImportAndDefaultFont() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.font.PDFont;
              import org.apache.pdfbox.pdmodel.font.PDFontFactory;

              import java.io.IOException;

              import static org.apache.pdfbox.pdmodel.font.PDType1Font.COURIER;

              class A {
                  PDFont[] fonts() throws IOException {
                      return new PDFont[]{COURIER, PDFontFactory.createDefaultFont()};
                  }
              }
              """,
            """
              import org.apache.pdfbox.pdmodel.font.PDFont;
              import org.apache.pdfbox.pdmodel.font.PDType1Font;
              import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

              import java.io.IOException;

              class A {
                  PDFont[] fonts() throws IOException {
                      return new PDFont[]{new PDType1Font(Standard14Fonts.FontName.COURIER), new PDType1Font(Standard14Fonts.FontName.HELVETICA)};
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveOtherFields() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.font.PDType1Font;

              class A {
                  String name(PDType1Font font) {
                      return font.getName();
                  }
              }
              """
          )
        );
    }

    @Test
    void compareFontNamesInAssertions() {
        rewriteRun(
          spec -> PdfBoxTestParsers.pdfBox2To3WithTestLibraries(spec, "junit-jupiter-api"),
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.font.PDFont;
              import org.apache.pdfbox.pdmodel.font.PDType1Font;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertNotEquals;

              class FontChooserTest {
                  void choose(FontChooser chooser) {
                      assertEquals(PDType1Font.COURIER, chooser.choose("Courier New"));
                      assertNotEquals(PDType1Font.TIMES_BOLD, chooser.choose("Arial"), "not Times");
                      assertEquals(PDType1Font.HELVETICA.getName(), "Helvetica");
                  }

                  interface FontChooser {
                      PDFont choose(String name);
                  }
              }
              """,
            """
              import org.apache.pdfbox.pdmodel.font.PDFont;
              import org.apache.pdfbox.pdmodel.font.PDType1Font;
              import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertNotEquals;

              class FontChooserTest {
                  void choose(FontChooser chooser) {
                      assertEquals(Standard14Fonts.FontName.COURIER.getName(), chooser.choose("Courier New").getName());
                      assertNotEquals(Standard14Fonts.FontName.TIMES_BOLD.getName(), chooser.choose("Arial").getName(), "not Times");
                      assertEquals(new PDType1Font(Standard14Fonts.FontName.HELVETICA).getName(), "Helvetica");
                  }

                  interface FontChooser {
                      PDFont choose(String name);
                  }
              }
              """
          )
        );
    }
}
