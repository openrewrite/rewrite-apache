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
public class MigrateFieldWidgets extends Recipe {

    private static final String TERMINAL_FIELD = "org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField";
    private static final MethodMatcher GET_WIDGET = new MethodMatcher(TERMINAL_FIELD + " getWidget()", true);

    String displayName = "Get the first widget of a PDFBox form field from `getWidgets()`";

    String description = "PDFBox 3.0 removed `PDTerminalField.getWidget()`, which returned the first of the field's widgets, " +
            "since a field can have more than one. This calls `getWidgets().get(0)`, as `getWidget()` did.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(GET_WIDGET), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (!GET_WIDGET.matches(m) || m.getSelect() == null) {
                    return m;
                }
                return new PdfBox3Template(getCursor())
                        .target(m.getSelect(), TERMINAL_FIELD).code(".getWidgets().get(0)")
                        .apply(this, updateCursor(m), m.getCoordinates().replace(), ctx);
            }
        });
    }
}
