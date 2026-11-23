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

class MigrateRandomAccessReadsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        PdfBoxTestParsers.pdfBox2To3(spec).recipe(new MigrateRandomAccessReads());
    }

    @DocumentExample
    @Test
    void readers() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.io.MemoryUsageSetting;
              import org.apache.pdfbox.io.RandomAccessBuffer;
              import org.apache.pdfbox.io.RandomAccessBufferedFileInputStream;
              import org.apache.pdfbox.io.RandomAccessFile;
              import org.apache.pdfbox.io.ScratchFile;
              import org.apache.pdfbox.pdfparser.PDFParser;

              import java.io.File;
              import java.io.IOException;
              import java.io.InputStream;

              class A {
                  void parse(File file, InputStream in, byte[] bytes) throws IOException {
                      new PDFParser(new RandomAccessFile(file, "r"));
                      new PDFParser(new RandomAccessBufferedFileInputStream(in), new ScratchFile(MemoryUsageSetting.setupTempFileOnly()));
                      new PDFParser(new RandomAccessBuffer(bytes), "secret");
                      new PDFParser(new RandomAccessBuffer(in));
                      new RandomAccessBuffer().close();
                      RandomAccessBuffer buffer = new RandomAccessBuffer();
                      buffer.write(bytes);
                  }
              }
              """,
            """
              import org.apache.pdfbox.io.RandomAccessReadBuffer;
              import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
              import org.apache.pdfbox.io.RandomAccessReadWriteBuffer;
              import org.apache.pdfbox.pdfparser.PDFParser;

              import java.io.File;
              import java.io.IOException;
              import java.io.InputStream;

              class A {
                  void parse(File file, InputStream in, byte[] bytes) throws IOException {
                      new PDFParser(new RandomAccessReadBufferedFile(file));
                      new PDFParser(new RandomAccessReadBuffer(in));
                      new PDFParser(new RandomAccessReadBuffer(bytes), "secret");
                      new PDFParser(new RandomAccessReadBuffer(in));
                      new RandomAccessReadWriteBuffer().close();
                      RandomAccessReadWriteBuffer buffer = new RandomAccessReadWriteBuffer();
                      buffer.write(bytes);
                  }
              }
              """
          )
        );
    }

    @Test
    void fontParsers() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.fontbox.ttf.OTFParser;
              import org.apache.fontbox.ttf.OpenTypeFont;
              import org.apache.fontbox.ttf.TTFParser;
              import org.apache.fontbox.ttf.TrueTypeFont;

              import java.io.File;
              import java.io.IOException;
              import java.io.InputStream;

              class A {
                  void parse(File file, InputStream in, String path) throws IOException {
                      TrueTypeFont a = new TTFParser().parse(file);
                      TrueTypeFont b = new TTFParser().parse(in);
                      OpenTypeFont c = new OTFParser().parse(path);
                  }
              }
              """,
            """
              import org.apache.fontbox.ttf.OTFParser;
              import org.apache.fontbox.ttf.OpenTypeFont;
              import org.apache.fontbox.ttf.TTFParser;
              import org.apache.fontbox.ttf.TrueTypeFont;
              import org.apache.pdfbox.io.RandomAccessReadBuffer;
              import org.apache.pdfbox.io.RandomAccessReadBufferedFile;

              import java.io.File;
              import java.io.IOException;
              import java.io.InputStream;

              class A {
                  void parse(File file, InputStream in, String path) throws IOException {
                      TrueTypeFont a = new TTFParser().parse(new RandomAccessReadBufferedFile(file));
                      TrueTypeFont b = new TTFParser().parse(new RandomAccessReadBuffer(in));
                      OpenTypeFont c = new OTFParser().parse(new RandomAccessReadBufferedFile(path));
                  }
              }
              """
          )
        );
    }

    @Test
    void mergerSources() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.multipdf.PDFMergerUtility;

              import java.io.ByteArrayInputStream;
              import java.io.InputStream;

              class A {
                  void add(PDFMergerUtility merger, byte[] pdf) {
                      merger.addSource(new ByteArrayInputStream(pdf));
                  }

                  void addOrFail(PDFMergerUtility merger, InputStream in) throws Exception {
                      merger.addSource(in);
                  }
              }
              """,
            """
              import org.apache.pdfbox.io.RandomAccessReadBuffer;
              import org.apache.pdfbox.multipdf.PDFMergerUtility;

              import java.io.InputStream;

              class A {
                  void add(PDFMergerUtility merger, byte[] pdf) {
                      merger.addSource(new RandomAccessReadBuffer(pdf));
                  }

                  void addOrFail(PDFMergerUtility merger, InputStream in) throws Exception {
                      merger.addSource(new RandomAccessReadBuffer(in));
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveStreamSourceWhereReadingCouldFail() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.multipdf.PDFMergerUtility;

              import java.io.InputStream;

              class A {
                  void add(PDFMergerUtility merger, InputStream in) {
                      merger.addSource(in);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepReadWriteFile() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.io.RandomAccessFile;

              import java.io.File;
              import java.io.IOException;

              class A {
                  void open(File file) throws IOException {
                      new RandomAccessFile(file, "rw").close();
                  }
              }
              """
          )
        );
    }
}
