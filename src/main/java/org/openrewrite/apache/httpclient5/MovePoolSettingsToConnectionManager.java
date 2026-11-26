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
package org.openrewrite.apache.httpclient5;

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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = false)
@Value
public class MovePoolSettingsToConnectionManager extends Recipe {

    private static final String BUILDER = "org.apache.hc.client5.http.impl.classic.HttpClientBuilder";
    private static final String POOL_BUILDER = "org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder";
    private static final MethodMatcher BUILD = new MethodMatcher(BUILDER + " build()");
    private static final MethodMatcher CREATE = new MethodMatcher(BUILDER + " create()");
    private static final MethodMatcher CUSTOM = new MethodMatcher("org.apache.hc.client5.http.impl.classic.HttpClients custom()");

    // HttpClient 4 settings that HttpClient 5 has on PoolingHttpClientConnectionManagerBuilder, with their argument types there
    private static final Map<String, String> POOL_SETTINGS = new LinkedHashMap<>();
    private static final List<MethodMatcher> POOL_SETTING_MATCHERS = new ArrayList<>();

    static {
        POOL_SETTINGS.put("setMaxConnTotal", "int");
        POOL_SETTINGS.put("setMaxConnPerRoute", "int");
        POOL_SETTINGS.put("setDefaultSocketConfig", "org.apache.hc.core5.http.io.SocketConfig");
        POOL_SETTINGS.put("setDnsResolver", "org.apache.hc.client5.http.DnsResolver");
        for (String name : POOL_SETTINGS.keySet()) {
            POOL_SETTING_MATCHERS.add(new MethodMatcher(BUILDER + " " + name + "(..)"));
        }
    }

    // HttpClient 4 builds its own connection manager from these as well; with any of them on the same builder, moving only
    // the pool settings onto a new connection manager could silently drop the others
    private static final List<MethodMatcher> OTHER_CONNECTION_MANAGER_INPUTS = Arrays.asList(
            new MethodMatcher(BUILDER + " setConnectionManager(..)"),
            new MethodMatcher(BUILDER + " useSystemProperties()"),
            new MethodMatcher(BUILDER + " setSSLSocketFactory(..)"),
            new MethodMatcher(BUILDER + " setSSLContext(..)"),
            new MethodMatcher(BUILDER + " setSslcontext(..)"),
            new MethodMatcher(BUILDER + " setSSLHostnameVerifier(..)"),
            new MethodMatcher(BUILDER + " setPublicSuffixMatcher(..)"),
            new MethodMatcher(BUILDER + " setDefaultConnectionConfig(..)"),
            new MethodMatcher(BUILDER + " setConnectionTimeToLive(..)"));

    String displayName = "Move `HttpClientBuilder` pool settings to a `PoolingHttpClientConnectionManager`";

    String description = "HttpClient 4's `HttpClientBuilder` applies `setMaxConnTotal`, `setMaxConnPerRoute`, " +
            "`setDefaultSocketConfig` and `setDnsResolver` to the connection manager it creates when none is set. " +
            "HttpClient 5's `HttpClientBuilder` no longer has these methods; set them on a " +
            "`PoolingHttpClientConnectionManagerBuilder` passed to `setConnectionManager` instead. This covers fluent " +
            "chains from `HttpClientBuilder.create()` or `HttpClients.custom()` to `build()`, and separate statements " +
            "on a local builder that is created, configured and built in the same block. Builders that also set a " +
            "connection manager, TLS settings, a connection config, a time to live or use system properties are left " +
            "unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesMethod<>(BUILDER + " setMaxConnTotal(..)"),
                new UsesMethod<>(BUILDER + " setMaxConnPerRoute(..)"),
                new UsesMethod<>(BUILDER + " setDefaultSocketConfig(..)"),
                new UsesMethod<>(BUILDER + " setDnsResolver(..)")), new JavaVisitor<ExecutionContext>() {

            @Override
            public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (!isPoolSetting(method)) {
                    return super.visitMethodInvocation(method, ctx);
                }
                Object parent = getCursor().getParentTreeCursor().getValue();
                if (parent instanceof J.Block && method.getSelect() instanceof J.Identifier) {
                    return moveStatement(method, (J.Block) parent, ctx);
                }
                return moveInChain(method, ctx);
            }

            // builder.setMaxConnTotal(100); as separate statements on a builder created, configured and built in the same block
            private @Nullable J moveStatement(J.MethodInvocation method, J.Block block, ExecutionContext ctx) {
                J.Identifier builder = (J.Identifier) method.getSelect();
                List<J.MethodInvocation> group = new ArrayList<>();
                for (Statement statement : block.getStatements()) {
                    if (statement instanceof J.MethodInvocation && isPoolSetting((J.MethodInvocation) statement) &&
                            isSameVariable(((J.MethodInvocation) statement).getSelect(), builder)) {
                        group.add((J.MethodInvocation) statement);
                    }
                }
                if (!isCreatedIn(builder, block) || !isOnlyConfiguredAndBuiltIn(block, builder, group)) {
                    return method;
                }
                if (group.get(group.size() - 1) != method) {
                    // Merged into the last of these statements
                    return null;
                }
                return setConnectionManager(method, builder, group, ctx);
            }

            // HttpClients.custom().setMaxConnTotal(100).setMaxConnPerRoute(20).build()
            private J moveInChain(J.MethodInvocation method, ExecutionContext ctx) {
                List<J.MethodInvocation> chain = chainOf(method);
                List<J.MethodInvocation> settings = new ArrayList<>();
                for (J.MethodInvocation m : chain) {
                    if (isPoolSetting(m)) {
                        settings.add(m);
                    }
                }
                // The last pool setting of a chain that creates and builds the client takes the connection manager
                if (settings.get(settings.size() - 1) != method || !BUILD.matches(chain.get(chain.size() - 1)) ||
                        !createsBuilder(chain.get(0)) ||
                        chain.stream().anyMatch(MovePoolSettingsToConnectionManager::isOtherConnectionManagerInput)) {
                    return super.visitMethodInvocation(method, ctx);
                }
                return setConnectionManager(method, withoutPoolSettings(method.getSelect()), settings, ctx);
            }

            // Turns the given pool setting into `setConnectionManager(...)` on the given builder, keeping its formatting
            private J setConnectionManager(J.MethodInvocation method, @Nullable Expression builder,
                                           List<J.MethodInvocation> settings, ExecutionContext ctx) {
                StringBuilder code = new StringBuilder("#{any(" + BUILDER + ")}.setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()");
                List<Object> parameters = new ArrayList<>();
                parameters.add(builder);
                for (J.MethodInvocation setting : settings) {
                    String name = setting.getSimpleName();
                    code.append('.').append(name).append("(#{any(").append(POOL_SETTINGS.get(name)).append(")})");
                    parameters.add(setting.getArguments().get(0));
                }
                code.append(".build())");
                maybeAddImport(POOL_BUILDER);
                J.MethodInvocation templated = JavaTemplate.builder(code.toString())
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpclient5", "httpcore5"))
                        .imports(POOL_BUILDER)
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), parameters.toArray());
                return method
                        .withSelect(builder)
                        .withName(templated.getName().withPrefix(method.getName().getPrefix()))
                        .withArguments(templated.getArguments())
                        .withMethodType(templated.getMethodType());
            }

            private List<J.MethodInvocation> chainOf(J.MethodInvocation method) {
                // All calls of the fluent chain, innermost first
                Deque<J.MethodInvocation> chain = new ArrayDeque<>();
                for (Expression e = method; e instanceof J.MethodInvocation; e = ((J.MethodInvocation) e).getSelect()) {
                    chain.addFirst((J.MethodInvocation) e);
                }
                J current = method;
                for (Cursor parent = getCursor().getParentTreeCursor();
                     parent.getValue() instanceof J.MethodInvocation &&
                             ((J.MethodInvocation) parent.getValue()).getSelect() == current;
                     parent = parent.getParentTreeCursor()) {
                    current = parent.getValue();
                    chain.addLast((J.MethodInvocation) current);
                }
                return new ArrayList<>(chain);
            }

            private @Nullable Expression withoutPoolSettings(@Nullable Expression select) {
                if (!(select instanceof J.MethodInvocation)) {
                    return select;
                }
                J.MethodInvocation m = (J.MethodInvocation) select;
                if (isPoolSetting(m)) {
                    return withoutPoolSettings(m.getSelect());
                }
                return m.withSelect(withoutPoolSettings(m.getSelect()));
            }

            private boolean isOnlyConfiguredAndBuiltIn(J.Block block, J.Identifier builder, List<J.MethodInvocation> poolSettings) {
                AtomicBoolean built = new AtomicBoolean();
                boolean usedOtherwise = new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, AtomicBoolean found) {
                        if (isSameVariable(innermostSelect(m), builder)) {
                            if (BUILD.matches(m)) {
                                built.set(true);
                            } else if (isOtherConnectionManagerInput(m) ||
                                    isPoolSetting(m) && poolSettings.stream().noneMatch(setting -> setting == m)) {
                                found.set(true);
                            }
                        }
                        return super.visitMethodInvocation(m, found);
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean found) {
                        // Passed, returned or reassigned, so it may be configured where this recipe cannot see
                        Object parent = getCursor().getParentTreeCursor().getValue();
                        if (isSameVariable(identifier, builder) &&
                                !(parent instanceof J.VariableDeclarations.NamedVariable) &&
                                !(parent instanceof J.MethodInvocation && ((J.MethodInvocation) parent).getSelect() == identifier)) {
                            found.set(true);
                        }
                        return super.visitIdentifier(identifier, found);
                    }
                }.reduce(block, new AtomicBoolean(), getCursor().getParentTreeCursor().getParentOrThrow()).get();
                return built.get() && !usedOtherwise;
            }
        });
    }

    private static boolean isPoolSetting(J.MethodInvocation m) {
        return POOL_SETTING_MATCHERS.stream().anyMatch(matcher -> matcher.matches(m));
    }

    private static boolean isOtherConnectionManagerInput(J.MethodInvocation m) {
        return OTHER_CONNECTION_MANAGER_INPUTS.stream().anyMatch(matcher -> matcher.matches(m));
    }

    private static @Nullable Expression innermostSelect(J.MethodInvocation m) {
        Expression e = m;
        while (e instanceof J.MethodInvocation) {
            e = ((J.MethodInvocation) e).getSelect();
        }
        return e;
    }

    private static boolean isSameVariable(@Nullable Expression e, J.Identifier builder) {
        return e instanceof J.Identifier && ((J.Identifier) e).getSimpleName().equals(builder.getSimpleName()) &&
                Objects.equals(((J.Identifier) e).getFieldType(), builder.getFieldType());
    }

    private static boolean isCreatedIn(J.Identifier builder, J.Block block) {
        for (Statement statement : block.getStatements()) {
            if (statement instanceof J.VariableDeclarations) {
                for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) statement).getVariables()) {
                    if (isSameVariable(variable.getName(), builder)) {
                        return createsBuilder(variable.getInitializer());
                    }
                }
            }
        }
        return false;
    }

    // HttpClientBuilder.create() or HttpClients.custom(), possibly followed by settings that do not go into the connection manager
    private static boolean createsBuilder(@Nullable Expression e) {
        for (; e instanceof J.MethodInvocation; e = ((J.MethodInvocation) e).getSelect()) {
            J.MethodInvocation m = (J.MethodInvocation) e;
            if (CREATE.matches(m) || CUSTOM.matches(m)) {
                return true;
            }
            if (isPoolSetting(m) || isOtherConnectionManagerInput(m)) {
                return false;
            }
        }
        return false;
    }
}
