/*
 * Copyright 2025 the original author or authors.
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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.nio.charset.Charset;

public class MigrateStringEntityStringCharsetConstructor extends Recipe {
    @Override
    public String getDisplayName() {
        return "Replace `new StringEntity(String, String)` with `new StringEntity(String, Charset)`";
    }

    @Override
    public String getDescription() {
        return "Replace `new StringEntity(String, String)` with `new StringEntity(String, Charset)` to eliminate literal usage for charset parameters.";
    }

    private static final MethodMatcher MATCHER_FOR_4x = new MethodMatcher("org.apache.http.entity.StringEntity <constructor>(String, String)");
    private static final MethodMatcher MATCHER_FOR_5x = new MethodMatcher("org.apache.hc.core5.http.io.entity.StringEntity <constructor>(String, String)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(MATCHER_FOR_4x), new UsesMethod<>(MATCHER_FOR_5x)), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = super.visitNewClass(newClass, ctx);
                if (MATCHER_FOR_4x.matches(nc) || MATCHER_FOR_5x.matches(nc)) {
                    return nc.withArguments(ListUtils.mapLast(nc.getArguments(), arg -> {
                        if (arg instanceof J.Literal && ((J.Literal) arg).getValue() instanceof String) {
                            String argValue = (String) ((J.Literal) arg).getValue();
                            if (Charset.isSupported(argValue)) {
                                String name = Charset.forName(argValue).name().replace("-", "_");
                                maybeAddImport("java.nio.charset.StandardCharsets");
                                return JavaTemplate.apply(
                                        "java.nio.charset.StandardCharsets." + name,
                                        new Cursor(getCursor(), arg),
                                        arg.getCoordinates().replace());
                            }
                        }
                        return arg;
                    }));
                }
                return nc;
            }
        });
    }
}
