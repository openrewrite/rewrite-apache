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
import org.openrewrite.java.tree.J;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateQuickSort extends Recipe {

    private static final String QUICK_SORT = "org.apache.pdfbox.util.QuickSort";
    private static final MethodMatcher SORT_NATURAL = new MethodMatcher(QUICK_SORT + " sort(java.util.List)");
    private static final MethodMatcher SORT = new MethodMatcher(QUICK_SORT + " sort(java.util.List, java.util.Comparator)");

    String displayName = "Sort with PDFBox `IterativeMergeSort` instead of `QuickSort`";

    String description = "PDFBox 2.0.25 replaced `org.apache.pdfbox.util.QuickSort` with `IterativeMergeSort`, and PDFBox 3.0 has no " +
            "`QuickSort`. This sorts with `IterativeMergeSort`, in natural order where `QuickSort` was given no comparator. " +
            "`IterativeMergeSort` is stable, so elements that the comparator finds equal keep the order they were in, " +
            "where `QuickSort` could swap them.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(SORT_NATURAL), new UsesMethod<>(SORT)), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                boolean natural = SORT_NATURAL.matches(m);
                if (!natural && !SORT.matches(m)) {
                    return m;
                }
                PdfBox3Template template = new PdfBox3Template(getCursor())
                        .type("org.apache.pdfbox.util.IterativeMergeSort").code(".sort(")
                        .expression(m.getArguments().get(0), "java.util.List").code(", ");
                if (natural) {
                    template.type("java.util.Comparator").code(".naturalOrder()");
                } else {
                    template.expression(m.getArguments().get(1), "java.util.Comparator");
                }
                maybeRemoveImport(QUICK_SORT);
                return template.code(")").apply(this, updateCursor(m), m.getCoordinates().replace(), ctx);
            }
        });
    }
}
