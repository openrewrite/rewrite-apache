/*
 * Copyright 2025 the original author or authors.
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

class MigrateSSLConnectionSocketFactoryTest implements RewriteTest {
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
    void migratesToDefaultClientTlsStrategy() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.net.ssl.SSLContext;

              import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
              import org.apache.http.impl.client.HttpClients;
              import org.apache.http.ssl.SSLContexts;

              class HttpClientManager {
                  void create() {
                      SSLContext sslContext = SSLContexts.createDefault();
                      SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext);
                      HttpClients.custom().setSSLSocketFactory(sslConnectionSocketFactory).build();
                  }
              }
              """,
            """
              import javax.net.ssl.SSLContext;

              import org.apache.hc.client5.http.impl.classic.HttpClients;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
              import org.apache.hc.client5.http.io.HttpClientConnectionManager;
              import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
              import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
              import org.apache.hc.core5.ssl.SSLContexts;

              class HttpClientManager {
                  void create() {
                      SSLContext sslContext = SSLContexts.createDefault();
                      TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(sslContext);
                      HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create().setTlsSocketStrategy(tlsSocketStrategy).build();
                      HttpClients.custom().setConnectionManager(cm).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesHttpClient4SSLConnectionSocketFactory() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

              import javax.net.ssl.SSLContext;

              class HttpClientManager {
                  void create(SSLContext sslContext) {
                      SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
              import org.apache.hc.client5.http.ssl.TlsSocketStrategy;

              import javax.net.ssl.SSLContext;

              class HttpClientManager {
                  void create(SSLContext sslContext) {
                      TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(sslContext);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesHttpClient5DeprecatedSSLConnectionSocketFactory() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;

              import javax.net.ssl.SSLContext;

              class HttpClientManager {
                  void create(SSLContext sslContext) {
                      SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
              import org.apache.hc.client5.http.ssl.TlsSocketStrategy;

              import javax.net.ssl.SSLContext;

              class HttpClientManager {
                  void create(SSLContext sslContext) {
                      TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(sslContext);
                  }
              }
              """
          )
        );
    }

    @Test
    void alreadyUsesTlsSocketStrategy() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.net.ssl.SSLContext;

              import org.apache.hc.client5.http.impl.classic.HttpClients;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
              import org.apache.hc.client5.http.io.HttpClientConnectionManager;
              import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
              import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
              import org.apache.hc.core5.ssl.SSLContexts;

              class HttpClientManager {
                  void create() {
                      SSLContext sslContext = SSLContexts.createDefault();
                      TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(sslContext);
                      HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create().setTlsSocketStrategy(tlsSocketStrategy).build();
                      HttpClients.custom().setConnectionManager(cm).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleConstructorArgsWithSetSSLSocketFactory() {
        // This test verifies that when SSLConnectionSocketFactory is created with SSLContext and HostnameVerifier,
        // the recipe fully migrates to DefaultClientTlsStrategy with connection manager setup.
        rewriteRun(
          //language=java
          java(
            """
              import javax.net.ssl.HostnameVerifier;
              import javax.net.ssl.SSLContext;

              import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
              import org.apache.http.impl.client.HttpClients;
              import org.apache.http.ssl.SSLContexts;

              class HttpClientManager {
                  void create() {
                      SSLContext sslContext = SSLContexts.createDefault();
                      HostnameVerifier customHostnameVerifier = (hostname, session) -> true;
                      SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext, customHostnameVerifier);
                      HttpClients.custom().setSSLSocketFactory(sslConnectionSocketFactory).build();
                  }
              }
              """,
            """
              import javax.net.ssl.HostnameVerifier;
              import javax.net.ssl.SSLContext;

              import org.apache.hc.client5.http.impl.classic.HttpClients;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
              import org.apache.hc.client5.http.io.HttpClientConnectionManager;
              import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
              import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
              import org.apache.hc.core5.ssl.SSLContexts;

              class HttpClientManager {
                  void create() {
                      SSLContext sslContext = SSLContexts.createDefault();
                      HostnameVerifier customHostnameVerifier = (hostname, session) -> true;
                      TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(sslContext, customHostnameVerifier);
                      HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create().setTlsSocketStrategy(tlsSocketStrategy).build();
                      HttpClients.custom().setConnectionManager(cm).build();
                  }
              }
              """
          )
        );
    }
}
