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
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(HTTPCLIENT_4_SSL_CONNECTION_SOCKET_FACTORY, false),
                        new UsesType<>(HTTPCLIENT_5_SSL_CONNECTION_SOCKET_FACTORY, false)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                        // First pass: transform SSLConnectionSocketFactory to TlsSocketStrategy
                        cu = (J.CompilationUnit) new TransformSSLConnectionSocketFactoryVisitor().visitNonNull(cu, ctx);
                        // Second pass: add connection manager if needed
                        cu = (J.CompilationUnit) new AddConnectionManagerVisitor().visitNonNull(cu, ctx);
                        // Third pass: transform setSSLSocketFactory to setConnectionManager
                        return (J.CompilationUnit) new TransformSetSSLSocketFactoryVisitor().visitNonNull(cu, ctx);
                    }
                }
        );
    }

    private static class TransformSSLConnectionSocketFactoryVisitor extends JavaIsoVisitor<ExecutionContext> {
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
                boolean hasExactlyOneArgument = !newClass.getArguments().isEmpty() &&
                        newClass.getArguments().size() == 1 &&
                        newClass.getArguments().get(0) instanceof J.Identifier;

                if (hasExactlyOneArgument) {
                    String code = "TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(#{any(javax.net.ssl.SSLContext)})";
                    maybeRemoveImport(HTTPCLIENT_4_SSL_CONNECTION_SOCKET_FACTORY);
                    maybeRemoveImport(HTTPCLIENT_5_SSL_CONNECTION_SOCKET_FACTORY);
                    maybeAddImport(TLS_SOCKET_STRATEGY);
                    maybeAddImport(DEFAULT_TLS_SOCKET_STRATEGY);
                    return JavaTemplate.builder(code)
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "httpclient5", "httpcore5"))
                            .imports(TLS_SOCKET_STRATEGY,
                                    DEFAULT_TLS_SOCKET_STRATEGY)
                            .build()
                            .apply(getCursor(), vd.getCoordinates().replace(), newClass.getArguments().get(0));
                }
            }

            return vd;
        }
    }

    private static class AddConnectionManagerVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String HTTP_CLIENT_CONNECTION_MANAGER = "org.apache.hc.client5.http.io.HttpClientConnectionManager";
        private static final String POOLING_HTTP_CLIENT_CONNECTION_MANAGER_BUILDER = "org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder";

        private boolean hasSetSSLSocketFactory = false;

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            if (m.getBody() == null) {
                return m;
            }

            // Needs to reset for each method
            hasSetSSLSocketFactory = false;
            J.VariableDeclarations tlsStrategyDecl = null;
            int tlsStrategyIndex = -1;

            // Find TlsSocketStrategy declaration and check for setSSLSocketFactory
            for (int i = 0; i < m.getBody().getStatements().size(); i++) {
                Statement stmt = m.getBody().getStatements().get(i);

                if (stmt instanceof J.VariableDeclarations) {
                    J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                    if (!vd.getVariables().isEmpty() &&
                            TypeUtils.isOfClassType(vd.getType(), TLS_SOCKET_STRATEGY)) {
                        tlsStrategyDecl = vd;
                        tlsStrategyIndex = i;
                    }
                }

                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        if (SET_SSL_SOCKET_FACTORY.matches(method)) {
                            hasSetSSLSocketFactory = true;
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }
                }.visitNonNull(stmt, ctx);
            }

            if (tlsStrategyDecl != null && hasSetSSLSocketFactory) {
                // Check if connection manager already exists
                boolean connectionManagerExists = false;
                for (int i = tlsStrategyIndex + 1; i < m.getBody().getStatements().size(); i++) {
                    Statement stmt = m.getBody().getStatements().get(i);
                    if (stmt instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                        if (!vd.getVariables().isEmpty() &&
                                TypeUtils.isOfClassType(vd.getVariables().get(0).getType(), HTTP_CLIENT_CONNECTION_MANAGER)) {
                            connectionManagerExists = true;
                            break;
                        }
                    }
                }

                if (!connectionManagerExists) {
                    J.Identifier tlsStrategyIdentifier = tlsStrategyDecl.getVariables().get(0).getName();
                    String httpClientConnectionManagerCode = "HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()" +
                            ".setTlsSocketStrategy(#{any(org.apache.hc.client5.http.ssl.TlsSocketStrategy)}).build();";

                    maybeAddImport(HTTP_CLIENT_CONNECTION_MANAGER);
                    maybeAddImport(POOLING_HTTP_CLIENT_CONNECTION_MANAGER_BUILDER);

                    return JavaTemplate.builder(httpClientConnectionManagerCode)
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "httpclient5", "httpcore5"))
                            .imports(HTTP_CLIENT_CONNECTION_MANAGER,
                                    POOLING_HTTP_CLIENT_CONNECTION_MANAGER_BUILDER)
                            .build()
                            .apply(updateCursor(m), tlsStrategyDecl.getCoordinates().after(), tlsStrategyIdentifier);
                }
            }

            return m;
        }
    }

    private static class TransformSetSSLSocketFactoryVisitor extends JavaIsoVisitor<ExecutionContext> {

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
                        maybeRemoveImport(HTTPCLIENT_4_SSL_CONNECTION_SOCKET_FACTORY);
                        maybeRemoveImport(HTTPCLIENT_5_SSL_CONNECTION_SOCKET_FACTORY);
                        // Always replace setSSLSocketFactory with setConnectionManager
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
}
