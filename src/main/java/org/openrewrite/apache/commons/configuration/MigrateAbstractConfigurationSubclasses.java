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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import static org.openrewrite.apache.commons.configuration.CommonsConfiguration2.PACKAGE;
import static org.openrewrite.apache.commons.configuration.CommonsConfiguration2.extendsAbstractConfiguration;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateAbstractConfigurationSubclasses extends Recipe {

    private static final String ABSTRACT_CONFIGURATION = PACKAGE + "AbstractConfiguration";

    /**
     * The methods 1.x let subclasses override or declare, which 2.x made final, by the method 2.x has them override
     * instead. The key is the 1.x method's signature.
     */
    private static final Map<String, String> RENAMES = new HashMap<>();

    static {
        RENAMES.put("getProperty(java.lang.String)", "getPropertyInternal");
        RENAMES.put("getKeys()", "getKeysInternal");
        RENAMES.put("getKeys(java.lang.String)", "getKeysInternal");
        RENAMES.put("isEmpty()", "isEmptyInternal");
        RENAMES.put("containsKey(java.lang.String)", "containsKeyInternal");
        RENAMES.put("clear()", "clearInternal");
        RENAMES.put("setProperty(java.lang.String,java.lang.Object)", "setPropertyInternal");
        RENAMES.put("addProperty(java.lang.String,java.lang.Object)", "addPropertyInternal");
        RENAMES.put("clearProperty(java.lang.String)", "clearPropertyDirect");
        RENAMES.put("containsValue(java.lang.Object)", "containsValueInternal");
        RENAMES.put("size()", "sizeInternal");
    }

    String displayName = "Override the methods Commons Configuration 2.x lets `AbstractConfiguration` subclasses override";

    String description = "Configuration 2 made `getProperty`, `getKeys`, `isEmpty`, `containsKey`, `containsValue`, " +
            "`clear`, `clearProperty`, `setProperty`, `addProperty` and `size` of `AbstractConfiguration` final, and has " +
            "them call protected methods named with an `Internal` or `Direct` suffix, which subclasses override instead. " +
            "This renames the overrides, unless the class declares the method they are renamed to already, and the " +
            "calls they make to the superclass's implementation, and adds the `clearPropertyDirect` and " +
            "`containsValueInternal` methods that 2.x made abstract to classes that extend `AbstractConfiguration` " +
            "directly: the first does nothing, as it did by default in 1.x, and the second asks the configuration's " +
            "own keys. This recipe matches the 1.x API by the names the move to the `org.apache.commons.configuration2` " +
            "package gives it, and is meant to run as part of `UpgradeApacheCommonsConfiguration_1_2`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(PACKAGE + "*", true), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                JavaType.FullyQualified type = c.getType();
                if (type == null || !extendsAbstractConfiguration(type) ||
                    c.getKind() != J.ClassDeclaration.Kind.Type.Class) {
                    return c;
                }
                Set<String> declared = new HashSet<>();
                for (Statement statement : c.getBody().getStatements()) {
                    if (statement instanceof J.MethodDeclaration) {
                        declared.add(((J.MethodDeclaration) statement).getSimpleName());
                    }
                }
                JavaType.FullyQualified supertype = type.getSupertype();
                if (c.hasModifier(J.Modifier.Type.Abstract) || supertype == null ||
                    !ABSTRACT_CONFIGURATION.equals(supertype.getFullyQualifiedName())) {
                    return c;
                }
                // 2.x made these abstract, which 1.x either had a default for or didn't have
                if (!declared.contains("clearPropertyDirect")) {
                    c = add(c, "@Override\nprotected void clearPropertyDirect(String key) {\n}", ctx);
                }
                if (!declared.contains("containsValueInternal")) {
                    c = add(c, "@Override\nprotected boolean containsValueInternal(Object value) {\n    return contains(getKeys(), value);\n}", ctx);
                }
                return c;
            }

            private J.ClassDeclaration add(J.ClassDeclaration c, String method, ExecutionContext ctx) {
                return JavaTemplate.builder(method)
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "commons-configuration2"))
                        .build()
                        .apply(updateCursor(c), c.getBody().getCoordinates().lastStatement());
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                String rename = renameOf(m.getMethodType());
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (rename == null || enclosing == null || declares(enclosing, m.getMethodType().withName(rename))) {
                    return m;
                }
                JavaType.Method methodType = m.getMethodType().withName(rename);
                return m.withName(m.getName().withSimpleName(rename).withType(methodType)).withMethodType(methodType);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                // An override calling the implementation it overrides calls the one it now overrides instead
                if (!(m.getSelect() instanceof J.Identifier) || !"super".equals(((J.Identifier) m.getSelect()).getSimpleName())) {
                    return m;
                }
                // in 2.x the final method would call the override back, where 1.x called the superclass's own
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                JavaType.Method type = m.getMethodType();
                String rename = type == null ? null : RENAMES.get(signature(type));
                if (enclosing == null || rename == null || !type.getDeclaringType().getFullyQualifiedName().startsWith(PACKAGE) ||
                    !overrides(enclosing, type)) {
                    return m;
                }
                JavaType.Method renamed = type.withName(rename);
                return m.withName(m.getName().withSimpleName(rename).withType(renamed)).withMethodType(renamed);
            }

            private boolean declares(J.ClassDeclaration classDecl, JavaType.Method method) {
                for (Statement statement : classDecl.getBody().getStatements()) {
                    if (statement instanceof J.MethodDeclaration) {
                        JavaType.Method declared = ((J.MethodDeclaration) statement).getMethodType();
                        if (declared != null && signature(declared).equals(signature(method))) {
                            return true;
                        }
                    }
                }
                return false;
            }

            private boolean overrides(J.ClassDeclaration classDecl, JavaType.Method method) {
                for (Statement statement : classDecl.getBody().getStatements()) {
                    if (statement instanceof J.MethodDeclaration) {
                        JavaType.Method declared = ((J.MethodDeclaration) statement).getMethodType();
                        if (declared != null && signature(declared).equals(signature(method)) && renameOf(declared) != null) {
                            return true;
                        }
                    }
                }
                return false;
            }

            private @Nullable String renameOf(JavaType.@Nullable Method method) {
                if (method == null || method.hasFlags(Flag.Static) || method.isConstructor()) {
                    return null;
                }
                // A method of a subclass with the signature of one it inherits overrides it
                String rename = RENAMES.get(signature(method));
                return rename != null && extendsAbstractConfiguration(method.getDeclaringType()) ? rename : null;
            }

            private String signature(JavaType.Method method) {
                StringJoiner parameters = new StringJoiner(",", method.getName() + "(", ")");
                for (JavaType parameterType : method.getParameterTypes()) {
                    parameters.add(TypeUtils.toString(parameterType));
                }
                return parameters.toString();
            }
        });
    }
}
