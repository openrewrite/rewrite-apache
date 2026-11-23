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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

import static org.openrewrite.apache.pdfbox.PdfBox3Template.MEMORY_USAGE_SETTING;
import static org.openrewrite.apache.pdfbox.PdfBox3Template.RANDOM_ACCESS_READ_BUFFER;
import static org.openrewrite.apache.pdfbox.PdfBox3Template.isNullLiteral;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateDocumentLoading extends Recipe {

    private static final MethodMatcher PD_DOCUMENT_LOAD = new MethodMatcher("org.apache.pdfbox.pdmodel.PDDocument load(..)");
    private static final MethodMatcher FDF_DOCUMENT_LOAD = new MethodMatcher("org.apache.pdfbox.pdmodel.fdf.FDFDocument load(..)");
    private static final MethodMatcher FDF_DOCUMENT_LOAD_XFDF = new MethodMatcher("org.apache.pdfbox.pdmodel.fdf.FDFDocument loadXFDF(..)");

    String displayName = "Load PDF and FDF documents with `Loader`";

    String description = "PDFBox 3.0 removed the static `load` methods of `PDDocument` and `FDFDocument`. " +
            "This replaces each with the `Loader.loadPDF`, `Loader.loadFDF` or `Loader.loadXFDF` method that takes the same arguments. " +
            "A PDF read from an `InputStream` is read into a `RandomAccessReadBuffer`, as `Loader` takes no stream, " +
            "and a `MemoryUsageSetting` argument becomes the stream cache function that creates the same scratch file.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(PD_DOCUMENT_LOAD),
                        new UsesMethod<>(FDF_DOCUMENT_LOAD),
                        new UsesMethod<>(FDF_DOCUMENT_LOAD_XFDF)),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                        String loaderMethod;
                        if (PD_DOCUMENT_LOAD.matches(m)) {
                            loaderMethod = "loadPDF";
                        } else if (FDF_DOCUMENT_LOAD.matches(m)) {
                            loaderMethod = "loadFDF";
                        } else if (FDF_DOCUMENT_LOAD_XFDF.matches(m)) {
                            loaderMethod = "loadXFDF";
                        } else {
                            return m;
                        }
                        JavaType.Method methodType = m.getMethodType();
                        if (methodType == null) {
                            return m;
                        }
                        List<JavaType> parameterTypes = methodType.getParameterTypes();
                        List<Expression> arguments = m.getArguments();
                        if (parameterTypes.size() != arguments.size()) {
                            return m;
                        }

                        PdfBox3Template template = new PdfBox3Template(getCursor())
                                .type("org.apache.pdfbox.Loader")
                                .code("." + loaderMethod + "(");
                        boolean first = true;
                        for (int i = 0; i < arguments.size(); i++) {
                            Expression argument = arguments.get(i);
                            JavaType parameterType = parameterTypes.get(i);
                            boolean memoryUsageSetting = TypeUtils.isOfClassType(parameterType, MEMORY_USAGE_SETTING);
                            if (memoryUsageSetting && isNullLiteral(argument)) {
                                // Without a stream cache function, Loader uses main memory, as PDFBox 2 did without a setting
                                continue;
                            }
                            if (!first) {
                                template.code(", ");
                            }
                            first = false;
                            if (memoryUsageSetting) {
                                template.streamCache(argument);
                            } else if (i == 0 && "loadPDF".equals(loaderMethod) &&
                                       TypeUtils.isOfClassType(parameterType, "java.io.InputStream")) {
                                template.code("new ").type(RANDOM_ACCESS_READ_BUFFER).code("(")
                                        .expression(argument, parameterType).code(")");
                            } else {
                                template.expression(argument, parameterType);
                            }
                        }
                        template.code(")");

                        maybeRemoveImport("org.apache.pdfbox.pdmodel.PDDocument");
                        maybeRemoveImport("org.apache.pdfbox.pdmodel.fdf.FDFDocument");
                        maybeRemoveImport(MEMORY_USAGE_SETTING);
                        return template.apply(this, updateCursor(m), m.getCoordinates().replace(), ctx);
                    }
                });
    }
}
