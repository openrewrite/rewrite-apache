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

class MigrateMemoryUsageSettingsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        PdfBoxTestParsers.pdfBox2To3(spec).recipe(new MigrateMemoryUsageSettings());
    }

    @DocumentExample
    @Test
    void mergeAndSplit() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.io.MemoryUsageSetting;
              import org.apache.pdfbox.multipdf.PDFMergerUtility;
              import org.apache.pdfbox.multipdf.Splitter;

              import java.io.File;
              import java.io.IOException;

              class A {
                  void merge(PDFMergerUtility merger, Splitter splitter, File tmp) throws IOException {
                      merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
                      merger.mergeDocuments(MemoryUsageSetting.setupMixed(1_000_000L).setTempDir(tmp));
                      merger.mergeDocuments(null);
                      merger.mergeDocuments();
                      splitter.setMemoryUsageSetting(MemoryUsageSetting.setupTempFileOnly());
                  }
              }
              """,
            """
              import org.apache.pdfbox.io.IOUtils;
              import org.apache.pdfbox.io.MemoryUsageSetting;
              import org.apache.pdfbox.multipdf.PDFMergerUtility;
              import org.apache.pdfbox.multipdf.Splitter;

              import java.io.File;
              import java.io.IOException;

              class A {
                  void merge(PDFMergerUtility merger, Splitter splitter, File tmp) throws IOException {
                      merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());
                      merger.mergeDocuments(MemoryUsageSetting.setupMixed(1_000_000L).setTempDir(tmp).streamCache);
                      merger.mergeDocuments(null);
                      merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());
                      splitter.setStreamCacheCreateFunction(IOUtils.createTempFileOnlyStreamCache());
                  }
              }
              """
          )
        );
    }

    @Test
    void newDocument() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.io.MemoryUsageSetting;
              import org.apache.pdfbox.pdmodel.PDDocument;

              class A {
                  PDDocument create(MemoryUsageSetting setting) {
                      return new PDDocument(setting);
                  }
              }
              """,
            """
              import org.apache.pdfbox.io.MemoryUsageSetting;
              import org.apache.pdfbox.pdmodel.PDDocument;

              class A {
                  PDDocument create(MemoryUsageSetting setting) {
                      return new PDDocument(setting.streamCache);
                  }
              }
              """
          )
        );
    }
}
