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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@EqualsAndHashCode(callSuper = false)
@Value
public class InlineBasicAsyncRequestProducerLocals extends Recipe {

    private static final String FQN_PRODUCER = "org.apache.http.nio.protocol.BasicAsyncRequestProducer";

    private static final Set<String> INLINABLE_TYPES = new HashSet<>(Arrays.asList(
            "org.apache.http.nio.entity.NStringEntity",
            "org.apache.http.nio.entity.NByteArrayEntity",
            "org.apache.http.nio.entity.NFileEntity",
            "org.apache.http.client.methods.HttpGet",
            "org.apache.http.client.methods.HttpPost",
            "org.apache.http.client.methods.HttpPut",
            "org.apache.http.client.methods.HttpDelete"));

    String displayName = "Inline hoisted locals used by `new BasicAsyncRequestProducer(...)`";

    String description = "Normalizes hoisted entity and request locals into the inline " +
            "`new BasicAsyncRequestProducer(HttpHost.create(uri), new HttpMethod(path).setEntity(new N*Entity(...)))` " +
            "form so that `MigrateBasicAsyncRequestProducer` can rewrite the call site.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(FQN_PRODUCER, false), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                if (md.getBody() == null || !methodContainsProducer(md)) {
                    return md;
                }
                for (int guard = 0; guard < 16; guard++) {
                    Candidate candidate = findCandidate(md);
                    if (candidate == null) {
                        break;
                    }
                    md = md.withBody(rewriteBlock(md.getBody(), candidate));
                }
                return md;
            }

            private boolean methodContainsProducer(J.MethodDeclaration md) {
                Boolean[] found = {false};
                new JavaVisitor<Boolean[]>() {
                    @Override
                    public J visitNewClass(J.NewClass newClass, Boolean[] f) {
                        if (TypeUtils.isOfClassType(newClass.getType(), FQN_PRODUCER)) {
                            f[0] = true;
                        } else if (newClass.getClazz() != null) {
                            JavaType.FullyQualified fq = TypeUtils.asFullyQualified(newClass.getClazz().getType());
                            if (fq != null && FQN_PRODUCER.equals(fq.getFullyQualifiedName())) {
                                f[0] = true;
                            }
                        }
                        return super.visitNewClass(newClass, f);
                    }
                }.visit(md, found);
                return found[0];
            }

            private @Nullable Candidate findCandidate(J.MethodDeclaration md) {
                List<Statement> statements = md.getBody().getStatements();
                for (int i = 0; i < statements.size(); i++) {
                    Statement stmt = statements.get(i);
                    if (!(stmt instanceof J.VariableDeclarations)) {
                        continue;
                    }
                    J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                    if (vd.getVariables().size() != 1) {
                        continue;
                    }
                    J.VariableDeclarations.NamedVariable nv = vd.getVariables().get(0);
                    if (!(nv.getInitializer() instanceof J.NewClass)) {
                        continue;
                    }
                    JavaType.FullyQualified fq = TypeUtils.asFullyQualified(nv.getType());
                    if (fq == null || !INLINABLE_TYPES.contains(fq.getFullyQualifiedName())) {
                        continue;
                    }
                    String name = nv.getSimpleName();
                    J.MethodInvocation setEntityStmt = findSetEntityOn(statements, i, name);
                    int expectedRefs = setEntityStmt == null ? 1 : 2;
                    if (countReferences(md, name) != expectedRefs) {
                        continue;
                    }
                    return new Candidate(name, (J.NewClass) nv.getInitializer(), stmt, setEntityStmt);
                }
                return null;
            }

            private int countReferences(J.MethodDeclaration md, String name) {
                AtomicInteger count = new AtomicInteger();
                new JavaVisitor<AtomicInteger>() {
                    @Override
                    public J visitIdentifier(J.Identifier identifier, AtomicInteger c) {
                        if (name.equals(identifier.getSimpleName()) &&
                                getCursor().firstEnclosing(J.VariableDeclarations.NamedVariable.class) == null) {
                            c.incrementAndGet();
                        }
                        return super.visitIdentifier(identifier, c);
                    }
                }.visit(md, count);
                return count.get();
            }

            private J.@Nullable MethodInvocation findSetEntityOn(List<Statement> statements, int declIndex, String name) {
                if (declIndex + 1 >= statements.size()) {
                    return null;
                }
                Statement next = statements.get(declIndex + 1);
                if (!(next instanceof J.MethodInvocation)) {
                    return null;
                }
                J.MethodInvocation mi = (J.MethodInvocation) next;
                if (!"setEntity".equals(mi.getSimpleName()) || mi.getArguments().size() != 1) {
                    return null;
                }
                if (!(mi.getSelect() instanceof J.Identifier)) {
                    return null;
                }
                return name.equals(((J.Identifier) mi.getSelect()).getSimpleName()) ? mi : null;
            }

            private J.Block rewriteBlock(J.Block body, Candidate c) {
                Expression replacement;
                if (c.setEntityStmt == null) {
                    replacement = c.newClass;
                } else {
                    replacement = c.setEntityStmt.withSelect(c.newClass.withPrefix(Space.EMPTY));
                }
                return body.withStatements(ListUtils.map(body.getStatements(), s -> {
                    if (s == c.declStmt || s == c.setEntityStmt) {
                        return null;
                    }
                    return (Statement) new JavaVisitor<Integer>() {
                        @Override
                        public J visitIdentifier(J.Identifier identifier, Integer p) {
                            if (c.name.equals(identifier.getSimpleName()) &&
                                    getCursor().firstEnclosing(J.VariableDeclarations.NamedVariable.class) == null) {
                                return replacement.withPrefix(identifier.getPrefix());
                            }
                            return super.visitIdentifier(identifier, p);
                        }
                    }.visit(s, 0);
                }));
            }
        });
    }

    @Value
    private static class Candidate {
        String name;
        J.NewClass newClass;
        Statement declStmt;
        J.@Nullable MethodInvocation setEntityStmt;
    }
}
