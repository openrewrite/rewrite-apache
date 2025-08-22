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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveByteBufferAllocators extends Recipe {
    private static final String dbbaClassName = "org.apache.http.nio.util.DirectByteBufferAllocator";
    private static final String dbbaAllocatePattern = dbbaClassName + " allocate(int)";
    private static final MethodMatcher dbbaAllocateMatcher = new MethodMatcher(dbbaAllocatePattern);
    private static final String hbbaClassName = "org.apache.http.nio.util.HeapByteBufferAllocator";
    private static final String hbbaAllocatePattern = hbbaClassName + " allocate(int)";
    private static final MethodMatcher hbbaAllocateMatcher = new MethodMatcher(hbbaAllocatePattern);

    @Override
    public String getDisplayName() {
        return "Remove ByteBufferAllocator implementations";
    }

    @Override
    public String getDescription() {
        return "In Apache Http Client 5.x migration, both implementations of `ByteBufferAllocator` have been removed. " +
                "This recipe will remove usage of said classes in favour of direct static calls to `ByteBuffer`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(new UsesMethod<>(dbbaAllocatePattern), new UsesMethod<>(hbbaAllocatePattern)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        if (dbbaAllocateMatcher.matches(m)) {
                            maybeAddImport("java.nio.ByteBuffer");
                            maybeRemoveImport(dbbaClassName);
                            return JavaTemplate
                                    .builder("ByteBuffer.allocateDirect(#{any(int)})")
                                    .imports("java.nio.ByteBuffer")
                                    .build()
                                    .apply(getCursor(), m.getCoordinates().replace(), m.getArguments().get(0));
                        }
                        if (hbbaAllocateMatcher.matches(m)) {
                            maybeAddImport("java.nio.ByteBuffer");
                            maybeRemoveImport(hbbaClassName);
                            return JavaTemplate
                                    .builder("ByteBuffer.allocate(#{any(int)})")
                                    .imports("java.nio.ByteBuffer")
                                    .build()
                                    .apply(getCursor(), m.getCoordinates().replace(), m.getArguments().get(0));
                        }
                        return m;
                    }
                }
        );
    }
}
