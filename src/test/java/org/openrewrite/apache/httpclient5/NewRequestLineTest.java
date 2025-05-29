package org.openrewrite.apache.httpclient5;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class NewRequestLineTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("httpclient", "httpcore"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @Test
    void removeRequestLineHttpResponse() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.methods.HttpGet;
              import org.apache.http.ProtocolVersion;

              class A {
                  void method() {
                      HttpGet httpGet = new HttpGet("https://moderne.io");
                      System.out.println("httpGet.getRequestLine() :: " + httpGet.getRequestLine());
                      String method = httpGet.getRequestLine().getMethod();
                      String uri = httpGet.getRequestLine().getUri();
                      ProtocolVersion version = httpGet.getRequestLine().getProtocolVersion();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.classic.methods.HttpGet;
              import org.apache.hc.core5.http.ProtocolVersion;
              import org.apache.hc.core5.http.message.RequestLine;

              class A {
                  void method() {
                      HttpGet httpGet = new HttpGet("https://moderne.io");
                      System.out.println("httpGet.getRequestLine() :: " + new RequestLine(httpGet));
                      String method = new RequestLine(httpGet).getMethod();
                      String uri = new RequestLine(httpGet).getUri();
                      ProtocolVersion version = new RequestLine(httpGet).getProtocolVersion();
                  }
              }
              """
          )
        );
    }
}
