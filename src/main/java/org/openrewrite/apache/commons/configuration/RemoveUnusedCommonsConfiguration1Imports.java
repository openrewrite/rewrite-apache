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
package org.openrewrite.apache.commons.configuration;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveUnusedCommonsConfiguration1Imports extends Recipe {

    private static final String PACKAGE = "org.apache.commons.configuration.";

    String displayName = "Remove unused imports of Commons Configuration 1.x";

    String description = "Removes imports of `org.apache.commons.configuration` types that the file doesn't refer to. " +
            "The move to the `org.apache.commons.configuration2` package would otherwise leave them importing types " +
            "that 2.x no longer has, such as `ConfigurationFactory`, which doesn't compile.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.apache.commons.configuration..*", true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                String typeName = anImport.getTypeName();
                if (!anImport.isStatic() && !"*".equals(anImport.getQualid().getSimpleName()) &&
                    typeName.startsWith(PACKAGE)) {
                    maybeRemoveImport(typeName);
                }
                return anImport;
            }
        });
    }
}
