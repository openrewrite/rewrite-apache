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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.apache.commons.configuration.CommonsConfiguration2.PACKAGE;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateHierarchicalConfigurations extends Recipe {

    private static final String HIERARCHICAL_CONFIGURATION = PACKAGE + "HierarchicalConfiguration";
    private static final String BASE_HIERARCHICAL_CONFIGURATION = PACKAGE + "BaseHierarchicalConfiguration";
    private static final String IMMUTABLE_NODE = PACKAGE + "tree.ImmutableNode";

    String displayName = "Use the Commons Configuration 2.x hierarchical configuration types";

    String description = "Configuration 2 made `HierarchicalConfiguration` a generic interface, which " +
            "`BaseHierarchicalConfiguration` implements, and returns the sub configurations of `configurationsAt` as " +
            "`HierarchicalConfiguration<ImmutableNode>`. This creates and extends `BaseHierarchicalConfiguration` where " +
            "1.x code created or extended `HierarchicalConfiguration`, and gives `HierarchicalConfiguration` the " +
            "`ImmutableNode` type argument, which every hierarchical configuration of 2.x has, where variables, methods, " +
            "casts and type arguments refer to it, as the raw type has none of the generic types of its methods, such " +
            "as the `List` that `configurationsAt` returns. This recipe matches the 1.x " +
            "API by the names the move to the `org.apache.commons.configuration2` package gives it, and is meant to run " +
            "as part of `UpgradeApacheCommonsConfiguration_1_2`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(HIERARCHICAL_CONFIGURATION, true), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                TypeTree extendings = c.getExtends();
                if (extendings != null && TypeUtils.isOfClassType(extendings.getType(), HIERARCHICAL_CONFIGURATION)) {
                    maybeAddImport(BASE_HIERARCHICAL_CONFIGURATION);
                    maybeRemoveImport(HIERARCHICAL_CONFIGURATION);
                    c = c.withExtends(new J.Identifier(randomId(), extendings.getPrefix(), Markers.EMPTY, new ArrayList<>(),
                            "BaseHierarchicalConfiguration", JavaType.ShallowClass.build(BASE_HIERARCHICAL_CONFIGURATION), null));
                }
                return c;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (n.getBody() != null || n.getClazz() == null ||
                    !TypeUtils.isOfClassType(n.getClazz().getType(), HIERARCHICAL_CONFIGURATION)) {
                    return n;
                }
                // 1.x had a constructor without parameters and one that copied another hierarchical configuration
                StringJoiner arguments = new StringJoiner(", ", "new BaseHierarchicalConfiguration(", ")");
                List<Object> parameters = new ArrayList<>();
                for (Expression argument : n.getArguments()) {
                    if (!(argument instanceof J.Empty)) {
                        arguments.add("#{any(" + HIERARCHICAL_CONFIGURATION + ")}");
                        parameters.add(argument);
                    }
                }
                maybeAddImport(BASE_HIERARCHICAL_CONFIGURATION);
                maybeRemoveImport(HIERARCHICAL_CONFIGURATION);
                return JavaTemplate.builder(arguments.toString())
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "commons-configuration2"))
                        .imports(BASE_HIERARCHICAL_CONFIGURATION)
                        .build()
                        .apply(getCursor(), n.getCoordinates().replace(), parameters.toArray());
            }

            @Override
            public J.ParameterizedType visitParameterizedType(J.ParameterizedType type, ExecutionContext ctx) {
                J.ParameterizedType p = super.visitParameterizedType(type, ctx);
                // configurationsAt gives the sub configurations as HierarchicalConfiguration<ImmutableNode>
                return p.withTypeParameters(ListUtils.map(p.getTypeParameters(), this::parameterize));
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations v = super.visitVariableDeclarations(multiVariable, ctx);
                // The raw type has none of the generic types of its methods, such as the List of configurationsAt
                return v.getTypeExpression() == null ? v : v.withTypeExpression(parameterize(v.getTypeExpression()));
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                return m.getReturnTypeExpression() == null ? m : m.withReturnTypeExpression(parameterize(m.getReturnTypeExpression()));
            }

            @Override
            public J.TypeCast visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
                J.TypeCast t = super.visitTypeCast(typeCast, ctx);
                return t.withClazz(t.getClazz().withTree(parameterize(t.getClazz().getTree())));
            }

            @SuppressWarnings("unchecked")
            private <T extends J> T parameterize(T tree) {
                if (!(tree instanceof J.Identifier || tree instanceof J.FieldAccess) ||
                    !TypeUtils.isOfClassType(((TypeTree) tree).getType(), HIERARCHICAL_CONFIGURATION)) {
                    return tree;
                }
                maybeAddImport(IMMUTABLE_NODE);
                TypeTree typeTree = (TypeTree) tree;
                JavaType.FullyQualified hierarchical = (JavaType.FullyQualified) typeTree.getType();
                JavaType.ShallowClass immutableNode = JavaType.ShallowClass.build(IMMUTABLE_NODE);
                J.Identifier argument = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, new ArrayList<>(),
                        "ImmutableNode", immutableNode, null);
                return (T) new J.ParameterizedType(randomId(), typeTree.getPrefix(), Markers.EMPTY,
                        (NameTree) typeTree.withPrefix(Space.EMPTY),
                        JContainer.build(singletonList(JRightPadded.build((Expression) argument))),
                        new JavaType.Parameterized(null, hierarchical, singletonList(immutableNode)));
            }
        });
    }
}
