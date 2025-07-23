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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateSSLConnectionSocketFactoryTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
                        .classpathFromResources(new InMemoryExecutionContext(),
                                "httpclient-4", "httpcore-4", "httpclient5", "httpcore5"))
                .recipe(new MigrateSSLConnectionSocketFactory())
                .afterTypeValidationOptions(TypeValidation.all().identifiers(false));
    }

    @Test
    void justVariableDeclaration() {
        rewriteRun(
            //language=java
            java(
                """
                import javax.net.ssl.SSLContext;

                import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

                public class HttpClientManager {
                    public void create(SSLContext sslContext) {
                        SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext);
                    }
                }
                """,
                """
                import javax.net.ssl.SSLContext;

                import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
                import org.apache.hc.client5.http.ssl.TlsSocketStrategy;

                public class HttpClientManager {
                    public void create(SSLContext sslContext) {
                        TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(sslContext);
                    }
                }
                """
            )
        );
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

                public class HttpClientManager {
                    public void create() {
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

                public class HttpClientManager {
                    public void create() {
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
}
