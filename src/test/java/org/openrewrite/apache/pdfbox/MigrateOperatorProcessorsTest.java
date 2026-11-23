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

class MigrateOperatorProcessorsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        PdfBoxTestParsers.pdfBox2To3(spec).recipe(new MigrateOperatorProcessors());
    }

    @DocumentExample
    @Test
    void passEngine() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.contentstream.PDFStreamEngine;
              import org.apache.pdfbox.contentstream.operator.DrawObject;
              import org.apache.pdfbox.contentstream.operator.state.Concatenate;
              import org.apache.pdfbox.contentstream.operator.state.Restore;
              import org.apache.pdfbox.contentstream.operator.state.Save;

              class ImageLocator extends PDFStreamEngine {
                  ImageLocator() {
                      addOperator(new Concatenate());
                      addOperator(new DrawObject());
                      addOperator(new Save());
                      addOperator(new Restore());
                  }
              }
              """,
            """
              import org.apache.pdfbox.contentstream.PDFStreamEngine;
              import org.apache.pdfbox.contentstream.operator.DrawObject;
              import org.apache.pdfbox.contentstream.operator.state.Concatenate;
              import org.apache.pdfbox.contentstream.operator.state.Restore;
              import org.apache.pdfbox.contentstream.operator.state.Save;

              class ImageLocator extends PDFStreamEngine {
                  ImageLocator() {
                      addOperator(new Concatenate(this));
                      addOperator(new DrawObject(this));
                      addOperator(new Save(this));
                      addOperator(new Restore(this));
                  }
              }
              """
          )
        );
    }
}
