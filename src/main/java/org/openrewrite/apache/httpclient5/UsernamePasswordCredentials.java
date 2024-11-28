/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.apache.httpclient5;

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class UsernamePasswordCredentials extends Recipe {
    private static final String FQN = "org.apache.http.auth.UsernamePasswordCredentials";
    private static final String METHOD_PATTERN = FQN + " <constructor>(String, String)";

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Migrate UsernamePasswordCredentials to httpclient5";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Migrate UsernamePasswordCredentials to httpclient5.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
          new UsesMethod<>(METHOD_PATTERN),
          new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                  newClass = super.visitNewClass(newClass, ctx);
                  if (TypeUtils.isOfType(newClass.getType(), JavaType.buildType(FQN))) {
                      newClass = JavaTemplate.builder(newClass.getArguments().get(1).printTrimmed() + ".toCharArray()")
                        .build()
                        .apply(getCursor(), newClass.getArguments().get(1).getCoordinates().replace());
                  }
                  return newClass;
              }
          });
    }
}
