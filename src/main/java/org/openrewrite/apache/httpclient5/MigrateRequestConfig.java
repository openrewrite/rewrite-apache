/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.apache.httpclient5;

import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MigrateRequestConfig extends Recipe {

    private static final String FQN_REQUEST_CONFIG = "org.apache.http.client.config.RequestConfig";
    private static final String FQN_REQUEST_CONFIG_BUILDER = FQN_REQUEST_CONFIG + ".Builder";
    private static final String FQN_POOL_CONN_MANAGER = "org.apache.http.impl.conn.PoolingHttpClientConnectionManager";
    private static final String FQN_HTTP_CLIENT_BUILDER = "org.apache.http.impl.client.HttpClientBuilder";

    private static final String PATTERN_STALE_CHECK_ENABLED = FQN_REQUEST_CONFIG_BUILDER + " setStaleConnectionCheckEnabled(..)";
    private static final String PATTERN_REQUEST_CONFIG = FQN_HTTP_CLIENT_BUILDER + " setDefaultRequestConfig(..)";
    private static final MethodMatcher MATCHER_STALE_CHECK_ENABLED = new MethodMatcher(PATTERN_STALE_CHECK_ENABLED, false);
    private static final MethodMatcher MATCHER_REQUEST_CONFIG = new MethodMatcher(PATTERN_REQUEST_CONFIG, false);

    private static final String KEY_REQUEST_CONFIG = "requestConfig";
    private static final String KEY_STALE_CHECK_ENABLED = "staleConnectionCheckEnabled";
    private static final String KEY_HTTP_CLIENT_BUILDER = "httpClientBuilder";
    private static final String KEY_POOL_CONN_MANAGER = "poolConnManager";

    @Override
    public String getDisplayName() {
        return "Migrate RequestConfig to httpclient5";
    }

    @Override
    public String getDescription() {
        return "Migrate RequestConfig to httpclient5.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new MigrateRequestConfigPrecondition(), new MigrateRequestConfigVisitor());
    }

    // Only check `setStaleConnectionCheckEnabled(false)` for now
    // Need another fix for `setStaleConnectionCheckEnabled(true)`
    private static class MigrateRequestConfigPrecondition extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            if (MATCHER_STALE_CHECK_ENABLED.matches(method) && !Boolean.parseBoolean(method.getArguments().get(0).print())) {
                return SearchResult.found(method);
            }
            return super.visitMethodInvocation(method, executionContext);
        }
    }

    private static class MigrateRequestConfigVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            method = super.visitMethodInvocation(method, ctx);

            if (MATCHER_STALE_CHECK_ENABLED.matches(method)) {
                boolean enabled = Boolean.parseBoolean(method.getArguments().get(0).print());
                getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, KEY_STALE_CHECK_ENABLED, enabled);
                getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, KEY_REQUEST_CONFIG, method);
                doAfterVisit(new RemoveMethodInvocationsVisitor(Collections.singletonList(PATTERN_STALE_CHECK_ENABLED)));
            } else if (MATCHER_REQUEST_CONFIG.matches(method)) {
                Set<Tree> connManagers = new HashSet<>(TreeVisitor.collect(
                  new JavaIsoVisitor<ExecutionContext>() {
                      @Override
                      public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                          if (TypeUtils.isOfClassType(multiVariable.getTypeAsFullyQualified(), FQN_POOL_CONN_MANAGER)) {
                              return SearchResult.found(multiVariable);
                          }
                          return super.visitVariableDeclarations(multiVariable, ctx);
                      }
                  },
                  getCursor().firstEnclosing(J.MethodDeclaration.class),
                  new HashSet<>()
                ));

                // Call `setConnectionManager()` if there's no PoolingHttpClientConnectionManager
                // The `poolingHttpClientConnectionManager` will be created later in `visitMethodDeclaration()`
                if (connManagers.isEmpty()) {
                    method = JavaTemplate.builder(method.print() + "\n.setConnectionManager(poolingHttpClientConnectionManager);").
                      javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                      .imports("org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager")
                      .build()
                      .apply(updateCursor(method), method.getCoordinates().replace());
                }

                getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, KEY_HTTP_CLIENT_BUILDER, method);
            }

            return method;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            // Consider only 1 RequestConfig and 0/1 ConnectionManager in a method
            if (TypeUtils.isOfClassType(multiVariable.getTypeAsFullyQualified(), FQN_POOL_CONN_MANAGER)) {
                getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, KEY_POOL_CONN_MANAGER, multiVariable);
            }
            return super.visitVariableDeclarations(multiVariable, ctx);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            method = super.visitMethodDeclaration(method, ctx);

            // setStaleConnectionCheckEnabled is only related to PoolingHttpClientConnectionManager
            boolean staleEnabled = getCursor().getMessage(KEY_STALE_CHECK_ENABLED);
            if (!staleEnabled) {
                J.VariableDeclarations varsConnManager = getCursor().getMessage(KEY_POOL_CONN_MANAGER);
                if (varsConnManager != null) {
                    J.VariableDeclarations.NamedVariable connManager = varsConnManager.getVariables().get(0);
                    method = JavaTemplate.builder("#{any(org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager)}.setValidateAfterInactivity(TimeValue.NEG_ONE_MILLISECOND);")
                      .javaParser(JavaParser.fromJavaVersion().classpath("httpclient5", "httpcore5"))
                      .imports("org.apache.hc.core5.util.TimeValue")
                      .build()
                      .apply(getCursor(), varsConnManager.getCoordinates().after(), connManager.getName());
                } else {
                    Statement httpClientBuilder = getCursor().getMessage(KEY_HTTP_CLIENT_BUILDER);
                    // Consider it's an useless RequestConfig if there's no httpClientBuilder
                    if (httpClientBuilder != null) {
                        String tpl = "PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager();" +
                          "poolingHttpClientConnectionManager.setValidateAfterInactivity(TimeValue.NEG_ONE_MILLISECOND);";
                        method = JavaTemplate.builder(tpl)
                          .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                          .imports(FQN_POOL_CONN_MANAGER)
                          .build()
                          .apply(updateCursor(method), method.getBody().getCoordinates().firstStatement());
                        maybeAddImport(FQN_POOL_CONN_MANAGER);
                    }
                }

                maybeAddImport("org.apache.hc.core5.util.TimeValue");
            }

            return method;
        }
    }
}
