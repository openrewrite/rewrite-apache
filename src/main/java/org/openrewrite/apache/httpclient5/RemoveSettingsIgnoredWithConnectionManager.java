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
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.staticanalysis.RemoveUnusedLocalVariables;
import org.openrewrite.trait.Comments;

import java.util.*;

import static java.util.Collections.emptySet;

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
    // Ignored the same way, but likely meant to take effect, so their removal is flagged and their arguments are kept
    private static final List<MethodMatcher> TLS_IGNORED_WITH_CONNECTION_MANAGER = Arrays.asList(
            new MethodMatcher(BUILDER + " setSSLContext(..)"),
            new MethodMatcher(BUILDER + " setSslcontext(..)"),
            new MethodMatcher(BUILDER + " setSSLSocketFactory(..)"),
            new MethodMatcher(BUILDER + " setSSLHostnameVerifier(..)"),
            new MethodMatcher(BUILDER + " setHostnameVerifier(..)"));
    private static final String TLS_SETTINGS_REMOVED = "TLS_SETTINGS_REMOVED";
    private static final String TLS_ARGUMENTS = "TLS_ARGUMENTS";

    String displayName = "Remove `HttpClientBuilder` connection settings ignored next to `setConnectionManager`";

    String description = "HttpClient 4's `HttpClientBuilder` applies `setDefaultSocketConfig`, `setDefaultConnectionConfig`, " +
            "`setMaxConnTotal`, `setMaxConnPerRoute`, `setConnectionTimeToLive` and `setDnsResolver` only to the connection " +
            "manager it creates itself, so they have no effect when the same builder chain also calls `setConnectionManager`. " +
            "HttpClient 5's `HttpClientBuilder` no longer has these methods; remove them from such chains, " +
            "along with local variables that only held their arguments. The TLS settings `setSSLContext`, " +
            "`setSSLSocketFactory`, `setSSLHostnameVerifier` and `setHostnameVerifier` are ignored the same way; they " +
            "are removed too, with a TODO comment, and the local variables they used are kept.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(SET_CONNECTION_MANAGER), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J md = super.visitMethodDeclaration(method, ctx);
                if (md != method) {
                    Set<String> tlsArguments = getCursor().getMessage(TLS_ARGUMENTS, emptySet());
                    // The dropped arguments are configuration values, typically built by side effect free builder chains
                    return (J) new RemoveUnusedLocalVariables(tlsArguments.toArray(new String[0]), null, true).getVisitor()
                            .visitNonNull(md, ctx, getCursor().getParentOrThrow());
                }
                return md;
            }

            @Override
            public J visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = (J.Block) super.visitBlock(block, ctx);
                Map<UUID, Set<String>> removed = getCursor().getMessage(TLS_SETTINGS_REMOVED);
                if (removed == null) {
                    return b;
                }
                return b.withStatements(ListUtils.map(b.getStatements(), statement -> {
                    Set<String> names = removed.get(statement.getId());
                    return names == null ? statement : Comments.of(new Cursor(getCursor(), statement)).<Statement>comment(
                            " TODO: HttpClient 4 ignored " + String.join(", ", names) + " next to setConnectionManager; " +
                            "configure TLS on that connection manager if it should apply");
                }));
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                boolean tls = TLS_IGNORED_WITH_CONNECTION_MANAGER.stream().anyMatch(matcher -> matcher.matches(m));
                if (m.getSelect() == null ||
                        !(tls || IGNORED_WITH_CONNECTION_MANAGER.stream().anyMatch(matcher -> matcher.matches(m))) ||
                        !chainSetsConnectionManager(m)) {
                    return m;
                }
                if (tls) {
                    flagRemovedTlsSetting(m);
                } else {
                    m.getArguments().forEach(argument -> maybeRemoveImport(TypeUtils.asFullyQualified(argument.getType())));
                }
                return m.getSelect();
            }

            private void flagRemovedTlsSetting(J.MethodInvocation m) {
                for (Cursor statement = getCursor(); !(statement.getValue() instanceof SourceFile);
                     statement = statement.getParentTreeCursor()) {
                    Cursor block = statement.getParentTreeCursor();
                    if (block.getValue() instanceof J.Block) {
                        block.<Map<UUID, Set<String>>>computeMessageIfAbsent(TLS_SETTINGS_REMOVED, k -> new HashMap<>())
                                .computeIfAbsent(((J) statement.getValue()).getId(), k -> new LinkedHashSet<>())
                                .add(m.getSimpleName());
                        break;
                    }
                }
                Cursor methodDeclaration = getCursor().dropParentUntil(c -> c instanceof J.MethodDeclaration || c == Cursor.ROOT_VALUE);
                if (methodDeclaration.getValue() instanceof J.MethodDeclaration) {
                    Set<String> tlsArguments = methodDeclaration.computeMessageIfAbsent(TLS_ARGUMENTS, k -> new HashSet<>());
                    new JavaIsoVisitor<Set<String>>() {
                        @Override
                        public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> names) {
                            names.add(identifier.getSimpleName());
                            return identifier;
                        }
                    }.visit(m.getArguments().get(0), tlsArguments);
                }
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
