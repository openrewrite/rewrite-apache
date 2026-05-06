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

class MigrateAuthStateTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
              "httpclient-4", "httpcore-4", "httpclient5", "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @DocumentExample
    @Test
    void renameTypeAndStateEnumAndMethods() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(2),
          //language=java
          java(
            """
              import org.apache.http.auth.AuthScheme;
              import org.apache.http.auth.AuthState;
              import org.apache.http.auth.AuthProtocolState;

              class A {
                  void m(AuthState s, AuthScheme scheme) {
                      s.setAuthScheme(scheme);
                      s.setState(AuthProtocolState.CHALLENGED);
                      s.reset();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.auth.AuthExchange.State;
              import org.apache.hc.client5.http.auth.AuthScheme;
              import org.apache.hc.client5.http.auth.AuthExchange;

              class A {
                  void m(AuthExchange s, AuthScheme scheme) {
                      s.select(scheme);
                      s.setState(AuthExchange.State.CHALLENGED);
                      s.reset();
                  }
              }
              """
          )
        );
    }

    @Test
    void updateWithBasicSchemeRewritesToInitPreemptiveAndSelect() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(2),
          //language=java
          java(
            """
              import org.apache.http.auth.AuthState;
              import org.apache.http.auth.Credentials;
              import org.apache.http.impl.auth.BasicScheme;

              class A {
                  void m(AuthState s, Credentials creds) {
                      BasicScheme scheme = new BasicScheme();
                      s.update(scheme, creds);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.auth.AuthExchange;
              import org.apache.hc.client5.http.auth.Credentials;
              import org.apache.hc.client5.http.impl.auth.BasicScheme;

              class A {
                  void m(AuthExchange s, Credentials creds) {
                      BasicScheme scheme = new BasicScheme();
                      scheme.initPreemptive(creds);
                      s.select(scheme);
                  }
              }
              """
          )
        );
    }

    @Test
    void updateWithGenericAuthSchemeAddsComment() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.auth.AuthScheme;
              import org.apache.http.auth.AuthState;
              import org.apache.http.auth.Credentials;

              class A {
                  void m(AuthState s, AuthScheme scheme, Credentials creds) {
                      s.update(scheme, creds);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.auth.AuthExchange;
              import org.apache.hc.client5.http.auth.AuthScheme;
              import org.apache.hc.client5.http.auth.Credentials;

              class A {
                  void m(AuthExchange s, AuthScheme scheme, Credentials creds) {
                      // HttpClient 5: AuthScheme no longer stores credentials directly. For preemptive Basic auth,
                      // cast to BasicScheme and call initPreemptive(creds). Otherwise, register the credentials with
                      // a CredentialsProvider on HttpClientBuilder and let the scheme look them up per-request.
                      s.update(scheme, creds);
                  }
              }
              """
          )
        );
    }

    @Test
    void setCredentialsAddsComment() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.auth.AuthState;
              import org.apache.http.auth.Credentials;

              class A {
                  void m(AuthState s, Credentials creds) {
                      s.setCredentials(creds);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.auth.AuthExchange;
              import org.apache.hc.client5.http.auth.Credentials;

              class A {
                  void m(AuthExchange s, Credentials creds) {
                      // HttpClient 5: AuthScheme no longer stores credentials directly. For preemptive Basic auth,
                      // cast to BasicScheme and call initPreemptive(creds). Otherwise, register the credentials with
                      // a CredentialsProvider on HttpClientBuilder and let the scheme look them up per-request.
                      s.setCredentials(creds);
                  }
              }
              """
          )
        );
    }

    @Test
    void authOptionRenamedAndGetAuthSchemeUnwrapped() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.auth.AuthOption;

              import java.util.Queue;

              class A {
                  Object process(Queue<AuthOption> options) {
                      AuthOption opt = options.peek();
                      return opt.getAuthScheme();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.auth.AuthScheme;

              import java.util.Queue;

              class A {
                  Object process(Queue<AuthScheme> options) {
                      AuthScheme opt = options.peek();
                      return opt;
                  }
              }
              """
          )
        );
    }

    @Test
    void getCredentialsAddsComment() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.auth.AuthOption;
              import org.apache.http.auth.Credentials;

              class A {
                  Credentials creds(AuthOption opt) {
                      return opt.getCredentials();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.auth.AuthScheme;
              import org.apache.hc.client5.http.auth.Credentials;

              class A {
                  Credentials creds(AuthScheme opt) {
                      return /* HttpClient 5: AuthScheme no longer exposes credentials directly. Credentials are owned by a CredentialsProvider and resolved per-request inside AuthScheme.isResponseReady(host, provider, context). */ opt.getCredentials();
                  }
              }
              """
          )
        );
    }
}
