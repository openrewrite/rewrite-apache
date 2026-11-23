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

class MigrateDocumentLoadingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        PdfBoxTestParsers.pdfBox2To3(spec).recipe(new MigrateDocumentLoading());
    }

    @DocumentExample
    @Test
    void loadFromFileAndBytes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.PDDocument;

              import java.io.File;
              import java.io.IOException;

              class A {
                  int pages(File file, byte[] bytes, String password) throws IOException {
                      try (PDDocument a = PDDocument.load(file);
                           PDDocument b = PDDocument.load(bytes);
                           PDDocument c = PDDocument.load(file, password)) {
                          return a.getNumberOfPages() + b.getNumberOfPages() + c.getNumberOfPages();
                      }
                  }
              }
              """,
            """
              import org.apache.pdfbox.Loader;
              import org.apache.pdfbox.pdmodel.PDDocument;

              import java.io.File;
              import java.io.IOException;

              class A {
                  int pages(File file, byte[] bytes, String password) throws IOException {
                      try (PDDocument a = Loader.loadPDF(file);
                           PDDocument b = Loader.loadPDF(bytes);
                           PDDocument c = Loader.loadPDF(file, password)) {
                          return a.getNumberOfPages() + b.getNumberOfPages() + c.getNumberOfPages();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void loadFromInputStream() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.PDDocument;

              import java.io.IOException;
              import java.io.InputStream;

              class A {
                  PDDocument open(InputStream in, String password) throws IOException {
                      return password == null ? PDDocument.load(in) : PDDocument.load(in, password);
                  }
              }
              """,
            """
              import org.apache.pdfbox.Loader;
              import org.apache.pdfbox.io.RandomAccessReadBuffer;
              import org.apache.pdfbox.pdmodel.PDDocument;

              import java.io.IOException;
              import java.io.InputStream;

              class A {
                  PDDocument open(InputStream in, String password) throws IOException {
                      return password == null ? Loader.loadPDF(new RandomAccessReadBuffer(in)) : Loader.loadPDF(new RandomAccessReadBuffer(in), password);
                  }
              }
              """
          )
        );
    }

    @Test
    void memoryUsageSettings() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.io.MemoryUsageSetting;
              import org.apache.pdfbox.pdmodel.PDDocument;

              import java.io.File;
              import java.io.IOException;
              import java.io.InputStream;

              class A {
                  void open(File file, InputStream in, File tmp) throws IOException {
                      PDDocument.load(file, MemoryUsageSetting.setupMainMemoryOnly()).close();
                      PDDocument.load(in, MemoryUsageSetting.setupTempFileOnly()).close();
                      PDDocument.load(file, "secret", MemoryUsageSetting.setupMixed(1024L).setTempDir(tmp)).close();
                      PDDocument.load(file, (MemoryUsageSetting) null).close();
                  }
              }
              """,
            """
              import org.apache.pdfbox.Loader;
              import org.apache.pdfbox.io.IOUtils;
              import org.apache.pdfbox.io.MemoryUsageSetting;
              import org.apache.pdfbox.io.RandomAccessReadBuffer;

              import java.io.File;
              import java.io.IOException;
              import java.io.InputStream;

              class A {
                  void open(File file, InputStream in, File tmp) throws IOException {
                      Loader.loadPDF(file, IOUtils.createMemoryOnlyStreamCache()).close();
                      Loader.loadPDF(new RandomAccessReadBuffer(in), IOUtils.createTempFileOnlyStreamCache()).close();
                      Loader.loadPDF(file, "secret", MemoryUsageSetting.setupMixed(1024L).setTempDir(tmp).streamCache).close();
                      Loader.loadPDF(file).close();
                  }
              }
              """
          )
        );
    }

    @Test
    void commonsIoUtilsAlreadyImported() {
        rewriteRun(
          spec -> PdfBoxTestParsers.pdfBox2To3(spec, "commons-io"),
          //language=java
          java(
            """
              import org.apache.commons.io.IOUtils;
              import org.apache.pdfbox.io.MemoryUsageSetting;
              import org.apache.pdfbox.pdmodel.PDDocument;

              import java.io.IOException;
              import java.io.InputStream;

              class A {
                  int pages(InputStream in) throws IOException {
                      byte[] bytes = IOUtils.toByteArray(in);
                      try (PDDocument document = PDDocument.load(bytes, null, null, null, MemoryUsageSetting.setupMainMemoryOnly())) {
                          return document.getNumberOfPages();
                      }
                  }
              }
              """,
            """
              import org.apache.commons.io.IOUtils;
              import org.apache.pdfbox.Loader;
              import org.apache.pdfbox.pdmodel.PDDocument;

              import java.io.IOException;
              import java.io.InputStream;

              class A {
                  int pages(InputStream in) throws IOException {
                      byte[] bytes = IOUtils.toByteArray(in);
                      try (PDDocument document = Loader.loadPDF(bytes, null, null, null, org.apache.pdfbox.io.IOUtils.createMemoryOnlyStreamCache())) {
                          return document.getNumberOfPages();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void loadFdfAndXfdf() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.fdf.FDFDocument;

              import java.io.File;
              import java.io.IOException;
              import java.io.InputStream;

              class A {
                  void open(File file, InputStream in) throws IOException {
                      FDFDocument.load(file).close();
                      FDFDocument.loadXFDF(in).close();
                  }
              }
              """,
            """
              import org.apache.pdfbox.Loader;

              import java.io.File;
              import java.io.IOException;
              import java.io.InputStream;

              class A {
                  void open(File file, InputStream in) throws IOException {
                      Loader.loadFDF(file).close();
                      Loader.loadXFDF(in).close();
                  }
              }
              """
          )
        );
    }
}
