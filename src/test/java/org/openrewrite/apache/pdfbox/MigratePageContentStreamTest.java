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

class MigratePageContentStreamTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        PdfBoxTestParsers.pdfBox2To3(spec).recipe(new MigratePageContentStream());
    }

    @DocumentExample
    @Test
    void writeText() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.PDDocument;
              import org.apache.pdfbox.pdmodel.PDPage;
              import org.apache.pdfbox.pdmodel.PDPageContentStream;

              import java.io.IOException;

              class A {
                  void write(PDDocument document, PDPage page, String text) throws IOException {
                      PDPageContentStream stream = new PDPageContentStream(document, page, true, true);
                      stream.beginText();
                      stream.moveTextPositionByAmount(100, 700);
                      stream.setLeading(14.5);
                      stream.drawString(text);
                      stream.endText();
                      stream.close();
                  }
              }
              """,
            """
              import org.apache.pdfbox.pdmodel.PDDocument;
              import org.apache.pdfbox.pdmodel.PDPage;
              import org.apache.pdfbox.pdmodel.PDPageContentStream;

              import java.io.IOException;

              class A {
                  void write(PDDocument document, PDPage page, String text) throws IOException {
                      PDPageContentStream stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true);
                      stream.beginText();
                      stream.newLineAtOffset(100, 700);
                      stream.setLeading(14.5f);
                      stream.showText(text);
                      stream.endText();
                      stream.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void unboxLeading() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.PDPageContentStream;

              import java.io.IOException;

              class A {
                  void lead(PDPageContentStream stream, Double leading, Object spacing) throws IOException {
                      stream.setLeading(leading);
                      stream.setLeading((Double) spacing);
                  }
              }
              """,
            """
              import org.apache.pdfbox.pdmodel.PDPageContentStream;

              import java.io.IOException;

              class A {
                  void lead(PDPageContentStream stream, Double leading, Object spacing) throws IOException {
                      stream.setLeading(leading.floatValue());
                      stream.setLeading(((Double) spacing).floatValue());
                  }
              }
              """
          )
        );
    }

    @Test
    void appendModeFromExpression() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.PDDocument;
              import org.apache.pdfbox.pdmodel.PDPage;
              import org.apache.pdfbox.pdmodel.PDPageContentStream;

              import java.io.IOException;

              class A {
                  PDPageContentStream open(PDDocument document, PDPage page, boolean append) throws IOException {
                      new PDPageContentStream(document, page, false, true, true).close();
                      return new PDPageContentStream(document, page, append, false);
                  }
              }
              """,
            """
              import org.apache.pdfbox.pdmodel.PDDocument;
              import org.apache.pdfbox.pdmodel.PDPage;
              import org.apache.pdfbox.pdmodel.PDPageContentStream;

              import java.io.IOException;

              class A {
                  PDPageContentStream open(PDDocument document, PDPage page, boolean append) throws IOException {
                      new PDPageContentStream(document, page, PDPageContentStream.AppendMode.OVERWRITE, true, true).close();
                      return new PDPageContentStream(document, page, append ? PDPageContentStream.AppendMode.APPEND : PDPageContentStream.AppendMode.OVERWRITE, false);
                  }
              }
              """
          )
        );
    }

    @Test
    void drawShapes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.PDPageContentStream;

              import java.awt.geom.PathIterator;
              import java.io.IOException;

              class A {
                  void draw(PDPageContentStream stream, float x, float y) throws IOException {
                      stream.drawLine(x, y, x + 100, y);
                      stream.fillRect(x, y, 50, 20);
                      stream.addLine(0, 0, x, y);
                      stream.closeSubPath();
                      stream.fill(PathIterator.WIND_EVEN_ODD);
                      stream.clipPath(PathIterator.WIND_NON_ZERO);
                  }
              }
              """,
            """
              import org.apache.pdfbox.pdmodel.PDPageContentStream;

              import java.io.IOException;

              class A {
                  void draw(PDPageContentStream stream, float x, float y) throws IOException {
                      stream.moveTo(x, y);
                      stream.lineTo(x + 100, y);
                      stream.stroke();
                      stream.addRect(x, y, 50, 20);
                      stream.fill();
                      stream.moveTo(0, 0);
                      stream.lineTo(x, y);
                      stream.closePath();
                      stream.fillEvenOdd();
                      stream.clip();
                  }
              }
              """
          )
        );
    }

    @Test
    void colors() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.PDPageContentStream;

              import java.io.IOException;

              class A {
                  void color(PDPageContentStream stream, int red, double gray) throws IOException {
                      stream.setNonStrokingColor(red, 0, 255);
                      stream.setStrokingColor(128);
                      stream.setStrokingColor(10, 20, 30, 40);
                      stream.setNonStrokingColor(gray);
                      stream.setStrokingColor(red - 1, 0, 0);
                  }
              }
              """,
            """
              import org.apache.pdfbox.pdmodel.PDPageContentStream;

              import java.io.IOException;

              class A {
                  void color(PDPageContentStream stream, int red, double gray) throws IOException {
                      stream.setNonStrokingColor(red / 255f, 0 / 255f, 255 / 255f);
                      stream.setStrokingColor(128 / 255f);
                      stream.setStrokingColor(10 / 255f, 20 / 255f, 30 / 255f, 40 / 255f);
                      stream.setNonStrokingColor((float) gray);
                      stream.setStrokingColor((red - 1) / 255f, 0 / 255f, 0 / 255f);
                  }
              }
              """
          )
        );
    }

    @Test
    void matrices() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.PDPageContentStream;
              import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

              import java.awt.geom.AffineTransform;
              import java.io.IOException;

              class A {
                  void transform(PDPageContentStream stream, AffineTransform at, double angle, PDImageXObject image) throws IOException {
                      stream.concatenate2CTM(at);
                      stream.concatenate2CTM(1, 0, 0, 1, 10.5, 20);
                      stream.setTextTranslation(10, 20);
                      stream.setTextRotation(angle, 10, 20);
                      stream.setTextScaling(2, 2, 0, 0);
                      stream.setTextMatrix(at);
                      stream.drawXObject(image, 10, 10, 100, 100);
                  }
              }
              """,
            """
              import org.apache.pdfbox.pdmodel.PDPageContentStream;
              import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
              import org.apache.pdfbox.util.Matrix;

              import java.awt.geom.AffineTransform;
              import java.io.IOException;

              class A {
                  void transform(PDPageContentStream stream, AffineTransform at, double angle, PDImageXObject image) throws IOException {
                      stream.transform(new Matrix(at));
                      stream.transform(new Matrix(1, 0, 0, 1, 10.5f, 20));
                      stream.setTextMatrix(Matrix.getTranslateInstance(10, 20));
                      stream.setTextMatrix(Matrix.getRotateInstance(angle, 10, 20));
                      stream.setTextMatrix(new Matrix(2, 0f, 0f, 2, 0, 0));
                      stream.setTextMatrix(new Matrix(at));
                      stream.drawImage(image, 10, 10, 100, 100);
                  }
              }
              """
          )
        );
    }
}
