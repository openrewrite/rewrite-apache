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
package org.openrewrite.codehaus.plexus;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesField;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.List;

public class ReplacePlexusSecurityRoleConstants extends Recipe {

    private static final List<String> TYPES_WITH_ROLE = Arrays.asList(
            "org.sonatype.plexus.components.cipher.PlexusCipher",
            "org.sonatype.plexus.components.sec.dispatcher.SecDispatcher",
            "org.sonatype.plexus.components.sec.dispatcher.PasswordDecryptor");

    @Getter
    final String displayName = "Replace the `ROLE` constants of the Plexus cipher and security dispatcher";

    @Getter
    final String description = "`PlexusCipher.ROLE`, which plexus-cipher 1.7 removed, and `SecDispatcher.ROLE` and " +
            "`PasswordDecryptor.ROLE`, which `org.codehaus.plexus:plexus-sec-dispatcher` 2.0 removed, held the name of " +
            "their interface. This replaces them with `getName()` of the interface's class.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> usesRole = Preconditions.or(TYPES_WITH_ROLE.stream()
                .map(type -> new UsesField<ExecutionContext>(type, "ROLE"))
                .toArray(TreeVisitor[]::new));
        return Preconditions.check(usesRole, new JavaVisitor<ExecutionContext>() {

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof J.CompilationUnit;
            }

            @Override
            public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                String type = roleOf(fieldAccess.getName().getFieldType());
                if (type == null) {
                    return super.visitFieldAccess(fieldAccess, ctx);
                }
                return className(type, fieldAccess);
            }

            @Override
            public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                String type = roleOf(identifier.getFieldType());
                if (type == null || getCursor().getParentTreeCursor().getValue() instanceof J.FieldAccess ||
                    getCursor().firstEnclosing(J.Import.class) != null) {
                    return identifier;
                }
                // a static import or an implementation of the interface names the constant alone
                maybeRemoveImport(type + ".ROLE");
                return className(type, identifier);
            }

            private J className(String type, Expression replaced) {
                maybeAddImport(type);
                return JavaTemplate.builder(type.substring(type.lastIndexOf('.') + 1) + ".class.getName()")
                        .imports(type)
                        .javaParser(JavaParser.fromJavaVersion().dependsOn(PlexusSecurityStubs.CODEHAUS_2_0))
                        .build()
                        .apply(getCursor(), replaced.getCoordinates().replace());
            }
        });
    }

    private static @Nullable String roleOf(JavaType.@Nullable Variable fieldType) {
        if (fieldType == null || !"ROLE".equals(fieldType.getName())) {
            return null;
        }
        for (String type : TYPES_WITH_ROLE) {
            if (TypeUtils.isOfClassType(fieldType.getOwner(), type)) {
                return type;
            }
        }
        return null;
    }
}
