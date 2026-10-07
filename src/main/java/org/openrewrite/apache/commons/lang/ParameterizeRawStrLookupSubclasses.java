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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.singletonList;

public class ParameterizeRawStrLookupSubclasses extends Recipe {

    private static final String STR_LOOKUP = "org.apache.commons.text.StrLookup";

    @Getter
    final String displayName = "Parameterize raw `StrLookup` subclasses";

    @Getter
    final String description = "`org.apache.commons.text.StrLookup` implements `StringLookup`, which extends " +
                               "`UnaryOperator<String>`. Extending it as a raw type erases the inherited " +
                               "`apply(String)` default method, leaving `Function#apply(Object)` unimplemented, " +
                               "so add the `String` type argument.";

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("apache", "commons"));

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(STR_LOOKUP, true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                TypeTree extendz = cd.getExtends();
                return extendz == null || !isRawStrLookup(extendz) ? cd : cd.withExtends(parameterize(extendz));
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = super.visitNewClass(newClass, ctx);
                TypeTree clazz = nc.getClazz();
                if (nc.getBody() == null || clazz == null || !isRawStrLookup(clazz)) {
                    return nc;
                }
                return nc.withClazz(parameterize(clazz));
            }

            private boolean isRawStrLookup(TypeTree typeTree) {
                return (typeTree instanceof J.Identifier || typeTree instanceof J.FieldAccess) &&
                        TypeUtils.isOfClassType(typeTree.getType(), STR_LOOKUP);
            }

            private J.ParameterizedType parameterize(TypeTree typeTree) {
                Expression typeArgument = ((Expression) TypeTree.build("String"))
                        .withType(JavaType.ShallowClass.build("java.lang.String"));
                return new J.ParameterizedType(
                        Tree.randomId(),
                        typeTree.getPrefix(),
                        Markers.EMPTY,
                        (NameTree) typeTree.withPrefix(Space.EMPTY),
                        JContainer.build(Space.EMPTY, singletonList(JRightPadded.build(typeArgument)), Markers.EMPTY),
                        typeTree.getType());
            }
        });
    }
}
