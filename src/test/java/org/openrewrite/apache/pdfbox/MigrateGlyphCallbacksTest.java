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

class MigrateGlyphCallbacksTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        PdfBoxTestParsers.pdfBox2To3(spec).recipe(new MigrateGlyphCallbacks());
    }

    @DocumentExample
    @Test
    void dropUnicodeParameter() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.font.PDFont;
              import org.apache.pdfbox.text.PDFTextStripper;
              import org.apache.pdfbox.util.Matrix;
              import org.apache.pdfbox.util.Vector;

              import java.io.IOException;

              class GlyphCounter extends PDFTextStripper {
                  int glyphs;

                  GlyphCounter() throws IOException {
                  }

                  @Override
                  protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code, String unicode,
                                           Vector displacement) throws IOException {
                      glyphs++;
                      super.showGlyph(textRenderingMatrix, font, code, unicode, displacement);
                  }
              }
              """,
            """
              import org.apache.pdfbox.pdmodel.font.PDFont;
              import org.apache.pdfbox.text.PDFTextStripper;
              import org.apache.pdfbox.util.Matrix;
              import org.apache.pdfbox.util.Vector;

              import java.io.IOException;

              class GlyphCounter extends PDFTextStripper {
                  int glyphs;

                  GlyphCounter() throws IOException {
                  }

                  @Override
                  protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code,
                                           Vector displacement) throws IOException {
                      glyphs++;
                      super.showGlyph(textRenderingMatrix, font, code, displacement);
                  }
              }
              """
          )
        );
    }

    @Test
    void getUnicodeWhereTheMethodUsesIt() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
              import org.apache.pdfbox.pdmodel.PDPage;
              import org.apache.pdfbox.pdmodel.font.PDFont;
              import org.apache.pdfbox.util.Matrix;
              import org.apache.pdfbox.util.Vector;

              import java.io.IOException;
              import java.util.ArrayList;
              import java.util.List;

              abstract class TextCollector extends PDFGraphicsStreamEngine {
                  final List<String> text = new ArrayList<>();

                  TextCollector(PDPage page) {
                      super(page);
                  }

                  @Override
                  protected void showFontGlyph(Matrix textRenderingMatrix, PDFont font, int code, String unicode, Vector displacement) throws IOException {
                      text.add(unicode);
                  }
              }
              """,
            """
              import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
              import org.apache.pdfbox.pdmodel.PDPage;
              import org.apache.pdfbox.pdmodel.font.PDFont;
              import org.apache.pdfbox.util.Matrix;
              import org.apache.pdfbox.util.Vector;

              import java.io.IOException;
              import java.util.ArrayList;
              import java.util.List;

              abstract class TextCollector extends PDFGraphicsStreamEngine {
                  final List<String> text = new ArrayList<>();

                  TextCollector(PDPage page) {
                      super(page);
                  }

                  @Override
                  protected void showFontGlyph(Matrix textRenderingMatrix, PDFont font, int code, Vector displacement) throws IOException {
                      String unicode = font.toUnicode(code);
                      text.add(unicode);
                  }
              }
              """
          )
        );
    }
}
