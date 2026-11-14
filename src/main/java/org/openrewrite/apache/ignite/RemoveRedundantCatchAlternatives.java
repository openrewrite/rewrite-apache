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
package org.openrewrite.apache.ignite;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveRedundantCatchAlternatives extends Recipe {

    private static final String IGNITE_EXCEPTION = "org.apache.ignite.lang.IgniteException";

    /**
     * Ignite 3 exceptions that the exception types of Ignite 2 change to, and the one they extend. The types that
     * {@code ChangeType} gives them carry no supertypes to ask.
     */
    private static final Map<String, String> SUPERTYPES = new HashMap<>();

    static {
        SUPERTYPES.put("org.apache.ignite.client.IgniteClientConnectionException", IGNITE_EXCEPTION);
        SUPERTYPES.put("org.apache.ignite.tx.TransactionException", IGNITE_EXCEPTION);
    }

    String displayName = "Remove catch alternatives that Ignite 3 merged";

    String description = "Ignite 3 throws `IgniteException` where Ignite 2 threw `ClientException` or `IgniteException`, " +
            "so a multi-catch of both names one type twice once the types are changed, which does not compile. " +
            "This keeps the first of them, and drops an alternative that is a subclass of another, such as " +
            "`IgniteClientConnectionException` next to `IgniteException`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.apache.ignite..*", false), new JavaVisitor<ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof J.CompilationUnit;
            }

            @Override
            public J visitMultiCatch(J.MultiCatch multiCatch, ExecutionContext ctx) {
                J.MultiCatch mc = (J.MultiCatch) super.visitMultiCatch(multiCatch, ctx);
                List<NameTree> alternatives = mc.getAlternatives();
                List<NameTree> kept = ListUtils.map(alternatives, (i, alternative) -> {
                    for (int j = 0; j < alternatives.size(); j++) {
                        if (j != i && isCoveredBy(alternative, alternatives.get(j), j < i)) {
                            JavaType.FullyQualified type = TypeUtils.asFullyQualified(alternative.getType());
                            if (type != null) {
                                maybeRemoveImport(type.getFullyQualifiedName());
                            }
                            return null;
                        }
                    }
                    return alternative;
                });
                if (kept.size() == alternatives.size()) {
                    return mc;
                }
                if (kept.size() == 1) {
                    return kept.get(0).withPrefix(mc.getPrefix());
                }
                return mc.withAlternatives(ListUtils.mapFirst(kept, first -> first.withPrefix(alternatives.get(0).getPrefix())));
            }
        });
    }

    /**
     * Whether a catch of the other type already catches this one: it is the same type and comes first, or a supertype.
     */
    private static boolean isCoveredBy(NameTree alternative, NameTree other, boolean otherComesFirst) {
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(alternative.getType());
        JavaType.FullyQualified otherType = TypeUtils.asFullyQualified(other.getType());
        if (type == null || otherType == null) {
            return false;
        }
        if (TypeUtils.isOfClassType(type, otherType.getFullyQualifiedName())) {
            return otherComesFirst;
        }
        return otherType.getFullyQualifiedName().equals(SUPERTYPES.get(type.getFullyQualifiedName())) ||
               TypeUtils.isAssignableTo(otherType.getFullyQualifiedName(), type);
    }
}
