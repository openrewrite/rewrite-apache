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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateStandard14Fonts extends Recipe {

    private static final String PD_TYPE1_FONT = "org.apache.pdfbox.pdmodel.font.PDType1Font";
    private static final String STANDARD_14_FONTS = "org.apache.pdfbox.pdmodel.font.Standard14Fonts";
    private static final Set<String> STANDARD_14_FONT_NAMES = new HashSet<>(Arrays.asList(
            "TIMES_ROMAN", "TIMES_BOLD", "TIMES_ITALIC", "TIMES_BOLD_ITALIC",
            "HELVETICA", "HELVETICA_BOLD", "HELVETICA_OBLIQUE", "HELVETICA_BOLD_OBLIQUE",
            "COURIER", "COURIER_BOLD", "COURIER_OBLIQUE", "COURIER_BOLD_OBLIQUE",
            "SYMBOL", "ZAPF_DINGBATS"));
    private static final String PD_FONT = "org.apache.pdfbox.pdmodel.font.PDFont";
    private static final MethodMatcher CREATE_DEFAULT_FONT = new MethodMatcher("org.apache.pdfbox.pdmodel.font.PDFontFactory createDefaultFont()");
    private static final List<MethodMatcher> EQUALITY_ASSERTIONS = Arrays.asList(
            new MethodMatcher("org.junit.jupiter.api.Assertions assertEquals(Object, Object, ..)"),
            new MethodMatcher("org.junit.jupiter.api.Assertions assertNotEquals(Object, Object, ..)"),
            new MethodMatcher("org.junit.Assert assertEquals(.., Object, Object)"),
            new MethodMatcher("org.junit.Assert assertNotEquals(.., Object, Object)"),
            new MethodMatcher("junit.framework.Assert assertEquals(.., Object, Object)", true),
            new MethodMatcher("org.testng.Assert assertEquals(Object, Object, ..)"),
            new MethodMatcher("org.testng.Assert assertNotEquals(Object, Object, ..)"));

    String displayName = "Create the standard 14 fonts with `Standard14Fonts.FontName`";

    String description = "PDFBox 3.0 removed the static `PDType1Font` instances of the standard 14 fonts, such as `PDType1Font.HELVETICA`, " +
            "because their shared, mutable dictionaries caused problems. This creates the font where each was used, " +
            "with `new PDType1Font(Standard14Fonts.FontName.HELVETICA)`, and does the same for `PDFontFactory.createDefaultFont()`, " +
            "which PDFBox 3.0 also removed. The instances are no longer shared, so a comparison by identity with one of them no longer holds. " +
            "Where a JUnit or TestNG assertion compares a font with one of them for equality, it compares their names, " +
            "as `assertEquals(Standard14Fonts.FontName.HELVETICA.getName(), font.getName())`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(new UsesType<>(PD_TYPE1_FONT, false), new UsesMethod<>(CREATE_DEFAULT_FONT)),
                new JavaVisitor<ExecutionContext>() {
                    /**
                     * The font name of each font this created, by the id of the expression that creates it.
                     */
                    private final Map<UUID, String> created = new HashMap<>();

                    @Override
                    public J visitImport(J.Import anImport, ExecutionContext ctx) {
                        return anImport;
                    }

                    @Override
                    public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                        J.FieldAccess f = (J.FieldAccess) super.visitFieldAccess(fieldAccess, ctx);
                        String fontName = standard14FontName(f.getName().getFieldType());
                        return fontName == null ? f : newFont(f, fontName, ctx);
                    }

                    @Override
                    public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                        J.Identifier i = (J.Identifier) super.visitIdentifier(identifier, ctx);
                        Object parent = getCursor().getParentTreeCursor().getValue();
                        if (parent instanceof J.FieldAccess && ((J.FieldAccess) parent).getName() == identifier) {
                            return i;
                        }
                        String fontName = standard14FontName(i.getFieldType());
                        return fontName == null ? i : newFont(i, fontName, ctx);
                    }

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                        if (CREATE_DEFAULT_FONT.matches(m)) {
                            maybeRemoveImport("org.apache.pdfbox.pdmodel.font.PDFontFactory");
                            return newFont(m, "HELVETICA", ctx);
                        }
                        for (MethodMatcher assertion : EQUALITY_ASSERTIONS) {
                            if (assertion.matches(m)) {
                                return compareNames(m, ctx);
                            }
                        }
                        return m;
                    }

                    /**
                     * A font created where a shared instance was used is equal to no other font, so an assertion that
                     * a font equals one of them compares the names of the fonts instead.
                     */
                    private J.MethodInvocation compareNames(J.MethodInvocation m, ExecutionContext ctx) {
                        List<Expression> args = m.getArguments();
                        if (args.stream().noneMatch(arg -> created.containsKey(arg.getId())) ||
                            args.stream().noneMatch(arg -> !created.containsKey(arg.getId()) &&
                                                           TypeUtils.isAssignableTo(PD_FONT, arg.getType()))) {
                            return m;
                        }
                        Cursor cursor = updateCursor(m);
                        return m.withArguments(ListUtils.map(args, arg -> {
                            String fontName = created.get(arg.getId());
                            PdfBox3Template template = new PdfBox3Template(cursor);
                            if (fontName != null) {
                                template.type(STANDARD_14_FONTS).code(".FontName." + fontName + ".getName()");
                            } else if (TypeUtils.isAssignableTo(PD_FONT, arg.getType())) {
                                template.target(arg, PD_FONT).code(".getName()");
                            } else {
                                return arg;
                            }
                            return template.apply(this, new Cursor(cursor, arg), arg.getCoordinates().replace(), ctx);
                        }));
                    }

                    private @Nullable String standard14FontName(JavaType.@Nullable Variable field) {
                        if (field != null && STANDARD_14_FONT_NAMES.contains(field.getName()) &&
                            TypeUtils.isOfClassType(field.getOwner(), PD_TYPE1_FONT)) {
                            return field.getName();
                        }
                        return null;
                    }

                    private J newFont(Expression replaced, String fontName, ExecutionContext ctx) {
                        maybeRemoveImport(PD_TYPE1_FONT + "." + fontName);
                        maybeRemoveImport(PD_TYPE1_FONT + ".*");
                        J font = new PdfBox3Template(getCursor())
                                .code("new ").type(PD_TYPE1_FONT).code("(").type(STANDARD_14_FONTS)
                                .code(".FontName." + fontName + ")")
                                .apply(this, getCursor(), replaced.getCoordinates().replace(), ctx);
                        created.put(font.getId(), fontName);
                        return font;
                    }
                });
    }
}
