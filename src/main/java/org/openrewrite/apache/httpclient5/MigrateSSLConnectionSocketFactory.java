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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
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

    String displayName = "Migrate deprecated `SSLConnectionSocketFactory` to `DefaultClientTlsStrategy`";

    String description = "Migrates usage of the deprecated `org.apache.http.conn.ssl.SSLConnectionSocketFactory` " +
            "to `org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy` with proper connection manager setup.";

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
                boolean hasOneArgSSLContext = newClass.getArguments().size() == 1 &&
                        TypeUtils.isAssignableTo("javax.net.ssl.SSLContext", newClass.getArguments().get(0).getType());

                boolean hasTwoArgsWithHostnameVerifier = newClass.getArguments().size() == 2 &&
                        TypeUtils.isAssignableTo("javax.net.ssl.SSLContext", newClass.getArguments().get(0).getType()) &&
                        TypeUtils.isAssignableTo("javax.net.ssl.HostnameVerifier", newClass.getArguments().get(1).getType());

                if (hasOneArgSSLContext) {
                    maybeRemoveImport(HTTPCLIENT_4_SSL_CONNECTION_SOCKET_FACTORY);
                    maybeRemoveImport(HTTPCLIENT_5_SSL_CONNECTION_SOCKET_FACTORY);
                    maybeAddImport(TLS_SOCKET_STRATEGY);
                    maybeAddImport(DEFAULT_TLS_SOCKET_STRATEGY);
                    String code = "TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(#{any(javax.net.ssl.SSLContext)})";
                    return JavaTemplate.builder(code)
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "httpclient5", "httpcore5"))
                            .imports(TLS_SOCKET_STRATEGY, DEFAULT_TLS_SOCKET_STRATEGY)
                            .build()
                            .apply(getCursor(), vd.getCoordinates().replace(), newClass.getArguments().get(0));
                }
                if (hasTwoArgsWithHostnameVerifier) {
                    maybeRemoveImport(HTTPCLIENT_4_SSL_CONNECTION_SOCKET_FACTORY);
                    maybeRemoveImport(HTTPCLIENT_5_SSL_CONNECTION_SOCKET_FACTORY);
                    maybeAddImport(TLS_SOCKET_STRATEGY);
                    maybeAddImport(DEFAULT_TLS_SOCKET_STRATEGY);
                    String code = "TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(#{any(javax.net.ssl.SSLContext)}, #{any(javax.net.ssl.HostnameVerifier)})";
                    return JavaTemplate.builder(code)
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "httpclient5", "httpcore5"))
                            .imports(TLS_SOCKET_STRATEGY, DEFAULT_TLS_SOCKET_STRATEGY)
                            .build()
                            .apply(getCursor(), vd.getCoordinates().replace(),
                                    newClass.getArguments().get(0), newClass.getArguments().get(1));
                }
            }

            return vd;
        }
    }

    private static class AddConnectionManagerVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String HTTP_CLIENT_CONNECTION_MANAGER = "org.apache.hc.client5.http.io.HttpClientConnectionManager";
        private static final String POOLING_HTTP_CLIENT_CONNECTION_MANAGER_BUILDER = "org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder";

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            if (m.getBody() == null) {
                return m;
            }

            class MethodAnalyzer extends JavaIsoVisitor<ExecutionContext> {
                boolean connectionManagerExists;
                boolean hasSetSSLSocketFactory;
                J.@Nullable VariableDeclarations tlsStrategyDecl;

                @Override
                public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations vd, ExecutionContext ctx) {
                    if (!vd.getVariables().isEmpty()) {
                        if (TypeUtils.isOfClassType(vd.getType(), TLS_SOCKET_STRATEGY)) {
                            tlsStrategyDecl = vd;
                        } else if (TypeUtils.isOfClassType(vd.getVariables().get(0).getType(), HTTP_CLIENT_CONNECTION_MANAGER)) {
                            connectionManagerExists = true;
                        }
                    }
                    return super.visitVariableDeclarations(vd, ctx);
                }

                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                    if (SET_SSL_SOCKET_FACTORY.matches(method)) {
                        hasSetSSLSocketFactory = true;
                    }
                    return super.visitMethodInvocation(method, ctx);
                }

                boolean shouldAddConnectionManager() {
                    return tlsStrategyDecl != null && hasSetSSLSocketFactory && !connectionManagerExists;
                }
            }

            MethodAnalyzer analyzer = new MethodAnalyzer();
            analyzer.visit(m.getBody(), ctx);

            if (analyzer.shouldAddConnectionManager()) {
                J.Identifier tlsStrategyIdentifier = analyzer.tlsStrategyDecl.getVariables().get(0).getName();
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
                        .apply(updateCursor(m), analyzer.tlsStrategyDecl.getCoordinates().after(), tlsStrategyIdentifier);
            }

            return m;
        }
    }

    private static class TransformSetSSLSocketFactoryVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String HTTP_CLIENT_CONNECTION_MANAGER = "org.apache.hc.client5.http.io.HttpClientConnectionManager";

        private boolean isInsideMethodWithConnectionManager(Cursor cursor) {
            // Walk up the cursor to find the enclosing method
            J.MethodDeclaration enclosingMethod = cursor.firstEnclosing(J.MethodDeclaration.class);
            if (enclosingMethod == null || enclosingMethod.getBody() == null) {
                return false;
            }

            // Check if a ConnectionManager variable exists in this method
            for (Statement stmt : enclosingMethod.getBody().getStatements()) {
                if (stmt instanceof J.VariableDeclarations) {
                    J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                    if (!vd.getVariables().isEmpty() &&
                            TypeUtils.isOfClassType(vd.getVariables().get(0).getType(), HTTP_CLIENT_CONNECTION_MANAGER)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

            // Check if this is a setSSLSocketFactory call with SSLConnectionSocketFactory as argument
            if (SET_SSL_SOCKET_FACTORY.matches(mi)) {
                if (mi.getArguments().size() == 1 && mi.getArguments().get(0) instanceof J.Identifier) {
                    J.Identifier arg = (J.Identifier) mi.getArguments().get(0);
                    // Check if the identifier type is SSLConnectionSocketFactory
                    if (arg.getType() != null &&
                            (TypeUtils.isOfClassType(arg.getType(), HTTPCLIENT_4_SSL_CONNECTION_SOCKET_FACTORY) ||
                                    TypeUtils.isOfClassType(arg.getType(), HTTPCLIENT_5_SSL_CONNECTION_SOCKET_FACTORY))) {
                        // Only transform if a ConnectionManager exists in the enclosing method
                        if (!isInsideMethodWithConnectionManager(getCursor())) {
                            return mi;
                        }
                        maybeRemoveImport(HTTPCLIENT_4_SSL_CONNECTION_SOCKET_FACTORY);
                        maybeRemoveImport(HTTPCLIENT_5_SSL_CONNECTION_SOCKET_FACTORY);
                        // Replace setSSLSocketFactory with setConnectionManager
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
