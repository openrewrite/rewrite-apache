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

class MigrateRenamedApisTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        PdfBoxTestParsers.pdfBox2To3(spec).recipeFromResources("org.openrewrite.apache.pdfbox.MigrateRenamedApis");
    }

    @DocumentExample
    @Test
    void renamedMethods() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.cos.COSStream;
              import org.apache.pdfbox.pdmodel.common.PDStream;
              import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
              import org.apache.pdfbox.util.Matrix;

              import java.io.IOException;
              import java.io.InputStream;

              class A {
                  float read(COSStream stream, PDStream pdStream, AccessPermission permission, Matrix matrix) throws IOException {
                      permission.setCanPrintDegraded(permission.canPrintDegraded());
                      try (InputStream raw = stream.getFilteredStream();
                           InputStream decoded = stream.getUnfilteredStream()) {
                          String text = stream.getString() + pdStream.getStream().getLength();
                          return matrix.getXPosition() + matrix.getYPosition() + text.length();
                      }
                  }
              }
              """,
            """
              import org.apache.pdfbox.cos.COSStream;
              import org.apache.pdfbox.pdmodel.common.PDStream;
              import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
              import org.apache.pdfbox.util.Matrix;

              import java.io.IOException;
              import java.io.InputStream;

              class A {
                  float read(COSStream stream, PDStream pdStream, AccessPermission permission, Matrix matrix) throws IOException {
                      permission.setCanPrintFaithful(permission.canPrintFaithful());
                      try (InputStream raw = stream.createRawInputStream();
                           InputStream decoded = stream.createInputStream()) {
                          String text = stream.toTextString() + pdStream.getCOSObject().getLength();
                          return matrix.getTranslateX() + matrix.getTranslateY() + text.length();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void renamedTypes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.io.RandomAccessBufferedFileInputStream;
              import org.apache.pdfbox.pdmodel.encryption.PDEncryptionDictionary;
              import org.apache.pdfbox.util.Charsets;

              class A {
                  String describe(PDEncryptionDictionary encryption, RandomAccessBufferedFileInputStream in) {
                      return new String("V".repeat(encryption.getCOSDictionary().size()).getBytes(), Charsets.ISO_8859_1) + in;
                  }

                  byte[] encode(String text) {
                      return text.getBytes(Charsets.WINDOWS_1252);
                  }
              }
              """,
            """
              import org.apache.pdfbox.io.RandomAccessRead;
              import org.apache.pdfbox.pdmodel.encryption.PDEncryption;

              import java.nio.charset.Charset;
              import java.nio.charset.StandardCharsets;

              class A {
                  String describe(PDEncryption encryption, RandomAccessRead in) {
                      return new String("V".repeat(encryption.getCOSObject().size()).getBytes(), StandardCharsets.ISO_8859_1) + in;
                  }

                  byte[] encode(String text) {
                      return text.getBytes(Charset.forName("Windows-1252"));
                  }
              }
              """
          )
        );
    }

    @Test
    void cosArrayOfList() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.cos.COSArray;
              import org.apache.pdfbox.pdmodel.common.COSArrayList;

              class A {
                  COSArray array(COSArrayList<String> fields) {
                      return fields.getCOSArray();
                  }
              }
              """,
            """
              import org.apache.pdfbox.cos.COSArray;
              import org.apache.pdfbox.pdmodel.common.COSArrayList;

              class A {
                  COSArray array(COSArrayList<String> fields) {
                      return fields.toList();
                  }
              }
              """
          )
        );
    }
}
