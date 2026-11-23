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
package org.openrewrite.apache.commons.lang;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class StringEscapeUtilsWriterMethodsToCommonsTextTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "commons-lang-2",
            "commons-lang3",
            "commons-text"))
          .recipe(new StringEscapeUtilsWriterMethodsToCommonsText());
    }

    @DocumentExample
    @Test
    void escapeXmlIntoWriter() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.StringEscapeUtils;

              import java.io.IOException;
              import java.io.Writer;

              class A {
                  void f(Writer out, String url) throws IOException {
                      StringEscapeUtils.escapeXml(out, url);
                  }
              }
              """,
            """
              import org.apache.commons.text.StringEscapeUtils;

              import java.io.IOException;
              import java.io.Writer;

              class A {
                  void f(Writer out, String url) throws IOException {
                      StringEscapeUtils.ESCAPE_XML10.translate(url, out);
                  }
              }
              """
          )
        );
    }

    @Test
    void otherMethodsAndKeepsStringVariants() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.StringEscapeUtils;

              import java.io.IOException;
              import java.io.Writer;

              class A {
                  String f(Writer out, String s) throws IOException {
                      StringEscapeUtils.unescapeHtml(out, s);
                      StringEscapeUtils.escapeJavaScript(out, s);
                      return StringEscapeUtils.escapeHtml(s);
                  }
              }
              """,
            """
              import org.apache.commons.lang.StringEscapeUtils;

              import java.io.IOException;
              import java.io.Writer;

              class A {
                  String f(Writer out, String s) throws IOException {
                      org.apache.commons.text.StringEscapeUtils.UNESCAPE_HTML4.translate(s, out);
                      org.apache.commons.text.StringEscapeUtils.ESCAPE_ECMASCRIPT.translate(s, out);
                      return StringEscapeUtils.escapeHtml(s);
                  }
              }
              """
          )
        );
    }
}
