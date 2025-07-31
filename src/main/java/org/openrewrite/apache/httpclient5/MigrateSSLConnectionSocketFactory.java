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
                        new UsesType<>("org.apache.http.conn.ssl.SSLConnectionSocketFactory", false),
                        new UsesType<>("org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory", false)
                ),
                new MigrateSSLConnectionSocketFactoryVisitor()
        );
    }

    private static class MigrateSSLConnectionSocketFactoryVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final MethodMatcher SET_SSL_SOCKET_FACTORY = new MethodMatcher(
                "org.apache..*..HttpClientBuilder setSSLSocketFactory(..)"
        );

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            // Check if this method contains the SSLConnectionSocketFactory pattern
            if (m.getBody() != null) {
                J.VariableDeclarations tlsStrategyDecl = null;
                boolean needsConnectionManager = false;

                // First pass: check if we have the pattern and find the transformed declaration
                for (Statement stmt : m.getBody().getStatements()) {
                    if (stmt instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                        if (!vd.getVariables().isEmpty()) {
                            String varName = vd.getVariables().get(0).getSimpleName();
                            if ("tlsSocketStrategy".equals(varName)) {
                                tlsStrategyDecl = vd;
                            }
                        }
                    } else if (stmt instanceof J.MethodInvocation) {
                        J.MethodInvocation mi = (J.MethodInvocation) stmt;
                        // Check if there's a setConnectionManager call that references 'cm'
                        if (mi.toString().contains("setConnectionManager(cm)")) {
                            needsConnectionManager = true;
                        }
                    } else if (stmt instanceof J.Return) {
                        J.Return ret = (J.Return) stmt;
                        if (ret.getExpression() != null && ret.getExpression().toString().contains("setConnectionManager(cm)")) {
                            needsConnectionManager = true;
                        }
                    }
                }

                // If we found a tlsSocketStrategy declaration and need a connection manager, add it
                if (tlsStrategyDecl != null && needsConnectionManager) {

                    maybeAddImport("org.apache.hc.client5.http.ssl.TlsSocketStrategy");
                    maybeAddImport("org.apache.hc.client5.http.io.HttpClientConnectionManager");
                    maybeAddImport("org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder");
                    return JavaTemplate.builder(
                            "HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()" +
                            ".setTlsSocketStrategy(tlsSocketStrategy).build();")
                            .contextSensitive()
                            .imports("org.apache.hc.client5.http.io.HttpClientConnectionManager",
                                    "org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder")
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "httpclient5", "httpcore5"))
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
            if ((TypeUtils.isOfClassType(vd.getType(), "org.apache.http.conn.ssl.SSLConnectionSocketFactory") ||
                    TypeUtils.isOfClassType(vd.getType(), "org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory")) &&
                    !vd.getVariables().isEmpty() &&
                    vd.getVariables().get(0).getInitializer() instanceof J.NewClass) {

                J.NewClass newClass = requireNonNull((J.NewClass) vd.getVariables().get(0).getInitializer());
                String variableName = vd.getVariables().get(0).getSimpleName();

                // Only transform if this is actually SSLConnectionSocketFactory
                if (variableName.equals("sslConnectionSocketFactory")) {

                    // Replace SSLConnectionSocketFactory with TlsSocketStrategy
                    String replacement;
                    boolean hasArgument = !newClass.getArguments().isEmpty() && !(newClass.getArguments().get(0) instanceof J.Empty);
                    if (hasArgument) {
                        replacement = "TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(#{any(javax.net.ssl.SSLContext)})";
                    } else {
                        replacement = "TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy()";
                    }

                    maybeRemoveImport("org.apache.http.conn.ssl.SSLConnectionSocketFactory");
                    maybeRemoveImport("org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory");
                    maybeAddImport("org.apache.hc.client5.http.ssl.TlsSocketStrategy");
                    maybeAddImport("org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy");
                    return JavaTemplate.builder(replacement)
                            .contextSensitive()
                            .imports("org.apache.hc.client5.http.ssl.TlsSocketStrategy")
                            .imports("org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy")
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "httpclient5", "httpcore5"))
                            .build()
                            .apply(getCursor(), vd.getCoordinates().replace(),
                                    hasArgument ? new Object[]{newClass.getArguments().get(0)} : new Object[0]);
                }
            }
            return vd;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

            if (SET_SSL_SOCKET_FACTORY.matches(mi)) {
                // Check if the argument is sslConnectionSocketFactory
                if (mi.getArguments().size() == 1) {
                    if (mi.getArguments().get(0) instanceof J.Identifier) {
                        J.Identifier arg = (J.Identifier) mi.getArguments().get(0);
                        if ("sslConnectionSocketFactory".equals(arg.getSimpleName())) {
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
            }

            return mi;
        }
    }
}
