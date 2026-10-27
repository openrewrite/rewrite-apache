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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateStringEntityContentEncoding extends Recipe {

    private static final String STRING_ENTITY = "org.apache.hc.core5.http.io.entity.StringEntity";
    private static final MethodMatcher CONSTRUCTOR = new MethodMatcher(
            STRING_ENTITY + " <constructor>(java.lang.String, org.apache.hc.core5.http.ContentType)");
    private static final MethodMatcher SET_CONTENT_ENCODING = new MethodMatcher(
            "org.apache.hc.core5.http.io.entity.AbstractHttpEntity setContentEncoding(java.lang.String)");

    String displayName = "Move `StringEntity` content encoding into its constructor";

    String description = "After migrating HttpClient 4 namespaces to HttpClient 5, move an adjacent literal " +
            "content-encoding setter on a new local `StringEntity` into its four-argument constructor. " +
            "Retain unsupported or commented pairs for manual migration.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(STRING_ENTITY, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                if (getCursor().firstEnclosing(J.CompilationUnit.class) == null) {
                    return b;
                }
                List<Statement> statements = b.getStatements();
                List<Statement> updated = new ArrayList<>(statements.size());
                Cursor blockCursor = updateCursor(b);
                boolean changed = false;
                for (int i = 0; i < statements.size(); i++) {
                    Statement statement = statements.get(i);
                    if (!(statement instanceof J.VariableDeclarations) || i + 1 >= statements.size() ||
                            !(statements.get(i + 1) instanceof J.MethodInvocation)) {
                        updated.add(statement);
                        continue;
                    }
                    J.VariableDeclarations declarations = (J.VariableDeclarations) statement;
                    if (declarations.getVariables().size() != 1) {
                        updated.add(statement);
                        continue;
                    }
                    J.VariableDeclarations.NamedVariable variable = declarations.getVariables().get(0);
                    JavaType.Variable symbol = variable.getVariableType();
                    J.MethodInvocation setter = (J.MethodInvocation) statements.get(i + 1);
                    if (symbol == null || !(symbol.getOwner() instanceof JavaType.Method) ||
                            !TypeUtils.isOfClassType(variable.getType(), STRING_ENTITY) ||
                            !(variable.getInitializer() instanceof J.NewClass) ||
                            !(setter.getSelect() instanceof J.Identifier) ||
                            !symbol.equals(((J.Identifier) setter.getSelect()).getFieldType()) ||
                            !SET_CONTENT_ENCODING.matches(setter) || setter.getArguments().size() != 1) {
                        updated.add(statement);
                        continue;
                    }
                    J.NewClass construction = (J.NewClass) variable.getInitializer();
                    Expression encoding = setter.getArguments().get(0);
                    if (construction.getBody() != null || construction.getEnclosing() != null ||
                            !TypeUtils.isOfClassType(construction.getType(), STRING_ENTITY) ||
                            !CONSTRUCTOR.matches(construction) || construction.getArguments().size() != 2 ||
                            !isLiteralEncoding(encoding) || hasComments(declarations) || hasComments(setter) ||
                            !b.getPadding().getStatements().get(i).getAfter().getComments().isEmpty() ||
                            !b.getPadding().getStatements().get(i + 1).getAfter().getComments().isEmpty()) {
                        updated.add(statement);
                        continue;
                    }

                    // Class mapping has already relocated these expressions. Parse the new overload against
                    // the real 5.x API, then preserve the original constructor type spelling and arguments.
                    Cursor constructionCursor = new Cursor(new Cursor(new Cursor(blockCursor, declarations), variable), construction);
                    J.NewClass replacement = JavaTemplate.builder(
                                    "new org.apache.hc.core5.http.io.entity.StringEntity(" +
                                    "#{any(java.lang.String)}, #{any(org.apache.hc.core5.http.ContentType)}, " +
                                    "#{any(java.lang.String)}, false)")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpcore5"))
                            .build()
                            .apply(constructionCursor, construction.getCoordinates().replace(),
                                    construction.getArguments().get(0), construction.getArguments().get(1), encoding);
                    replacement = replacement.withPrefix(construction.getPrefix()).withClazz(construction.getClazz());
                    updated.add(declarations.withVariables(Collections.singletonList(variable.withInitializer(replacement))));
                    i++;
                    changed = true;
                }
                return changed ? b.withStatements(updated) : b;
            }

            private boolean isLiteralEncoding(Expression expression) {
                if (expression instanceof J.Literal) {
                    Object value = ((J.Literal) expression).getValue();
                    return value == null || value instanceof String;
                }
                // The 4.x String/Header overloads make a bare null ambiguous in valid original Java.
                if (expression instanceof J.TypeCast && TypeUtils.isOfClassType(expression.getType(), "java.lang.String")) {
                    Expression cast = ((J.TypeCast) expression).getExpression();
                    return cast instanceof J.Literal && ((J.Literal) cast).getValue() == null;
                }
                return false;
            }

            private boolean hasComments(J tree) {
                return new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public Space visitSpace(Space space, Space.Location loc, AtomicBoolean found) {
                        if (!space.getComments().isEmpty()) {
                            found.set(true);
                        }
                        return space;
                    }
                }.reduce(tree, new AtomicBoolean()).get();
            }
        });
    }
}
