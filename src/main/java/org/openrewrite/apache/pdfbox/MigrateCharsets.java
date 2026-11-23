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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateCharsets extends Recipe {

    private static final String PDFBOX_CHARSETS = "org.apache.pdfbox.util.Charsets";
    private static final String FONTBOX_CHARSETS = "org.apache.fontbox.util.Charsets";
    private static final Map<String, String> PDFBOX_ONLY = new HashMap<>();
    private static final Map<String, String> FONTBOX_ONLY = new HashMap<>();

    static {
        PDFBOX_ONLY.put("WINDOWS_1252", "Windows-1252");
        FONTBOX_ONLY.put("ISO_10646", "ISO-10646-UCS-2");
    }

    String displayName = "Look up the PDFBox charsets that `StandardCharsets` lacks";

    String description = "PDFBox 3.0 removed the `Charsets` classes of PDFBox and FontBox, whose other charsets " +
            "`StandardCharsets` has under the same names. This looks up the two that it doesn't have, " +
            "`Charsets.WINDOWS_1252` and FontBox's `Charsets.ISO_10646`, with `Charset.forName`, as the PDFBox classes did.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesType<>(PDFBOX_CHARSETS, true), new UsesType<>(FONTBOX_CHARSETS, true)),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitImport(J.Import anImport, ExecutionContext ctx) {
                        return anImport;
                    }

                    @Override
                    public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                        J.FieldAccess f = (J.FieldAccess) super.visitFieldAccess(fieldAccess, ctx);
                        String charset = charsetName(f.getName().getFieldType());
                        return charset == null ? f : forName(f, charset, ctx);
                    }

                    @Override
                    public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                        J.Identifier i = (J.Identifier) super.visitIdentifier(identifier, ctx);
                        Object parent = getCursor().getParentTreeCursor().getValue();
                        if (parent instanceof J.FieldAccess && ((J.FieldAccess) parent).getName() == identifier) {
                            return i;
                        }
                        String charset = charsetName(i.getFieldType());
                        return charset == null ? i : forName(i, charset, ctx);
                    }

                    private @Nullable String charsetName(JavaType.@Nullable Variable field) {
                        if (field == null) {
                            return null;
                        } else if (TypeUtils.isOfClassType(field.getOwner(), PDFBOX_CHARSETS)) {
                            return PDFBOX_ONLY.get(field.getName());
                        } else if (TypeUtils.isOfClassType(field.getOwner(), FONTBOX_CHARSETS)) {
                            return FONTBOX_ONLY.get(field.getName());
                        }
                        return null;
                    }

                    private J forName(Expression replaced, String charset, ExecutionContext ctx) {
                        JavaType.Variable field = replaced instanceof J.FieldAccess ?
                                ((J.FieldAccess) replaced).getName().getFieldType() : ((J.Identifier) replaced).getFieldType();
                        if (field != null && field.getOwner() instanceof JavaType.FullyQualified) {
                            String owner = ((JavaType.FullyQualified) field.getOwner()).getFullyQualifiedName();
                            maybeRemoveImport(owner + "." + field.getName());
                            maybeRemoveImport(owner);
                        }
                        return new PdfBox3Template(getCursor())
                                .type("java.nio.charset.Charset").code(".forName(\"" + charset + "\")")
                                .apply(this, getCursor(), replaced.getCoordinates().replace(), ctx);
                    }
                });
    }
}
