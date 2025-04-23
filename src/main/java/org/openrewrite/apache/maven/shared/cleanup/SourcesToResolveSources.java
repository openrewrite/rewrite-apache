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
package org.openrewrite.apache.maven.shared.cleanup;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.semver.Semver;
import org.openrewrite.semver.VersionComparator;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

@SuppressWarnings("ALL")
public class SourcesToResolveSources extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate to `resolve-sources`";
    }

    @Override
    public String getDescription() {
        return "Migrate from `sources` to `resolve-sources` for the `maven-dependency-plugin`.";
    }

    private static final XPathMatcher xPathMatcher = new XPathMatcher("//plugin[artifactId='maven-dependency-plugin']/executions/execution/goals[goal='sources']/goal");
    private static final String minimumVersion = "3.7.0";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return new MavenVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = (Xml.Tag) super.visitTag(tag, ctx);

                if (!xPathMatcher.matches(getCursor())) {
                    return t;
                }

                if (isPlugInVersionInRange()) {
                    return tag.withValue("resolve-sources");
                }
                return t;
            }

            private boolean isPlugInVersionInRange() {
                Cursor pluginCursor = getCursor().dropParentUntil(i -> i instanceof Xml.Tag && ((Xml.Tag) i).getName().equals("plugin"));
                Xml.Tag MavenPluginTag = pluginCursor.getValue();

                String currentVersion = MavenPluginTag.getChildValue("version").orElse(null);
                if (currentVersion == null || !Semver.validate(currentVersion, null).isValid()) {
                    return false;
                }

                VersionComparator comparator = Semver.validate(minimumVersion, null).getValue();
                return comparator != null && comparator.compare(null, currentVersion, minimumVersion) >= 0;
            }
        };
    }
}
