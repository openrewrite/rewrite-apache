/*
 * Copyright 2024 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.trait.MethodAccess;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

public class MigrateRequestConfig extends Recipe {

    private static final String FQN_REQUEST_CONFIG = "org.apache.http.client.config.RequestConfig";
    private static final String FQN_REQUEST_CONFIG_BUILDER = FQN_REQUEST_CONFIG + ".Builder";
    private static final String FQN_POOL_CONN_MANAGER4 = "org.apache.http.impl.conn.PoolingHttpClientConnectionManager";
    private static final String FQN_POOL_CONN_MANAGER5 = "org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager";
    private static final String FQN_HTTP_CLIENT_BUILDER = "org.apache.http.impl.client.HttpClientBuilder";
    private static final String FQN_TIME_VALUE = "org.apache.hc.core5.util.TimeValue";

    private static final String PATTERN_STALE_CHECK_ENABLED = FQN_REQUEST_CONFIG_BUILDER + " setStaleConnectionCheckEnabled(..)";
    private static final String PATTERN_REQUEST_CONFIG = FQN_HTTP_CLIENT_BUILDER + " setDefaultRequestConfig(..)";
    private static final MethodMatcher MATCHER_STALE_CHECK_ENABLED = new MethodMatcher(PATTERN_STALE_CHECK_ENABLED, false);
    private static final MethodMatcher MATCHER_REQUEST_CONFIG = new MethodMatcher(PATTERN_REQUEST_CONFIG, false);

    private static final String KEY_POOL_CONN_MANAGER = "poolConnManager";

    @Override
    public String getDisplayName() {
        return "Migrate `RequestConfig` to httpclient5";
    }

    @Override
    public String getDescription() {
        return "Migrate `RequestConfig` to httpclient5.";
    }

    private static TreeVisitor<? extends Tree, ExecutionContext> callsSetStaleCheckEnabledFalse() {
        return new MethodAccess.Matcher(MATCHER_STALE_CHECK_ENABLED)
                .asVisitor(access ->
                        J.Literal.isLiteralValue(access.getTree().getArguments().get(0), false) ?
                                SearchResult.found(access.getTree()) : access.getTree());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        // Cheap check first, to avoid more expensive search
                        new UsesMethod<>(MATCHER_STALE_CHECK_ENABLED),
                        callsSetStaleCheckEnabledFalse()
                ), new MigrateRequestConfigVisitor());
    }

    private static class MigrateRequestConfigVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            // setStaleConnectionCheckEnabled is only related to PoolingHttpClientConnectionManager
            boolean staleEnabled = callsSetStaleCheckEnabledFalse().visitNonNull(method, ctx, getCursor().getParentOrThrow()) != method;
            if (staleEnabled) {
                // Find or create a new PoolingHttpClientConnectionManager
                J.VariableDeclarations connectionManagerVD = findExistingConnectionPool(method);
                boolean needsNewConnectionManager = connectionManagerVD == null;
                if (needsNewConnectionManager) {
                    maybeAddImport(FQN_POOL_CONN_MANAGER5);
                    method = JavaTemplate.builder(
                                    "PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = " +
                                    "new PoolingHttpClientConnectionManager();")
                            .javaParser(JavaParser.fromJavaVersion().classpath("httpclient5", "httpcore5"))
                            .imports(FQN_POOL_CONN_MANAGER5)
                            .build()
                            .apply(getCursor(), method.getBody().getCoordinates().firstStatement());
                    connectionManagerVD = (J.VariableDeclarations) method.getBody().getStatements().get(0);
                }

                // Set `setValidateAfterInactivity(TimeValue.NEG_ONE_MILLISECOND)`
                J.Identifier connectionManagerIdentifier = connectionManagerVD.getVariables().get(0).getName();
                maybeAddImport(FQN_TIME_VALUE);
                method = JavaTemplate.builder("#{any(" + FQN_POOL_CONN_MANAGER5 + ")}.setValidateAfterInactivity(TimeValue.NEG_ONE_MILLISECOND);")
                        .javaParser(JavaParser.fromJavaVersion().classpath("httpclient5", "httpcore5"))
                        .imports(FQN_TIME_VALUE)
                        .build()
                        .apply(updateCursor(method), connectionManagerVD.getCoordinates().after(), connectionManagerIdentifier);

                // Make the connection manager available to set in the method invocation visit below
                if (needsNewConnectionManager) {
                    getCursor().putMessage(KEY_POOL_CONN_MANAGER, connectionManagerIdentifier);
                }
            }
            return super.visitMethodDeclaration(method, ctx);
        }

        private J.@Nullable VariableDeclarations findExistingConnectionPool(J.MethodDeclaration method) {
            AtomicReference<J.VariableDeclarations> existingConnManager = new AtomicReference<>();
            new JavaIsoVisitor<AtomicReference<J.VariableDeclarations>>() {
                @Override
                public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, AtomicReference<J.VariableDeclarations> ref) {
                    J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ref);
                    if (TypeUtils.isOfClassType(vd.getTypeAsFullyQualified(), FQN_POOL_CONN_MANAGER4)) {
                        ref.set(vd);
                    }
                    return vd;
                }
            }.visitNonNull(method, existingConnManager, getCursor().getParentOrThrow());
            return existingConnManager.get();
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (MATCHER_STALE_CHECK_ENABLED.matches(method)) {
                doAfterVisit(new RemoveMethodInvocationsVisitor(Collections.singletonList(PATTERN_STALE_CHECK_ENABLED)));
            } else if (MATCHER_REQUEST_CONFIG.matches(method)) {
                J.Identifier connectionManagerIdentifier = getCursor().pollNearestMessage(KEY_POOL_CONN_MANAGER);
                if (connectionManagerIdentifier != null) {
                    method = JavaTemplate.builder("#{any()}.setConnectionManager(#{any()});")
                            .javaParser(JavaParser.fromJavaVersion().classpath("httpclient5", "httpcore5"))
                            .imports(FQN_POOL_CONN_MANAGER5)
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), method, connectionManagerIdentifier);
                }
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }
}
