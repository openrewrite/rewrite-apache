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

class MigrateFieldWidgetsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        PdfBoxTestParsers.pdfBox2To3(spec).recipe(new MigrateFieldWidgets());
    }

    @DocumentExample
    @Test
    void firstWidget() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.pdfbox.pdmodel.common.PDRectangle;
              import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
              import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

              class A {
                  PDRectangle place(PDAcroForm form) {
                      PDTextField field = (PDTextField) form.getField("name");
                      return field.getWidget().getRectangle();
                  }
              }
              """,
            """
              import org.apache.pdfbox.pdmodel.common.PDRectangle;
              import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
              import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

              class A {
                  PDRectangle place(PDAcroForm form) {
                      PDTextField field = (PDTextField) form.getField("name");
                      return field.getWidgets().get(0).getRectangle();
                  }
              }
              """
          )
        );
    }
}
