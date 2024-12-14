/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.apache.commons.io;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseJavaStandardCharsetsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.apache.commons.io.UseStandardCharsets")
          .parser(JavaParser.fromJavaVersion().classpath("commons-io"));
    }

    @DocumentExample
    @Test
    @SuppressWarnings("deprecation")
    void toStandardCharsets() {
        //language=java
        rewriteRun(
          java(
            """
              import java.nio.charset.Charset;
              import org.apache.commons.io.Charsets;
              
              class A {
                   Charset iso88591 = Charsets.ISO_8859_1;
                   Charset usAscii = Charsets.US_ASCII;
                   Charset utf16 = Charsets.UTF_16;
                   Charset utf16be = Charsets.UTF_16BE;
                   Charset utf16le = Charsets.UTF_16LE;
                   Charset utf8 = Charsets.UTF_8;
              }
              """,
            """
              import java.nio.charset.Charset;
              import java.nio.charset.StandardCharsets;
              
              class A {
                   Charset iso88591 = StandardCharsets.ISO_8859_1;
                   Charset usAscii = StandardCharsets.US_ASCII;
                   Charset utf16 = StandardCharsets.UTF_16;
                   Charset utf16be = StandardCharsets.UTF_16BE;
                   Charset utf16le = StandardCharsets.UTF_16LE;
                   Charset utf8 = StandardCharsets.UTF_8;
              }
              """
          )
        );
    }
}
