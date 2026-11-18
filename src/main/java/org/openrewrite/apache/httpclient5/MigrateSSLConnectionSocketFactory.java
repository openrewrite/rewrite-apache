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

    private static final String HTTP_CLIENT_CONNECTION_MANAGER = "org.apache.hc.client5.http.io.HttpClientConnectionManager";

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
        private static final String POOLING_HTTP_CLIENT_CONNECTION_MANAGER_BUILDER = "org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder";

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block b = super.visitBlock(block, ctx);
            Object parent = getCursor().getParentTreeCursor().getValue();
            if (parent instanceof J.ClassDeclaration || parent instanceof J.NewClass) {
                return b;
            }

            J.VariableDeclarations tlsStrategyDecl = null;
            for (Statement statement : b.getStatements()) {
                if (statement instanceof J.VariableDeclarations &&
                        !((J.VariableDeclarations) statement).getVariables().isEmpty() &&
                        TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getType(), TLS_SOCKET_STRATEGY)) {
                    tlsStrategyDecl = (J.VariableDeclarations) statement;
                }
            }
            if (tlsStrategyDecl == null) {
                return b;
            }

            BlockAnalyzer analyzer = new BlockAnalyzer();
            analyzer.visit(b, ctx, getCursor().getParentOrThrow());
            if (!analyzer.hasSetSSLSocketFactory || analyzer.connectionManagerExists ||
                    hasVisibleConnectionManager(getCursor().getParentOrThrow())) {
                return b;
            }

            maybeAddImport(HTTP_CLIENT_CONNECTION_MANAGER);
            maybeAddImport(POOLING_HTTP_CLIENT_CONNECTION_MANAGER_BUILDER);
            return JavaTemplate.builder("HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()" +
                            ".setTlsSocketStrategy(#{any(org.apache.hc.client5.http.ssl.TlsSocketStrategy)}).build();")
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "httpclient5", "httpcore5"))
                    .imports(HTTP_CLIENT_CONNECTION_MANAGER, POOLING_HTTP_CLIENT_CONNECTION_MANAGER_BUILDER)
                    .build()
                    .apply(updateCursor(b), tlsStrategyDecl.getCoordinates().after(),
                            tlsStrategyDecl.getVariables().get(0).getName());
        }

        private static class BlockAnalyzer extends JavaIsoVisitor<ExecutionContext> {
            boolean connectionManagerExists;
            boolean hasSetSSLSocketFactory;

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations vd, ExecutionContext ctx) {
                if (isConnectionManagerDeclaration(vd)) {
                    connectionManagerExists = true;
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

            @Override
            public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext ctx) {
                return lambda;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                return classDecl;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                super.visitNewClass(newClass.withBody(null), ctx);
                return newClass;
            }
        }
    }

    private static class TransformSetSSLSocketFactoryVisitor extends JavaIsoVisitor<ExecutionContext> {
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
                        if (!hasVisibleConnectionManager(getCursor())) {
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

    private static boolean isConnectionManagerDeclaration(J.VariableDeclarations vd) {
        return !vd.getVariables().isEmpty() &&
                TypeUtils.isOfClassType(vd.getVariables().get(0).getType(), HTTP_CLIENT_CONNECTION_MANAGER);
    }

    private static boolean hasVisibleConnectionManager(Cursor cursor) {
        for (Cursor c = cursor; c != null; c = c.getParent()) {
            Object value = c.getValue();
            if (value instanceof J.MethodDeclaration || value instanceof J.ClassDeclaration) {
                return false;
            }
            if (value instanceof J.Block) {
                for (Statement statement : ((J.Block) value).getStatements()) {
                    if (statement instanceof J.VariableDeclarations &&
                            isConnectionManagerDeclaration((J.VariableDeclarations) statement)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
