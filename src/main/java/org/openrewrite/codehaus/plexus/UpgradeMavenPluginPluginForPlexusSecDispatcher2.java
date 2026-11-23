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
package org.openrewrite.codehaus.plexus;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.UpgradePluginVersion;
import org.openrewrite.xml.tree.Xml;

import java.util.concurrent.atomic.AtomicBoolean;

public class UpgradeMavenPluginPluginForPlexusSecDispatcher2 extends ScanningRecipe<AtomicBoolean> {

    @Getter
    final String displayName = "Upgrade `maven-plugin-plugin` to a version that reads the dependencies of `plexus-sec-dispatcher` 2.0";

    @Getter
    final String description = "`org.codehaus.plexus:plexus-sec-dispatcher` 2.0 depends on `org.codehaus.plexus:plexus-utils` " +
            "3.4.1, a multi-release jar with classes for Java 10. `maven-plugin-plugin` before 3.5 fails with an " +
            "`IllegalArgumentException` when it reads them, as it scans the dependencies of a Maven plugin for mojos. " +
            "In a repository where a pom declares `org.sonatype.plexus:plexus-sec-dispatcher`, which moves to 2.0, this " +
            "upgrades an older `maven-plugin-plugin` to 3.5.";

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean declaresSecDispatcher) {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                if (getResolutionResult().getPom().getRequestedDependencies().stream().anyMatch(d ->
                        "org.sonatype.plexus".equals(d.getGroupId()) && "plexus-sec-dispatcher".equals(d.getArtifactId()))) {
                    declaresSecDispatcher.set(true);
                }
                return document;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean declaresSecDispatcher) {
        if (!declaresSecDispatcher.get()) {
            return TreeVisitor.noop();
        }
        // the plugin is often managed by a parent pom, not by the module that declares the dependency
        return new UpgradePluginVersion("org.apache.maven.plugins", "maven-plugin-plugin", "3.5", null, null, null)
                .getVisitor();
    }
}
