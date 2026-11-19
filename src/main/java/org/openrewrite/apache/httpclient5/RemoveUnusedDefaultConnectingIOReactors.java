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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveUnusedDefaultConnectingIOReactors extends Recipe {

    private static final MethodMatcher NEW_REACTOR = new MethodMatcher("org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor <constructor>()");

    String displayName = "Remove unused `DefaultConnectingIOReactor` locals";

    String description = "HttpClient 5's clients and connection managers create their own I/O reactor, and HttpCore 5's " +
            "`DefaultConnectingIOReactor` has no constructor without arguments. Remove a local variable initialized with " +
            "`new DefaultConnectingIOReactor()` that nothing uses, such as one that only an HttpAsyncClient 4 " +
            "connection manager took.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(NEW_REACTOR), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                return b.withStatements(ListUtils.map(b.getStatements(), statement -> {
                    if (!isUnusedReactor(statement, b)) {
                        return statement;
                    }
                    JavaType.FullyQualified declaredType = TypeUtils.asFullyQualified(((J.VariableDeclarations) statement).getType());
                    if (declaredType != null) {
                        maybeRemoveImport(declaredType);
                    }
                    maybeRemoveImport("org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor");
                    return null;
                }));
            }
        });
    }

    private static boolean isUnusedReactor(Statement statement, J.Block block) {
        if (!(statement instanceof J.VariableDeclarations) || ((J.VariableDeclarations) statement).getVariables().size() != 1) {
            return false;
        }
        J.VariableDeclarations.NamedVariable reactor = ((J.VariableDeclarations) statement).getVariables().get(0);
        if (!(reactor.getInitializer() instanceof J.NewClass) || !NEW_REACTOR.matches((J.NewClass) reactor.getInitializer())) {
            return false;
        }
        AtomicBoolean referenced = new AtomicBoolean();
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean found) {
                if (identifier != reactor.getName() && identifier.getFieldType() != null &&
                    identifier.getSimpleName().equals(reactor.getSimpleName())) {
                    found.set(true);
                }
                return identifier;
            }
        }.visit(block, referenced);
        return !referenced.get();
    }
}
