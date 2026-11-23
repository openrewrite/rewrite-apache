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
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigratePageContentStream extends Recipe {

    private static final String CONTENT_STREAM = "org.apache.pdfbox.pdmodel.PDPageContentStream";
    private static final String MATRIX = "org.apache.pdfbox.util.Matrix";
    private static final String PATH_ITERATOR = "java.awt.geom.PathIterator";
    private static final String IMAGE_XOBJECT = "org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject";

    private static final Map<String, String> RENAMED = new HashMap<>();

    static {
        RENAMED.put("drawString", "showText");
        RENAMED.put("moveTextPositionByAmount", "newLineAtOffset");
        RENAMED.put("addBezier31", "curveTo1");
        RENAMED.put("addBezier32", "curveTo2");
        RENAMED.put("addBezier312", "curveTo");
        RENAMED.put("closeSubPath", "closePath");
        RENAMED.put("endMarkedContentSequence", "endMarkedContent");
        RENAMED.put("drawInlineImage", "drawImage");
    }

    private static final MethodMatcher RENAMED_METHODS = new MethodMatcher(CONTENT_STREAM +
            " *(..)");
    private static final MethodMatcher BEGIN_MARKED_CONTENT_SEQUENCE = new MethodMatcher(CONTENT_STREAM + " beginMarkedContentSequence(org.apache.pdfbox.cos.COSName)");
    private static final MethodMatcher DOUBLE_TO_FLOAT = new MethodMatcher(CONTENT_STREAM + " set*(double, ..)");
    private static final MethodMatcher SET_COLOR_INT = new MethodMatcher(CONTENT_STREAM + " set*Color(int, ..)");
    private static final MethodMatcher CONCATENATE_DOUBLES = new MethodMatcher(CONTENT_STREAM + " concatenate2CTM(double, double, double, double, double, double)");
    private static final MethodMatcher CONCATENATE_AFFINE = new MethodMatcher(CONTENT_STREAM + " concatenate2CTM(java.awt.geom.AffineTransform)");
    private static final MethodMatcher SET_TEXT_MATRIX_DOUBLES = new MethodMatcher(CONTENT_STREAM + " setTextMatrix(double, double, double, double, double, double)");
    private static final MethodMatcher SET_TEXT_MATRIX_AFFINE = new MethodMatcher(CONTENT_STREAM + " setTextMatrix(java.awt.geom.AffineTransform)");
    private static final MethodMatcher SET_TEXT_TRANSLATION = new MethodMatcher(CONTENT_STREAM + " setTextTranslation(double, double)");
    private static final MethodMatcher SET_TEXT_ROTATION = new MethodMatcher(CONTENT_STREAM + " setTextRotation(double, double, double)");
    private static final MethodMatcher SET_TEXT_SCALING = new MethodMatcher(CONTENT_STREAM + " setTextScaling(double, double, double, double)");
    private static final MethodMatcher FILL_WINDING_RULE = new MethodMatcher(CONTENT_STREAM + " fill(int)");
    private static final MethodMatcher CLIP_PATH_WINDING_RULE = new MethodMatcher(CONTENT_STREAM + " clipPath(int)");
    private static final MethodMatcher DRAW_XOBJECT_RECTANGLE = new MethodMatcher(CONTENT_STREAM + " drawXObject(org.apache.pdfbox.pdmodel.graphics.PDXObject, float, float, float, float)");
    private static final MethodMatcher DRAW_XOBJECT_AFFINE = new MethodMatcher(CONTENT_STREAM + " drawXObject(org.apache.pdfbox.pdmodel.graphics.PDXObject, java.awt.geom.AffineTransform)");
    private static final MethodMatcher DRAW_LINE = new MethodMatcher(CONTENT_STREAM + " drawLine(float, float, float, float)");
    private static final MethodMatcher ADD_LINE = new MethodMatcher(CONTENT_STREAM + " addLine(float, float, float, float)");
    private static final MethodMatcher FILL_RECT = new MethodMatcher(CONTENT_STREAM + " fillRect(float, float, float, float)");
    private static final MethodMatcher NEW_WITH_BOOLEAN_APPEND = new MethodMatcher(CONTENT_STREAM +
            " <constructor>(org.apache.pdfbox.pdmodel.PDDocument, org.apache.pdfbox.pdmodel.PDPage, boolean, boolean, ..)");

    String displayName = "Replace the `PDPageContentStream` methods PDFBox 3.0 removed";

    String description = "PDFBox 3.0 removed the `PDPageContentStream` methods and constructors that PDFBox 2 deprecated. " +
            "This calls their replacements as the PDFBox 2 methods did: `showText` for `drawString`, `newLineAtOffset` for " +
            "`moveTextPositionByAmount`, `setTextMatrix(Matrix)` and `transform(Matrix)` for the text and transformation " +
            "methods that took numbers, `float` arguments where they took `double`, the `fill`, `fillEvenOdd`, `clip` and " +
            "`clipEvenOdd` methods for a winding rule, and an `AppendMode` for the boolean that said whether to append. " +
            "Color components in 0..255 become the fractions of 255 that the `float` methods take, since the `int` overloads " +
            "PDFBox 3.0 removed would otherwise be called with the `float` overloads, which reject values above 1. " +
            "`drawLine`, `addLine` and `fillRect` become the `moveTo`, `lineTo`, `addRect`, `stroke` and `fill` calls they made.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(CONTENT_STREAM, false), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = (J.NewClass) super.visitNewClass(newClass, ctx);
                JavaType.Method constructor = n.getConstructorType();
                if (!NEW_WITH_BOOLEAN_APPEND.matches(n) || constructor == null) {
                    return n;
                }
                List<Expression> args = n.getArguments();
                List<JavaType> parameterTypes = constructor.getParameterTypes();
                PdfBox3Template template = new PdfBox3Template(getCursor()).code("new ").type(CONTENT_STREAM).code("(");
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        template.code(", ");
                    }
                    if (i == 2) {
                        appendMode(template, args.get(i));
                    } else {
                        template.expression(args.get(i), parameterTypes.get(i));
                    }
                }
                return template.code(")").apply(this, updateCursor(n), n.getCoordinates().replace(), ctx);
            }

            private void appendMode(PdfBox3Template template, Expression append) {
                Expression e = append.unwrap();
                if (e instanceof J.Literal && Boolean.TRUE.equals(((J.Literal) e).getValue())) {
                    template.type(CONTENT_STREAM).code(".AppendMode.APPEND");
                } else if (e instanceof J.Literal && Boolean.FALSE.equals(((J.Literal) e).getValue())) {
                    template.type(CONTENT_STREAM).code(".AppendMode.OVERWRITE");
                } else {
                    template.expression(append, "boolean").code(" ? ").type(CONTENT_STREAM).code(".AppendMode.APPEND : ")
                            .type(CONTENT_STREAM).code(".AppendMode.OVERWRITE");
                }
            }

            @Override
            public J visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = (J.Block) super.visitBlock(block, ctx);
                // Expand one call at a time, as each expansion changes the statements of the block
                for (J.MethodInvocation m = nextDrawnPath(b); m != null; m = nextDrawnPath(b)) {
                    String code = drawnPath(m);
                    assert code != null;
                    PdfBox3Template template = new PdfBox3Template(getCursor());
                    List<Expression> args = m.getArguments();
                    for (String part : code.split("%")) {
                        if (part.startsWith("s")) {
                            template.expression(m.getSelect(), CONTENT_STREAM).code(part.substring(1));
                        } else if (!part.isEmpty() && Character.isDigit(part.charAt(0))) {
                            template.expression(args.get(part.charAt(0) - '0'), "float").code(part.substring(1));
                        } else {
                            template.code(part);
                        }
                    }
                    J.Block expanded = template.apply(this, new Cursor(getCursor().getParentOrThrow(), b), m.getCoordinates().replace(), ctx);
                    if (expanded == b || nextDrawnPath(expanded) == m) {
                        break;
                    }
                    b = expanded;
                }
                return b;
            }

            private J.@Nullable MethodInvocation nextDrawnPath(J.Block block) {
                for (Statement statement : block.getStatements()) {
                    if (statement instanceof J.MethodInvocation && drawnPath((J.MethodInvocation) statement) != null) {
                        return (J.MethodInvocation) statement;
                    }
                }
                return null;
            }

            /**
             * The calls that replace a PDFBox 2 path method called as a statement, with %s for the content stream and
             * %0 to %3 for its arguments, each used once, or null when the call isn't one of them or the content stream
             * expression can't be repeated.
             */
            private @Nullable String drawnPath(J.MethodInvocation m) {
                if (!(m.getSelect() instanceof J.Identifier || m.getSelect() instanceof J.FieldAccess &&
                                                             ((J.FieldAccess) m.getSelect()).getTarget() instanceof J.Identifier)) {
                    return null;
                }
                if (DRAW_LINE.matches(m)) {
                    return "%s.moveTo(%0, %1);\n%s.lineTo(%2, %3);\n%s.stroke();";
                } else if (ADD_LINE.matches(m)) {
                    return "%s.moveTo(%0, %1);\n%s.lineTo(%2, %3);";
                } else if (FILL_RECT.matches(m)) {
                    return "%s.addRect(%0, %1, %2, %3);\n%s.fill();";
                }
                return null;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                Expression select = m.getSelect();
                if (select == null || methodType == null || !TypeUtils.isOfClassType(methodType.getDeclaringType(), CONTENT_STREAM)) {
                    return m;
                }
                List<Expression> args = m.getArguments();
                List<JavaType> parameterTypes = methodType.getParameterTypes();
                PdfBox3Template template = new PdfBox3Template(getCursor()).target(select, CONTENT_STREAM);
                String name = m.getSimpleName();
                if (RENAMED.containsKey(name) && RENAMED_METHODS.matches(m) ||
                    BEGIN_MARKED_CONTENT_SEQUENCE.matches(m)) {
                    template.code("." + (RENAMED.containsKey(name) ? RENAMED.get(name) : "beginMarkedContent") + "(");
                    // A call without arguments has one J.Empty argument
                    for (int i = 0; i < parameterTypes.size(); i++) {
                        template.code(i == 0 ? "" : ", ").expression(args.get(i), parameterTypes.get(i));
                    }
                    template.code(")");
                } else if (CONCATENATE_DOUBLES.matches(m) || SET_TEXT_MATRIX_DOUBLES.matches(m)) {
                    template.code(CONCATENATE_DOUBLES.matches(m) ? ".transform(new " : ".setTextMatrix(new ").type(MATRIX).code("(");
                    for (int i = 0; i < args.size(); i++) {
                        template.code(i == 0 ? "" : ", ").asFloat(args.get(i));
                    }
                    template.code("))");
                } else if (CONCATENATE_AFFINE.matches(m) || SET_TEXT_MATRIX_AFFINE.matches(m)) {
                    template.code(CONCATENATE_AFFINE.matches(m) ? ".transform(new " : ".setTextMatrix(new ").type(MATRIX)
                            .code("(").expression(args.get(0), "java.awt.geom.AffineTransform").code("))");
                } else if (SET_TEXT_TRANSLATION.matches(m)) {
                    template.code(".setTextMatrix(").type(MATRIX).code(".getTranslateInstance(")
                            .asFloat(args.get(0)).code(", ").asFloat(args.get(1)).code("))");
                } else if (SET_TEXT_ROTATION.matches(m)) {
                    template.code(".setTextMatrix(").type(MATRIX).code(".getRotateInstance(")
                            .expression(args.get(0), "double").code(", ").asFloat(args.get(1)).code(", ")
                            .asFloat(args.get(2)).code("))");
                } else if (SET_TEXT_SCALING.matches(m)) {
                    template.code(".setTextMatrix(new ").type(MATRIX).code("(").asFloat(args.get(0)).code(", 0f, 0f, ")
                            .asFloat(args.get(1)).code(", ").asFloat(args.get(2)).code(", ").asFloat(args.get(3)).code("))");
                } else if (DOUBLE_TO_FLOAT.matches(m) && allDoubles(parameterTypes) &&
                           (name.equals("setLeading") || name.endsWith("Color"))) {
                    template.code("." + name + "(");
                    for (int i = 0; i < args.size(); i++) {
                        template.code(i == 0 ? "" : ", ").asFloat(args.get(i));
                    }
                    template.code(")");
                } else if (SET_COLOR_INT.matches(m) && allInts(parameterTypes)) {
                    template.code("." + name + "(");
                    for (int i = 0; i < args.size(); i++) {
                        template.code(i == 0 ? "" : ", ").per255(args.get(i));
                    }
                    template.code(")");
                } else if (FILL_WINDING_RULE.matches(m) || CLIP_PATH_WINDING_RULE.matches(m)) {
                    Integer windingRule = windingRule(args.get(0));
                    if (windingRule == null) {
                        return m;
                    }
                    boolean evenOdd = windingRule == 0;
                    template.code(FILL_WINDING_RULE.matches(m) ? evenOdd ? ".fillEvenOdd()" : ".fill()" :
                            evenOdd ? ".clipEvenOdd()" : ".clip()");
                    maybeRemoveImport(PATH_ITERATOR);
                } else if ((DRAW_XOBJECT_RECTANGLE.matches(m) || DRAW_XOBJECT_AFFINE.matches(m)) &&
                           TypeUtils.isAssignableTo(IMAGE_XOBJECT, args.get(0).getType())) {
                    template.code(".drawImage(").expression(args.get(0), IMAGE_XOBJECT);
                    if (DRAW_XOBJECT_RECTANGLE.matches(m)) {
                        for (int i = 1; i < args.size(); i++) {
                            template.code(", ").expression(args.get(i), parameterTypes.get(i));
                        }
                        template.code(")");
                    } else {
                        template.code(", new ").type(MATRIX).code("(")
                                .expression(args.get(1), "java.awt.geom.AffineTransform").code("))");
                    }
                } else {
                    return m;
                }
                return template.apply(this, updateCursor(m), m.getCoordinates().replace(), ctx);
            }

            private boolean allDoubles(List<JavaType> types) {
                return types.stream().allMatch(t -> t == JavaType.Primitive.Double);
            }

            private boolean allInts(List<JavaType> types) {
                return types.stream().allMatch(t -> t == JavaType.Primitive.Int);
            }

            /**
             * The {@code PathIterator} winding rule a constant argument stands for: 0 for even-odd, 1 for non-zero.
             */
            private @Nullable Integer windingRule(Expression arg) {
                Expression e = arg.unwrap();
                if (e instanceof J.Literal && ((J.Literal) e).getValue() instanceof Integer) {
                    int value = (Integer) ((J.Literal) e).getValue();
                    return value == 0 || value == 1 ? value : null;
                }
                JavaType.Variable field = e instanceof J.FieldAccess ? ((J.FieldAccess) e).getName().getFieldType() :
                        e instanceof J.Identifier ? ((J.Identifier) e).getFieldType() : null;
                if (field != null && TypeUtils.isOfClassType(field.getOwner(), PATH_ITERATOR)) {
                    if ("WIND_EVEN_ODD".equals(field.getName())) {
                        return 0;
                    } else if ("WIND_NON_ZERO".equals(field.getName())) {
                        return 1;
                    }
                }
                return null;
            }
        });
    }
}
