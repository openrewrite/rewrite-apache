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
package org.openrewrite.apache.commons.lang;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class FindCommonsLangEnums extends Recipe {

    private static final String ENUMS_PACKAGE = "org.apache.commons.lang.enums";
    private static final String MESSAGE = "Commons Lang 3 has no `enums` package; replace with a Java `enum`";

    @Getter
    final String displayName = "Find Commons Lang 2 `enums` types";

    @Getter
    final String description = "Commons Lang 3 dropped the `org.apache.commons.lang.enums` package, so its `Enum`, " +
                               "`ValuedEnum` and `EnumUtils` types have no direct replacement. " +
                               "Flag their uses for a manual migration to Java `enum` types.";

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("apache", "commons"));

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(ENUMS_PACKAGE + ".*", false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                Object parent = getCursor().getParentTreeCursor().getValue();
                if (parent instanceof J.FieldAccess && ((J.FieldAccess) parent).getName() == identifier) {
                    return i;
                }
                return isEnumsTypeReference(i.getType(), i.getSimpleName()) ? SearchResult.found(i, MESSAGE) : i;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess fa = super.visitFieldAccess(fieldAccess, ctx);
                return isEnumsTypeReference(fa.getType(), fa.getSimpleName()) ? SearchResult.found(fa, MESSAGE) : fa;
            }

            private boolean isEnumsTypeReference(@Nullable JavaType type, String simpleName) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
                return fq != null &&
                       ENUMS_PACKAGE.equals(fq.getPackageName()) &&
                       simpleName.equals(fq.getClassName()) &&
                       getCursor().firstEnclosing(J.Import.class) == null;
            }
        });
    }
}
