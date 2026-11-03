/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Arrays;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveSettingsIgnoredWithConnectionManager extends Recipe {

    private static final String BUILDER = "org.apache.http.impl.client.HttpClientBuilder";
    private static final MethodMatcher SET_CONNECTION_MANAGER = new MethodMatcher(BUILDER + " setConnectionManager(..)");
    // HttpClient 4 applies these only to the connection manager it creates when none was set; HttpClient 5's builder lacks them
    private static final List<MethodMatcher> IGNORED_WITH_CONNECTION_MANAGER = Arrays.asList(
            new MethodMatcher(BUILDER + " setDefaultSocketConfig(..)"),
            new MethodMatcher(BUILDER + " setDefaultConnectionConfig(..)"),
            new MethodMatcher(BUILDER + " setMaxConnTotal(..)"),
            new MethodMatcher(BUILDER + " setMaxConnPerRoute(..)"),
            new MethodMatcher(BUILDER + " setConnectionTimeToLive(..)"),
            new MethodMatcher(BUILDER + " setDnsResolver(..)"));

    String displayName = "Remove `HttpClientBuilder` connection settings ignored next to `setConnectionManager`";

    String description = "HttpClient 4's `HttpClientBuilder` applies `setDefaultSocketConfig`, `setDefaultConnectionConfig`, " +
            "`setMaxConnTotal`, `setMaxConnPerRoute`, `setConnectionTimeToLive` and `setDnsResolver` only to the connection " +
            "manager it creates itself, so they have no effect when the same builder chain also calls `setConnectionManager`. " +
            "HttpClient 5's `HttpClientBuilder` no longer has these methods; remove them from such chains.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(SET_CONNECTION_MANAGER), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (m.getSelect() != null &&
                        IGNORED_WITH_CONNECTION_MANAGER.stream().anyMatch(matcher -> matcher.matches(m)) &&
                        chainSetsConnectionManager(m)) {
                    return m.getSelect();
                }
                return m;
            }

            private boolean chainSetsConnectionManager(J.MethodInvocation m) {
                // Calls made before this one in the chain
                for (Expression select = m.getSelect(); select instanceof J.MethodInvocation;
                     select = ((J.MethodInvocation) select).getSelect()) {
                    if (setsConnectionManager((J.MethodInvocation) select)) {
                        return true;
                    }
                }
                // Calls made after this one in the chain, on the builder this one returns
                J current = getCursor().getValue();
                for (Cursor parent = getCursor().getParentTreeCursor();
                     parent.getValue() instanceof J.MethodInvocation &&
                             ((J.MethodInvocation) parent.getValue()).getSelect() == current;
                     parent = parent.getParentTreeCursor()) {
                    J.MethodInvocation next = parent.getValue();
                    if (setsConnectionManager(next)) {
                        return true;
                    }
                    current = next;
                }
                return false;
            }

            private boolean setsConnectionManager(J.MethodInvocation m) {
                return SET_CONNECTION_MANAGER.matches(m) && !J.Literal.isLiteralValue(m.getArguments().get(0), null);
            }
        });
    }
}
