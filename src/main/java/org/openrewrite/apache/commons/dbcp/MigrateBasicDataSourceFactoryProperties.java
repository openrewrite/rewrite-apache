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
package org.openrewrite.apache.commons.dbcp;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateBasicDataSourceFactoryProperties extends ScanningRecipe<Set<String>> {

    private static final String CREATE_DATA_SOURCE =
            "org.apache.commons.dbcp.BasicDataSourceFactory createDataSource(java.util.Properties)";

    private static final Map<String, String> RENAMED_KEYS = new HashMap<>();

    static {
        RENAMED_KEYS.put("maxActive", "maxTotal");
        RENAMED_KEYS.put("maxWait", "maxWaitMillis");
        RENAMED_KEYS.put("removeAbandoned", "removeAbandonedOnBorrow");
    }

    String displayName = "Rename the properties `BasicDataSourceFactory` reads";

    String description = "`BasicDataSourceFactory.createDataSource(Properties)` in DBCP 2 ignores `maxActive`, `maxWait` " +
                         "and `removeAbandoned` without a warning, which leaves the pool on its defaults. This renames " +
                         "them in each `.properties` file that a class calling `createDataSource` names in a string. " +
                         "Other properties files are left alone, since their keys can be the application's own, such as " +
                         "placeholders in Spring bean definitions.";

    @Override
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> fileNames) {
        return Preconditions.check(new UsesMethod<>(new MethodMatcher(CREATE_DATA_SOURCE)), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                if (literal.getValue() instanceof String) {
                    String value = (String) literal.getValue();
                    if (value.endsWith(".properties")) {
                        fileNames.add(value.substring(value.lastIndexOf('/') + 1));
                    }
                }
                return literal;
            }
        });
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<String> fileNames) {
        return Preconditions.check(!fileNames.isEmpty(), new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.File visitFile(Properties.File file, ExecutionContext ctx) {
                return fileNames.contains(file.getSourcePath().getFileName().toString()) ? super.visitFile(file, ctx) : file;
            }

            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                String renamed = RENAMED_KEYS.get(entry.getKey());
                return renamed == null ? entry : entry.withKey(renamed);
            }
        });
    }
}
