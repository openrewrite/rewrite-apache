/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.apache.poi;

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class ReplaceSetCellType extends Recipe {

    @Override
    public String getDisplayName() {
        return "Apache POI use `Cell.setCellType(CellType)`";
    }

    @Override
    public String getDescription() {
        return "`Cell.setCellType()` can be configured with either an integer or a the `CellType` enumeration. " +
               "It is clearer and less error-prone to use the `CellType` enumeration, so this recipe converts all `setCellType()` calls to use it.";
    }

    private static final MethodMatcher SET_CELL_TYPE = new MethodMatcher("org.apache.poi.ss.usermodel.Cell#setCellType(..)");

    private @Nullable JavaTemplate numericLiteral;
    private @Nullable JavaTemplate numericField;
    private @Nullable JavaTemplate numericStaticField;
    private @Nullable JavaTemplate stringLiteral;
    private @Nullable JavaTemplate stringField;
    private @Nullable JavaTemplate stringStaticField;
    private @Nullable JavaTemplate blankLiteral;
    private @Nullable JavaTemplate blankField;
    private @Nullable JavaTemplate blankStaticField;
    private @Nullable JavaTemplate formulaLiteral;
    private @Nullable JavaTemplate formulaField;
    private @Nullable JavaTemplate formulaStaticField;
    private @Nullable JavaTemplate booleanLiteral;
    private @Nullable JavaTemplate booleanField;
    private @Nullable JavaTemplate booleanStaticField;
    private @Nullable JavaTemplate errorLiteral;
    private @Nullable JavaTemplate errorField;
    private @Nullable JavaTemplate errorStaticField;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(SET_CELL_TYPE), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (!SET_CELL_TYPE.matches(m)) {
                    return m;
                }
                m = numeric(m, getCursor(), ctx);
                m = string(m, getCursor(), ctx);
                m = blank(m, getCursor(), ctx);
                m = formula(m, getCursor(), ctx);
                m = bool(m, getCursor(), ctx);
                m = error(m, getCursor(), ctx);
                return m;
            }

            private J.MethodInvocation numeric(J.MethodInvocation m, Cursor c, ExecutionContext ctx) {
                if (TypeUtils.isAssignableTo("org.apache.poi.ss.usermodel.CellType", m.getArguments().get(0).getType())) {
                    return m;
                }
                if (numericLiteral == null || numericField == null || numericStaticField == null) {
                    numericLiteral = JavaTemplate.builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(0);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                    numericField = JavaTemplate
                            .builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(#{cell}.CELL_TYPE_NUMERIC);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                    numericStaticField = JavaTemplate
                            .builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(org.apache.poi.ss.usermodel.Cell.CELL_TYPE_NUMERIC);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                }
                JavaTemplate.Matcher matcher;
                if ((matcher = numericLiteral.matcher(c)).find() ||
                    (matcher = numericField.matcher(c)).find() ||
                    (matcher = numericStaticField.matcher(c)).find()) {
                    maybeAddImport("org.apache.poi.ss.usermodel.CellType");
                    m = JavaTemplate.builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(CellType.NUMERIC);")
                            .imports("org.apache.poi.ss.usermodel.CellType")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build()
                            .apply(c, m.getCoordinates().replace(), matcher.parameter(0));
                    setCursor(new Cursor(c.getParent(), m));
                }
                return m;
            }

            private J.MethodInvocation string(J.MethodInvocation m, Cursor c, ExecutionContext ctx) {
                if (TypeUtils.isAssignableTo("org.apache.poi.ss.usermodel.CellType", m.getArguments().get(0).getType())) {
                    return m;
                }
                if (stringLiteral == null || stringField == null || stringStaticField == null) {
                    stringLiteral = JavaTemplate.builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(1);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                    stringField = JavaTemplate.builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(#{cell}.CELL_TYPE_STRING);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                    stringStaticField = JavaTemplate
                            .builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(org.apache.poi.ss.usermodel.Cell.CELL_TYPE_STRING);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                }
                JavaTemplate.Matcher matcher;
                if ((matcher = stringLiteral.matcher(c)).find() ||
                    (matcher = stringField.matcher(c)).find() ||
                    (matcher = stringStaticField.matcher(c)).find()) {
                    maybeAddImport("org.apache.poi.ss.usermodel.CellType");
                    m = JavaTemplate.builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(CellType.STRING);")
                            .imports("org.apache.poi.ss.usermodel.CellType")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build()
                            .apply(c, m.getCoordinates().replace(), matcher.parameter(0));
                    setCursor(new Cursor(c.getParent(), m));
                }
                return m;
            }

            private J.MethodInvocation formula(J.MethodInvocation m, Cursor c, ExecutionContext ctx) {
                if (TypeUtils.isAssignableTo("org.apache.poi.ss.usermodel.CellType", m.getArguments().get(0).getType())) {
                    return m;
                }
                if (formulaLiteral == null || formulaField == null || formulaStaticField == null) {
                    formulaLiteral = JavaTemplate.builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(2);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                    formulaField = JavaTemplate
                            .builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(#{cell}.CELL_TYPE_FORMULA);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                    formulaStaticField = JavaTemplate
                            .builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(org.apache.poi.ss.usermodel.Cell.CELL_TYPE_FORMULA);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                }
                JavaTemplate.Matcher matcher;
                if ((matcher = formulaLiteral.matcher(c)).find() ||
                    (matcher = formulaField.matcher(c)).find() ||
                    (matcher = formulaStaticField.matcher(c)).find()) {
                    maybeAddImport("org.apache.poi.ss.usermodel.CellType");
                    m = JavaTemplate.builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(CellType.FORMULA);")
                            .imports("org.apache.poi.ss.usermodel.CellType")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build()
                            .apply(c, m.getCoordinates().replace(), matcher.parameter(0));
                    setCursor(new Cursor(c.getParent(), m));
                }
                return m;
            }

            private J.MethodInvocation blank(J.MethodInvocation m, Cursor c, ExecutionContext ctx) {
                if (TypeUtils.isAssignableTo("org.apache.poi.ss.usermodel.CellType", m.getArguments().get(0).getType())) {
                    return m;
                }
                if (blankLiteral == null || blankField == null || blankStaticField == null) {
                    blankLiteral = JavaTemplate.builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(3);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                    blankField = JavaTemplate
                            .builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(#{cell}.CELL_TYPE_BLANK);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                    blankStaticField = JavaTemplate
                            .builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(org.apache.poi.ss.usermodel.Cell.CELL_TYPE_BLANK);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                }
                JavaTemplate.Matcher matcher;
                if ((matcher = blankLiteral.matcher(c)).find() ||
                    (matcher = blankField.matcher(c)).find() ||
                    (matcher = blankStaticField.matcher(c)).find()) {
                    maybeAddImport("org.apache.poi.ss.usermodel.CellType");
                    m = JavaTemplate.builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(CellType.BLANK);")
                            .imports("org.apache.poi.ss.usermodel.CellType")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build()
                            .apply(c, m.getCoordinates().replace(), matcher.parameter(0));
                    setCursor(new Cursor(c.getParent(), m));
                }
                return m;
            }

            private J.MethodInvocation bool(J.MethodInvocation m, Cursor c, ExecutionContext ctx) {
                if (TypeUtils.isAssignableTo("org.apache.poi.ss.usermodel.CellType", m.getArguments().get(0).getType())) {
                    return m;
                }
                if (booleanLiteral == null || booleanField == null || booleanStaticField == null) {
                    booleanLiteral = JavaTemplate.builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(4);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                    booleanField = JavaTemplate
                            .builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(#{cell}.CELL_TYPE_BOOLEAN);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                    booleanStaticField = JavaTemplate
                            .builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(org.apache.poi.ss.usermodel.Cell.CELL_TYPE_BOOLEAN);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                }
                JavaTemplate.Matcher matcher;
                if ((matcher = booleanLiteral.matcher(c)).find() ||
                    (matcher = booleanField.matcher(c)).find() ||
                    (matcher = booleanStaticField.matcher(c)).find()) {
                    maybeAddImport("org.apache.poi.ss.usermodel.CellType");
                    m = JavaTemplate.builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(CellType.BOOLEAN);")
                            .imports("org.apache.poi.ss.usermodel.CellType")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build()
                            .apply(c, m.getCoordinates().replace(), matcher.parameter(0));
                    setCursor(new Cursor(c.getParent(), m));
                }
                return m;
            }

            private J.MethodInvocation error(J.MethodInvocation m, Cursor c, ExecutionContext ctx) {
                if (TypeUtils.isAssignableTo("org.apache.poi.ss.usermodel.CellType", m.getArguments().get(0).getType())) {
                    return m;
                }
                if (errorLiteral == null || errorField == null || errorStaticField == null) {
                    errorLiteral = JavaTemplate.builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(5);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                    errorField = JavaTemplate
                            .builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(#{cell}.CELL_TYPE_ERROR);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                    errorStaticField = JavaTemplate
                            .builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(org.apache.poi.ss.usermodel.Cell.CELL_TYPE_ERROR);")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build();
                }
                JavaTemplate.Matcher matcher;
                if ((matcher = errorLiteral.matcher(c)).find() ||
                    (matcher = errorField.matcher(c)).find() ||
                    (matcher = errorStaticField.matcher(c)).find()) {
                    maybeAddImport("org.apache.poi.ss.usermodel.CellType");
                    m = JavaTemplate.builder("#{cell:any(org.apache.poi.ss.usermodel.Cell)}.setCellType(CellType.ERROR);")
                            .imports("org.apache.poi.ss.usermodel.CellType")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build()
                            .apply(c, m.getCoordinates().replace(), matcher.parameter(0));
                    setCursor(new Cursor(c.getParent(), m));
                }
                return m;
            }
        });
    }
}
