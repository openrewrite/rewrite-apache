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

import static org.openrewrite.apache.pdfbox.PdfBox3Template.IO_UTILS;
import static org.openrewrite.apache.pdfbox.PdfBox3Template.MEMORY_USAGE_SETTING;
import static org.openrewrite.apache.pdfbox.PdfBox3Template.isNullLiteral;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateMemoryUsageSettings extends Recipe {

    private static final MethodMatcher NEW_PD_DOCUMENT = new MethodMatcher("org.apache.pdfbox.pdmodel.PDDocument <constructor>(" + MEMORY_USAGE_SETTING + ")");
    private static final MethodMatcher MERGE_DOCUMENTS = new MethodMatcher("org.apache.pdfbox.multipdf.PDFMergerUtility mergeDocuments(" + MEMORY_USAGE_SETTING + ")");
    private static final MethodMatcher MERGE_DOCUMENTS_DEFAULT = new MethodMatcher("org.apache.pdfbox.multipdf.PDFMergerUtility mergeDocuments()");
    private static final MethodMatcher SET_MEMORY_USAGE_SETTING = new MethodMatcher("org.apache.pdfbox.multipdf.Splitter setMemoryUsageSetting(" + MEMORY_USAGE_SETTING + ")");

    String displayName = "Replace `MemoryUsageSetting` arguments with stream cache functions";

    String description = "PDFBox 3.0 takes a `StreamCacheCreateFunction` where PDFBox 2 took a `MemoryUsageSetting`: in the " +
            "`PDDocument` constructor, in `PDFMergerUtility.mergeDocuments`, and in `Splitter.setStreamCacheCreateFunction`, " +
            "which replaces `Splitter.setMemoryUsageSetting`. " +
            "This passes the setting's `streamCache`, which creates the same scratch file PDFBox 2 created from the setting, " +
            "or the `IOUtils` factory of a main memory only or temporary file only cache. " +
            "`PDFMergerUtility.mergeDocuments()`, which PDFBox 3.0 removed, merges in main memory as it did.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(NEW_PD_DOCUMENT),
                        new UsesMethod<>(MERGE_DOCUMENTS),
                        new UsesMethod<>(MERGE_DOCUMENTS_DEFAULT),
                        new UsesMethod<>(SET_MEMORY_USAGE_SETTING)),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass n = (J.NewClass) super.visitNewClass(newClass, ctx);
                        if (!NEW_PD_DOCUMENT.matches(n)) {
                            return n;
                        }
                        Expression setting = n.getArguments().get(0);
                        PdfBox3Template template = new PdfBox3Template(getCursor()).code("new ").type("org.apache.pdfbox.pdmodel.PDDocument").code("(");
                        if (!isNullLiteral(setting)) {
                            template.streamCache(setting);
                        }
                        maybeRemoveImport(MEMORY_USAGE_SETTING);
                        return template.code(")").apply(this, updateCursor(n), n.getCoordinates().replace(), ctx);
                    }

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                        if (m.getSelect() == null) {
                            return m;
                        }
                        PdfBox3Template template;
                        if (MERGE_DOCUMENTS_DEFAULT.matches(m)) {
                            template = new PdfBox3Template(getCursor())
                                    .target(m.getSelect(), "org.apache.pdfbox.multipdf.PDFMergerUtility")
                                    .code(".mergeDocuments(").type(IO_UTILS).code(".createMemoryOnlyStreamCache())");
                        } else if (MERGE_DOCUMENTS.matches(m)) {
                            template = new PdfBox3Template(getCursor())
                                    .target(m.getSelect(), "org.apache.pdfbox.multipdf.PDFMergerUtility")
                                    .code(".mergeDocuments(");
                            if (isNullLiteral(m.getArguments().get(0))) {
                                // Null still merges in main memory
                                template.code("null");
                            } else {
                                template.streamCache(m.getArguments().get(0));
                            }
                            template.code(")");
                        } else if (SET_MEMORY_USAGE_SETTING.matches(m)) {
                            template = new PdfBox3Template(getCursor())
                                    .target(m.getSelect(), "org.apache.pdfbox.multipdf.Splitter")
                                    .code(".setStreamCacheCreateFunction(");
                            if (isNullLiteral(m.getArguments().get(0))) {
                                template.code("null");
                            } else {
                                template.streamCache(m.getArguments().get(0));
                            }
                            template.code(")");
                        } else {
                            return m;
                        }
                        maybeRemoveImport(MEMORY_USAGE_SETTING);
                        return template.apply(this, updateCursor(m), m.getCoordinates().replace(), ctx);
                    }
                });
    }
}
