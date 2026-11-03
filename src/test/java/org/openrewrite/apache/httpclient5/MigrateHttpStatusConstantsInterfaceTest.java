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
package org.openrewrite.apache.httpclient5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateHttpStatusConstantsInterfaceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpcore-4",
            "httpclient5",
            "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @DocumentExample
    @Test
    void removeHttpStatusFromImplements() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpStatus;

              public final class HttpUtils implements HttpStatus {
                  private HttpUtils() {
                  }

                  public static boolean isOk(int code) {
                      return code == SC_OK;
                  }

                  class Inner {
                      boolean notFound(int code) {
                          return code == SC_NOT_FOUND;
                      }
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.HttpStatus;

              public final class HttpUtils {
                  private HttpUtils() {
                  }

                  public static boolean isOk(int code) {
                      return code == HttpStatus.SC_OK;
                  }

                  class Inner {
                      boolean notFound(int code) {
                          return code == HttpStatus.SC_NOT_FOUND;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void keepOtherInterfacesAndQualifyAccessThroughSubtype() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpStatus;

              import java.io.Serializable;

              public class Codes implements HttpStatus, Serializable {
              }
              """,
            """
              import java.io.Serializable;

              public class Codes implements Serializable {
              }
              """
          ),
          //language=java
          java(
            """
              class Client {
                  boolean created(int code) {
                      return code == Codes.SC_CREATED;
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.HttpStatus;

              class Client {
                  boolean created(int code) {
                      return code == HttpStatus.SC_CREATED;
                  }
              }
              """
          )
        );
    }

    @Test
    void qualifyAccessThroughInstance() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpStatus;

              class Codes implements HttpStatus {
                  boolean ok(int code) {
                      return code == this.SC_OK;
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.HttpStatus;

              class Codes {
                  boolean ok(int code) {
                      return code == HttpStatus.SC_OK;
                  }
              }
              """
          )
        );
    }

    @Test
    void interfaceExtendingHttpStatus() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpStatus;

              import java.io.Serializable;

              interface Codes extends Serializable, HttpStatus {
                  int SC_CUSTOM = SC_OK + 1000;
              }
              """,
            """
              import org.apache.hc.core5.http.HttpStatus;

              import java.io.Serializable;

              interface Codes extends Serializable {
                  int SC_CUSTOM = HttpStatus.SC_OK + 1000;
              }
              """
          )
        );
    }

    @Test
    void noChangeForQualifiedOrStaticImportedConstants() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpStatus;

              import static org.apache.http.HttpStatus.SC_NOT_FOUND;

              class A {
                  boolean check(int code) {
                      return code == HttpStatus.SC_OK || code == SC_NOT_FOUND;
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.HttpStatus;

              import static org.apache.hc.core5.http.HttpStatus.SC_NOT_FOUND;

              class A {
                  boolean check(int code) {
                      return code == HttpStatus.SC_OK || code == SC_NOT_FOUND;
                  }
              }
              """
          )
        );
    }
}
