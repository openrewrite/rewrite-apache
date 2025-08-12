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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Objects.requireNonNull;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateSSLConnectionSocketFactory extends Recipe {
    private static final String HTTPCLIENT_4_SSL_CONNECTION_SOCKET_FACTORY = "org.apache.http.conn.ssl.SSLConnectionSocketFactory";
    private static final String HTTPCLIENT_5_SSL_CONNECTION_SOCKET_FACTORY = "org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory";

    private static final String HTTP_CLIENT_CONNECTION_MANAGER = "org.apache.hc.client5.http.io.HttpClientConnectionManager";
    private static final String POOLING_HTTP_CLIENT_CONNECTION_MANAGER_BUILDER = "org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder";
    private static final String DEFAULT_TLS_SOCKET_STRATEGY = "org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy";
    private static final String TLS_SOCKET_STRATEGY = "org.apache.hc.client5.http.ssl.TlsSocketStrategy";

    private static final MethodMatcher SET_SSL_SOCKET_FACTORY = new MethodMatcher(
            "org.apache..*..HttpClientBuilder setSSLSocketFactory(..)");

    @Override
    public String getDisplayName() {
        return "Migrate deprecated `SSLConnectionSocketFactory` to `DefaultClientTlsStrategy`";
    }

    @Override
    public String getDescription() {
        return "Migrates usage of the deprecated `org.apache.http.conn.ssl.SSLConnectionSocketFactory` " +
                "to `org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy` with proper connection manager setup.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> conditions = Preconditions.or(
                new UsesType<>(HTTPCLIENT_4_SSL_CONNECTION_SOCKET_FACTORY, false),
                new UsesType<>(HTTPCLIENT_5_SSL_CONNECTION_SOCKET_FACTORY, false)
        );
        return Preconditions.check(conditions, new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                        // Check if this method contains the SSLConnectionSocketFactory pattern
                        if (m.getBody() == null) {
                            return m;
                        }

                        J.VariableDeclarations tlsStrategyDecl = null;
                        boolean needsConnectionManager = false;
                        int tlsStrategyIndex = -1;

                        // First pass: check if we have the pattern and find the transformed declaration
                        for (int i = 0; i < m.getBody().getStatements().size(); i++) {
                            Statement stmt = m.getBody().getStatements().get(i);

                            if (stmt instanceof J.VariableDeclarations) {
                                J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                                if (!vd.getVariables().isEmpty() &&
                                        TypeUtils.isOfClassType(vd.getType(), "org.apache.hc.client5.http.ssl.TlsSocketStrategy")) {
                                    tlsStrategyDecl = vd;
                                    tlsStrategyIndex = i;
                                }
                            }

                            // Check in the complete statement tree for setConnectionManager calls
                            if (stmt.toString().contains("setConnectionManager")) {
                                needsConnectionManager = true;
                            }
                        }

                        // If we found a tlsSocketStrategy declaration and need a connection manager, add it
                        if (tlsStrategyDecl != null && needsConnectionManager) {
                            boolean connectionManagerExists = false;
                            for (int i = tlsStrategyIndex + 1; i < m.getBody().getStatements().size(); i++) {
                                Statement stmt = m.getBody().getStatements().get(i);
                                if (stmt instanceof J.VariableDeclarations) {
                                    J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                                    if (!vd.getVariables().isEmpty() &&
                                            TypeUtils.isOfClassType(vd.getVariables().get(0).getType(), "org.apache.hc.client5.http.io.HttpClientConnectionManager")) {
                                        connectionManagerExists = true;
                                        break;
                                    }
                                }
                            }

                            if (!connectionManagerExists) {

                                maybeAddImport(HTTP_CLIENT_CONNECTION_MANAGER);
                                maybeAddImport(POOLING_HTTP_CLIENT_CONNECTION_MANAGER_BUILDER);
                                return JavaTemplate.builder("HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()" +
                                                ".setTlsSocketStrategy(tlsSocketStrategy).build();")
                                        .contextSensitive()
                                        .javaParser(JavaParser.fromJavaVersion()
                                                .classpathFromResources(ctx, "httpclient5", "httpcore5"))
                                        .imports(HTTP_CLIENT_CONNECTION_MANAGER,
                                                POOLING_HTTP_CLIENT_CONNECTION_MANAGER_BUILDER)
                                        .build()
                                        .apply(updateCursor(m), tlsStrategyDecl.getCoordinates().after());
                            }
                        }

                        return m;
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);

                        // Check if this is a SSLConnectionSocketFactory variable declaration
                        // The SSLConnectionSocketFactory in HttpClient 5 has been deprecated and replaced with TlsSocketStrategy
                        if ((TypeUtils.isOfClassType(vd.getType(), HTTPCLIENT_4_SSL_CONNECTION_SOCKET_FACTORY) ||
                                TypeUtils.isOfClassType(vd.getType(), HTTPCLIENT_5_SSL_CONNECTION_SOCKET_FACTORY)) &&
                                !vd.getVariables().isEmpty() &&
                                vd.getVariables().get(0).getInitializer() instanceof J.NewClass) {
                            J.NewClass newClass = requireNonNull((J.NewClass) vd.getVariables().get(0).getInitializer());
                            boolean hasArgument = !newClass.getArguments().isEmpty() &&
                                    newClass.getArguments().size() == 1 &&
                                    newClass.getArguments().get(0) instanceof J.Identifier;

                            if (hasArgument) {
                                // Replace SSLConnectionSocketFactory with TlsSocketStrategy
                                maybeRemoveImport(HTTPCLIENT_4_SSL_CONNECTION_SOCKET_FACTORY);
                                maybeRemoveImport(HTTPCLIENT_5_SSL_CONNECTION_SOCKET_FACTORY);
                                maybeAddImport(TLS_SOCKET_STRATEGY);
                                maybeAddImport(DEFAULT_TLS_SOCKET_STRATEGY);

                                String code = "TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(#{any(javax.net.ssl.SSLContext)})";
                                return JavaTemplate.builder(code)
                                        .contextSensitive()
                                        .javaParser(JavaParser.fromJavaVersion()
                                                .classpathFromResources(ctx, "httpclient5", "httpcore5"))
                                        .imports(TLS_SOCKET_STRATEGY,
                                                DEFAULT_TLS_SOCKET_STRATEGY)
                                        .build()
                                        .apply(getCursor(), vd.getCoordinates().replace(), new Object[]{newClass.getArguments().get(0)});
                            }
                        }
                        return vd;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        // Check if this is a setConnectionManager call with SSLConnectionSocketFactory as argument
                        if (SET_SSL_SOCKET_FACTORY.matches(mi)) {
                            if (mi.getArguments().size() == 1 && mi.getArguments().get(0) instanceof J.Identifier) {
                                J.Identifier arg = (J.Identifier) mi.getArguments().get(0);
                                // Check if the identifier type is SSLConnectionSocketFactory
                                if (arg.getType() != null &&
                                        (TypeUtils.isOfClassType(arg.getType(), HTTPCLIENT_4_SSL_CONNECTION_SOCKET_FACTORY) ||
                                                TypeUtils.isOfClassType(arg.getType(), HTTPCLIENT_5_SSL_CONNECTION_SOCKET_FACTORY))) {
                                    // Replace the method call
                                    return JavaTemplate.builder("#{any()}.setConnectionManager(cm)")
                                            .contextSensitive()
                                            .javaParser(JavaParser.fromJavaVersion()
                                                    .classpathFromResources(ctx, "httpclient5", "httpcore5"))
                                            .build()
                                            .apply(getCursor(), mi.getCoordinates().replace(), mi.getSelect());
                                }
                            }
                        }
                        return mi;
                    }
                }
        );
    }
}
