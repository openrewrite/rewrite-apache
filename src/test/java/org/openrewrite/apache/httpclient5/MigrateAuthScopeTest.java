package org.openrewrite.apache.httpclient5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateAuthScopeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("httpclient", "httpcore", "httpclient5", "httpcore5"))
          .afterTypeValidationOptions(TypeValidation.none())
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite")
            .build()
            .activateRecipes("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5")
          );
    }

    @DocumentExample
    @Test
    void authScopeAnyTest() {
        rewriteRun(
          //language=java
          java(
            """
            import org.apache.http.auth.AuthScope;
            
            class A {
                void method() {
                    AuthScope any = AuthScope.ANY;
                }
            }
            """, """
            import org.apache.hc.client5.http.auth.AuthScope;
            
            class A {
                void method() {
                    AuthScope any = new AuthScope(null, -1);
                }
            }
            """
          )
        );
    }
}