package org.openrewrite.apache.httpclient5;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class NewStatusLineTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("httpclient", "httpcore"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @Test
    void removeStatusLineHttpResponse() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpStatus;
              import org.apache.http.client.methods.CloseableHttpResponse;
              import org.apache.http.client.methods.HttpGet;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClientBuilder;
              import org.apache.http.ProtocolVersion;

              import java.io.IOException;

              class A {
                  void method() throws IOException {
                      HttpGet httpGet = new HttpGet("https://moderne.io");
                      CloseableHttpClient instance = HttpClientBuilder.create().build();
                      CloseableHttpResponse response = instance.execute(httpGet);

                      System.out.println("response.getStatusLine() :: " + response.getStatusLine());
                      int statusCode = response.getStatusLine().getStatusCode();
                      String reason = response.getStatusLine().getReasonPhrase();
                      ProtocolVersion version = response.getStatusLine().getProtocolVersion();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
              import org.apache.hc.core5.http.HttpStatus;
              import org.apache.hc.client5.http.classic.methods.HttpGet;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
              import org.apache.hc.core5.http.ProtocolVersion;
              import org.apache.hc.core5.http.message.StatusLine;

              import java.io.IOException;

              class A {
                  void method() throws IOException {
                      HttpGet httpGet = new HttpGet("https://moderne.io");
                      CloseableHttpClient instance = HttpClientBuilder.create().build();
                      CloseableHttpResponse response = instance.execute(httpGet);

                      System.out.println("response.getStatusLine() :: " + new StatusLine(response));
                      int statusCode = response.getCode();
                      String reason = response.getReasonPhrase();
                      ProtocolVersion version = response.getVersion();
                  }
              }
              """
          )
        );
    }
}
