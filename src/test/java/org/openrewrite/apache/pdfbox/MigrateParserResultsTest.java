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

class MigrateParserResultsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        PdfBoxTestParsers.pdfBox2To3(spec).recipe(new MigrateParserResults());
    }

    @DocumentExample
    @Test
    void parseIntoDocument() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.cos.COSDocument;
              import org.apache.pdfbox.io.RandomAccessRead;
              import org.apache.pdfbox.pdfparser.PDFParser;
              import org.apache.pdfbox.pdmodel.PDDocument;
              import org.apache.pdfbox.text.PDFTextStripper;

              import java.io.IOException;

              class A {
                  String text(RandomAccessRead source) throws IOException {
                      PDFParser parser = new PDFParser(source);
                      parser.parse();
                      COSDocument cosDoc = parser.getDocument();
                      PDFTextStripper stripper = new PDFTextStripper();
                      PDDocument pdDoc = new PDDocument(cosDoc);
                      return stripper.getText(pdDoc);
                  }

                  PDDocument document(RandomAccessRead source) throws IOException {
                      PDFParser parser = new PDFParser(source);
                      parser.parse();
                      return parser.getPDDocument();
                  }
              }
              """,
            """
              import org.apache.pdfbox.cos.COSDocument;
              import org.apache.pdfbox.io.RandomAccessRead;
              import org.apache.pdfbox.pdfparser.PDFParser;
              import org.apache.pdfbox.pdmodel.PDDocument;
              import org.apache.pdfbox.text.PDFTextStripper;

              import java.io.IOException;

              class A {
                  String text(RandomAccessRead source) throws IOException {
                      PDFParser parser = new PDFParser(source);
                      COSDocument cosDoc = parser.parse().getDocument();
                      PDFTextStripper stripper = new PDFTextStripper();
                      PDDocument pdDoc = new PDDocument(cosDoc);
                      return stripper.getText(pdDoc);
                  }

                  PDDocument document(RandomAccessRead source) throws IOException {
                      PDFParser parser = new PDFParser(source);
                      return parser.parse();
                  }
              }
              """
          )
        );
    }

    @Test
    void streamTokens() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdfparser.PDFStreamParser;
              import org.apache.pdfbox.pdmodel.PDPage;

              import java.io.IOException;
              import java.util.List;

              class A {
                  int count(PDPage page) throws IOException {
                      PDFStreamParser parser = new PDFStreamParser(page);
                      parser.parse();
                      List<Object> tokens = parser.getTokens();
                      return tokens.size();
                  }
              }
              """,
            """
              import org.apache.pdfbox.pdfparser.PDFStreamParser;
              import org.apache.pdfbox.pdmodel.PDPage;

              import java.io.IOException;
              import java.util.List;

              class A {
                  int count(PDPage page) throws IOException {
                      PDFStreamParser parser = new PDFStreamParser(page);
                      List<Object> tokens = parser.parse();
                      return tokens.size();
                  }
              }
              """
          )
        );
    }

    @Test
    void reuseTheVariableTheResultIsAssignedTo() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.io.RandomAccessRead;
              import org.apache.pdfbox.pdfparser.PDFParser;
              import org.apache.pdfbox.pdmodel.PDDocument;

              import java.io.IOException;

              class A {
                  int pages(RandomAccessRead source) throws IOException {
                      PDFParser parser = new PDFParser(source);
                      parser.parse();
                      PDDocument document = parser.getPDDocument();
                      int pages = document.getNumberOfPages();
                      parser.getPDDocument().close();
                      return pages;
                  }
              }
              """,
            """
              import org.apache.pdfbox.io.RandomAccessRead;
              import org.apache.pdfbox.pdfparser.PDFParser;
              import org.apache.pdfbox.pdmodel.PDDocument;

              import java.io.IOException;

              class A {
                  int pages(RandomAccessRead source) throws IOException {
                      PDFParser parser = new PDFParser(source);
                      PDDocument document = parser.parse();
                      int pages = document.getNumberOfPages();
                      document.close();
                      return pages;
                  }
              }
              """
          )
        );
    }

    @Test
    void declareVariableForResultGotTwice() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.io.RandomAccessRead;
              import org.apache.pdfbox.multipdf.LayerUtility;
              import org.apache.pdfbox.pdfparser.PDFParser;
              import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;

              import java.io.IOException;

              class A {
                  PDFormXObject form(LayerUtility layerUtility, RandomAccessRead source) throws IOException {
                      PDFParser pdfParser = new PDFParser(source);
                      pdfParser.parse();
                      PDFormXObject form = layerUtility.importPageAsForm(pdfParser.getPDDocument(), 0);
                      pdfParser.getPDDocument().close();
                      return form;
                  }
              }
              """,
            """
              import org.apache.pdfbox.io.RandomAccessRead;
              import org.apache.pdfbox.multipdf.LayerUtility;
              import org.apache.pdfbox.pdfparser.PDFParser;
              import org.apache.pdfbox.pdmodel.PDDocument;
              import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;

              import java.io.IOException;

              class A {
                  PDFormXObject form(LayerUtility layerUtility, RandomAccessRead source) throws IOException {
                      PDFParser pdfParser = new PDFParser(source);
                      PDDocument document = pdfParser.parse();
                      PDFormXObject form = layerUtility.importPageAsForm(document, 0);
                      document.close();
                      return form;
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveParserUsedForMoreThanTheResult() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.io.RandomAccessRead;
              import org.apache.pdfbox.pdfparser.PDFParser;
              import org.apache.pdfbox.pdmodel.PDDocument;

              import java.io.IOException;

              class A {
                  PDDocument parse(RandomAccessRead source) throws IOException {
                      PDFParser parser = new PDFParser(source);
                      parser.parse();
                      System.out.println(parser.isLenient());
                      return parser.getPDDocument();
                  }
              }
              """
          )
        );
    }

    @Test
    void parseEveryParserInTheBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.io.RandomAccessRead;
              import org.apache.pdfbox.pdfparser.PDFParser;
              import org.apache.pdfbox.pdmodel.PDDocument;

              import java.io.IOException;

              class A {
                  int pages(RandomAccessRead first, RandomAccessRead second) throws IOException {
                      PDFParser one = new PDFParser(first);
                      one.parse();
                      PDDocument a = one.getPDDocument();
                      PDFParser two = new PDFParser(second);
                      two.parse();
                      PDDocument b = two.getPDDocument();
                      return a.getNumberOfPages() + b.getNumberOfPages();
                  }
              }
              """,
            """
              import org.apache.pdfbox.io.RandomAccessRead;
              import org.apache.pdfbox.pdfparser.PDFParser;
              import org.apache.pdfbox.pdmodel.PDDocument;

              import java.io.IOException;

              class A {
                  int pages(RandomAccessRead first, RandomAccessRead second) throws IOException {
                      PDFParser one = new PDFParser(first);
                      PDDocument a = one.parse();
                      PDFParser two = new PDFParser(second);
                      PDDocument b = two.parse();
                      return a.getNumberOfPages() + b.getNumberOfPages();
                  }
              }
              """
          )
        );
    }

    @Test
    void parseWhereTheCodeParsedWhenTheResultIsGotLaterOrConditionally() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.io.RandomAccessRead;
              import org.apache.pdfbox.pdfparser.PDFParser;
              import org.apache.pdfbox.pdmodel.PDDocument;

              import java.io.IOException;

              class A {
                  PDDocument later(RandomAccessRead source) throws IOException {
                      PDFParser parser = new PDFParser(source);
                      parser.parse();
                      source.close();
                      return parser.getPDDocument();
                  }

                  int conditionally(RandomAccessRead source, boolean count) throws IOException {
                      PDFParser parser = new PDFParser(source);
                      parser.parse();
                      if (count) {
                          return parser.getPDDocument().getNumberOfPages();
                      }
                      for (int document = 0; document < 1; document++) {
                          String document1 = "taken";
                      }
                      return 0;
                  }
              }
              """,
            """
              import org.apache.pdfbox.io.RandomAccessRead;
              import org.apache.pdfbox.pdfparser.PDFParser;
              import org.apache.pdfbox.pdmodel.PDDocument;

              import java.io.IOException;

              class A {
                  PDDocument later(RandomAccessRead source) throws IOException {
                      PDFParser parser = new PDFParser(source);
                      PDDocument document = parser.parse();
                      source.close();
                      return document;
                  }

                  int conditionally(RandomAccessRead source, boolean count) throws IOException {
                      PDFParser parser = new PDFParser(source);
                      PDDocument document2 = parser.parse();
                      if (count) {
                          return document2.getNumberOfPages();
                      }
                      for (int document = 0; document < 1; document++) {
                          String document1 = "taken";
                      }
                      return 0;
                  }
              }
              """
          )
        );
    }
}
