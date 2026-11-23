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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;

import static org.openrewrite.apache.commons.configuration.CommonsConfiguration2.PACKAGE;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateUntypedListGetters extends Recipe {

    String displayName = "Ask Commons Configuration 2.x for lists of the type the code expects";

    String description = "Before 1.7, `getList` returned a raw `List`, which code could assign to a `List<String>`. " +
            "2.x returns a `List<Object>`, which it can't, so this asks for the element type instead, as in " +
            "`getList(String.class, key, new ArrayList<>())`, which converts the values to it. The default keeps the " +
            "empty list that 1.x returned for a missing key. This recipe matches the 1.x API by the names the move to " +
            "the `org.apache.commons.configuration2` package gives it, and is meant to run as part of " +
            "`UpgradeApacheCommonsConfiguration_1_2`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(PACKAGE + "*", true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method type = m.getMethodType();
                if (m.getSelect() == null || type == null || !"getList".equals(type.getName()) ||
                    !type.getDeclaringType().getFullyQualifiedName().startsWith(PACKAGE) ||
                    type.getParameterTypes().isEmpty() || type.getParameterTypes().size() > 2 ||
                    !TypeUtils.isString(type.getParameterTypes().get(0)) ||
                    type.getReturnType() instanceof JavaType.Parameterized) {
                    return m;
                }
                JavaType.FullyQualified element = expectedElementType(getCursor().getParentTreeCursor(), m);
                if (element == null) {
                    return m;
                }
                String elementName = element.getClassName();
                boolean defaulted = type.getParameterTypes().size() == 2;
                String code = "#{any(" + PACKAGE + "Configuration)}.getList(" + elementName + ".class, #{any(java.lang.String)}, " +
                              (defaulted ? "#{any(java.util.List)}" : "new ArrayList<" + (infersArgumentDiamond() ? "" : elementName) + ">()") + ")";
                if (!defaulted) {
                    maybeAddImport("java.util.ArrayList");
                }
                List<Object> parameters = new ArrayList<>();
                parameters.add(m.getSelect());
                for (Expression argument : m.getArguments()) {
                    parameters.add(argument);
                }
                return JavaTemplate.builder(code)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "commons-configuration2"))
                        .imports("java.util.ArrayList", element.getFullyQualifiedName())
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace(), parameters.toArray());
            }

            /**
             * The element type of the list a variable, assignment or return statement expects the call to give, unless
             * it is {@code Object}, which 2.x returns already.
             */
            private JavaType.@Nullable FullyQualified expectedElementType(Cursor parent, J.MethodInvocation m) {
                JavaType expected = null;
                Object tree = parent.getValue();
                if (tree instanceof J.VariableDeclarations.NamedVariable && ((J.VariableDeclarations.NamedVariable) tree).getInitializer() == m) {
                    J.VariableDeclarations declarations = parent.getParentTreeCursor().getValue();
                    expected = declarations.getTypeExpression() == null ? null : declarations.getTypeExpression().getType();
                } else if (tree instanceof J.Assignment && ((J.Assignment) tree).getAssignment() == m) {
                    expected = ((J.Assignment) tree).getVariable().getType();
                } else if (tree instanceof J.Return) {
                    Object enclosing = parent.dropParentUntil(t -> t instanceof J.MethodDeclaration || t instanceof J.Lambda ||
                                                                   t instanceof J.ClassDeclaration).getValue();
                    if (enclosing instanceof J.MethodDeclaration && ((J.MethodDeclaration) enclosing).getMethodType() != null) {
                        expected = ((J.MethodDeclaration) enclosing).getMethodType().getReturnType();
                    }
                }
                if (!(expected instanceof JavaType.Parameterized) ||
                    !TypeUtils.isOfClassType(expected, "java.util.List") &&
                    !TypeUtils.isOfClassType(expected, "java.util.Collection") &&
                    !TypeUtils.isOfClassType(expected, "java.lang.Iterable")) {
                    return null;
                }
                List<JavaType> typeParameters = ((JavaType.Parameterized) expected).getTypeParameters();
                if (typeParameters.size() != 1 || !(typeParameters.get(0) instanceof JavaType.Class) ||
                    TypeUtils.isObject(typeParameters.get(0))) {
                    return null;
                }
                return (JavaType.FullyQualified) typeParameters.get(0);
            }

            /**
             * Java 7 has the diamond, but infers {@code ArrayList<Object>} for one given as an argument, as only Java 8
             * infers it from the parameter's type.
             */
            private boolean infersArgumentDiamond() {
                JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
                JavaVersion version = sourceFile == null ? null : sourceFile.getMarkers().findFirst(JavaVersion.class).orElse(null);
                return version == null || version.getMajorVersion() >= 8;
            }
        });
    }
}
