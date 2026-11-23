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
package org.openrewrite.apache.pdfbox;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateOperatorProcessors extends Recipe {

    private static final String OPERATOR_PACKAGE = "org.apache.pdfbox.contentstream.operator";
    private static final String STREAM_ENGINE = "org.apache.pdfbox.contentstream.PDFStreamEngine";
    private static final String GRAPHICS_STREAM_ENGINE = "org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine";
    private static final MethodMatcher NEW_OPERATOR = new MethodMatcher(OPERATOR_PACKAGE + "..* <constructor>()");

    String displayName = "Pass the stream engine to PDFBox operator processors";

    String description = "PDFBox 3.0 operator processors such as `Concatenate` take the `PDFStreamEngine` they belong to in their " +
            "constructor, where PDFBox 2 set it when the operator was added. In a `PDFStreamEngine` subclass this passes `this` " +
            "to the operators it creates.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(NEW_OPERATOR),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass n = (J.NewClass) super.visitNewClass(newClass, ctx);
                        JavaType.Method constructor = n.getConstructorType();
                        if (!NEW_OPERATOR.matches(n) || constructor == null || n.getBody() != null ||
                            !TypeUtils.isAssignableTo(OPERATOR_PACKAGE + ".OperatorProcessor", constructor.getDeclaringType())) {
                            return n;
                        }
                        String operator = constructor.getDeclaringType().getFullyQualifiedName();
                        String engine = operator.startsWith(OPERATOR_PACKAGE + ".graphics.") ? GRAPHICS_STREAM_ENGINE : STREAM_ENGINE;
                        JavaType.FullyQualified enclosing = enclosingEngine(getCursor(), engine);
                        if (enclosing == null) {
                            return n;
                        }
                        J.Identifier self = new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "this", enclosing, null);
                        return new PdfBox3Template(getCursor())
                                .code("new ").type(operator).code("(").expression(self, engine).code(")")
                                .apply(this, updateCursor(n), n.getCoordinates().replace(), ctx);
                    }

                    /**
                     * The class whose {@code this} the code at the cursor can pass as the engine, when it is one.
                     */
                    private JavaType.@Nullable FullyQualified enclosingEngine(Cursor cursor, String engine) {
                        Object previous = cursor.getValue();
                        for (Cursor c = cursor.getParentTreeCursor(); !c.isRoot(); c = c.getParentTreeCursor()) {
                            Object value = c.getValue();
                            if (value instanceof J.NewClass && ((J.NewClass) value).getBody() == previous) {
                                return null;
                            } else if (value instanceof J.MethodDeclaration &&
                                       ((J.MethodDeclaration) value).hasModifier(J.Modifier.Type.Static)) {
                                return null;
                            } else if (value instanceof J.VariableDeclarations &&
                                       ((J.VariableDeclarations) value).hasModifier(J.Modifier.Type.Static)) {
                                return null;
                            } else if (value instanceof J.Block && ((J.Block) value).isStatic()) {
                                return null;
                            } else if (value instanceof J.ClassDeclaration) {
                                JavaType.FullyQualified type = ((J.ClassDeclaration) value).getType();
                                return TypeUtils.isAssignableTo(engine, type) ? type : null;
                            }
                            previous = value;
                        }
                        return null;
                    }
                });
    }
}
