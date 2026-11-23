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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateGlyphCallbacks extends Recipe {

    private static final String STREAM_ENGINE = "org.apache.pdfbox.contentstream.PDFStreamEngine";
    private static final String MATRIX = "org.apache.pdfbox.util.Matrix";
    private static final String VECTOR = "org.apache.pdfbox.util.Vector";
    private static final String PD_FONT = "org.apache.pdfbox.pdmodel.font.PDFont";
    private static final List<MethodMatcher> WITH_UNICODE = Arrays.asList(
            new MethodMatcher(STREAM_ENGINE + " showGlyph(" + MATRIX + ", " + PD_FONT + ", int, String, " + VECTOR + ")", true),
            new MethodMatcher(STREAM_ENGINE + " showFontGlyph(" + MATRIX + ", " + PD_FONT + ", int, String, " + VECTOR + ")", true),
            new MethodMatcher(STREAM_ENGINE + " showType3Glyph(" + MATRIX + ", org.apache.pdfbox.pdmodel.font.PDType3Font, int, String, " + VECTOR + ")", true));

    String displayName = "Drop the Unicode parameter of PDFBox glyph callbacks";

    String description = "PDFBox 3.0 removed the `showGlyph`, `showFontGlyph` and `showType3Glyph` methods of `PDFStreamEngine` " +
            "that took the Unicode text of the glyph, which PDFBox 2 deprecated, and calls the ones without it. " +
            "This removes the `String` parameter from the methods that override them, so that PDFBox calls them again, " +
            "and gets the text with `font.toUnicode(code)`, as PDFBox 2 did, where the method still uses it. " +
            "It also drops the text from the calls of these methods.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(VECTOR, false), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                JavaType.Method type = md.getMethodType();
                if (type == null || !withUnicode(type) || md.getParameters().size() != 5 ||
                    !(md.getParameters().get(3) instanceof J.VariableDeclarations)) {
                    return md;
                }
                J.VariableDeclarations.NamedVariable unicode = ((J.VariableDeclarations) md.getParameters().get(3)).getVariables().get(0);
                md = md.getPadding().withParameters(md.getPadding().getParameters().getPadding().withElements(
                        ListUtils.map(md.getPadding().getParameters().getPadding().getElements(), (i, p) -> i == 3 ? null : p)));
                JavaType.Method withoutUnicode = withoutFourth(type);
                md = md.withMethodType(withoutUnicode).withName(md.getName().withType(withoutUnicode));
                if (md.getBody() == null || !references(md.getBody(), unicode.getVariableType())) {
                    return md;
                }
                J.Identifier font = ((J.VariableDeclarations) md.getParameters().get(1)).getVariables().get(0).getName();
                J.Identifier code = ((J.VariableDeclarations) md.getParameters().get(2)).getVariables().get(0).getName();
                return new PdfBox3Template(getCursor())
                        .code("String " + unicode.getSimpleName() + " = ").target(font, PD_FONT)
                        .code(".toUnicode(").expression(code, "int").code(");")
                        .apply(this, updateCursor(md), md.getBody().getCoordinates().firstStatement(), ctx);
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                JavaType.Method type = m.getMethodType();
                if (type == null || !withUnicode(type) || m.getArguments().size() != 5) {
                    return m;
                }
                return m.getPadding().withArguments(m.getPadding().getArguments().getPadding().withElements(
                                ListUtils.map(m.getPadding().getArguments().getPadding().getElements(), (i, a) -> i == 3 ? null : a)))
                        .withMethodType(withoutFourth(type));
            }

            private boolean withUnicode(JavaType.Method type) {
                for (MethodMatcher matcher : WITH_UNICODE) {
                    if (matcher.matches(type)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private static JavaType.Method withoutFourth(JavaType.Method type) {
        return type
                .withParameterNames(ListUtils.map(type.getParameterNames(), (i, n) -> i == 3 ? null : n))
                .withParameterTypes(ListUtils.map(type.getParameterTypes(), (i, t) -> i == 3 ? null : t));
    }

    private static boolean references(J tree, JavaType.@Nullable Variable variable) {
        AtomicBoolean found = new AtomicBoolean();
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean f) {
                if (variable != null && variable.equals(identifier.getFieldType())) {
                    f.set(true);
                }
                return identifier;
            }
        }.visit(tree, found);
        return found.get();
    }
}
