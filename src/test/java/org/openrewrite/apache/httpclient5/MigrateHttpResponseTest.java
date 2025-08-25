package org.openrewrite.apache.httpclient5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class MigrateHttpResponseTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "httpclient-4", "httpcore-4",
              "httpclient5", "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @DocumentExample
    @Test
    void migratesHttpResponseToClassicHttpResponse() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpEntity;
              import org.apache.http.HttpResponse;
              import org.apache.http.client.methods.HttpGet;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClients;

              import java.io.IOException;

              class HttpClientManager {
                  void getEntity() throws IOException {
                      CloseableHttpClient httpClient = HttpClients.createDefault();
                      HttpGet httpGet = new HttpGet("https://example.com");
                      HttpResponse response = httpClient.execute(httpGet);
                      HttpEntity entity = response.getEntity();
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ClassicHttpResponse;
              import org.apache.hc.core5.http.HttpEntity;
              import org.apache.hc.client5.http.classic.methods.HttpGet;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClients;

              import java.io.IOException;

              class HttpClientManager {
                  void getEntity() throws IOException {
                      CloseableHttpClient httpClient = HttpClients.createDefault();
                      HttpGet httpGet = new HttpGet("https://example.com");
                      ClassicHttpResponse response = httpClient.execute(httpGet);
                      HttpEntity entity = response.getEntity();
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesCloseableHttpResponse() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpEntity;
              import org.apache.http.client.methods.CloseableHttpResponse;
              import org.apache.http.client.methods.HttpGet;
              import org.apache.http.impl.client.CloseableHttpClient;
              import org.apache.http.impl.client.HttpClients;

              import java.io.IOException;

              class HttpClientManager {
                  void getEntity() throws IOException {
                      CloseableHttpClient httpClient = HttpClients.createDefault();
                      HttpGet httpGet = new HttpGet("https://example.com");
                      CloseableHttpResponse response = httpClient.execute(httpGet);
                      HttpEntity entity = response.getEntity();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
              import org.apache.hc.core5.http.HttpEntity;
              import org.apache.hc.client5.http.classic.methods.HttpGet;
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClients;

              import java.io.IOException;

              class HttpClientManager {
                  void getEntity() throws IOException {
                      CloseableHttpClient httpClient = HttpClients.createDefault();
                      HttpGet httpGet = new HttpGet("https://example.com");
                      CloseableHttpResponse response = httpClient.execute(httpGet);
                      HttpEntity entity = response.getEntity();
                  }
              }
              """
          )
        );
    }
}
