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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extracts the version the recipes picked for a dependency, so tests do not pin the minor version
 * that the recipes currently resolve (for example {@code 5.6.x}).
 */
final class DependencyVersions {

    private DependencyVersions() {
    }

    /**
     * @param buildFile  the content of a {@code pom.xml} or {@code build.gradle}
     * @param artifactId the artifact to find, such as {@code httpclient5} or {@code httpcore5}
     * @return the {@code 5.x.y} version declared for that artifact
     */
    static String versionOf(String buildFile, String artifactId) {
        Matcher matcher = Pattern.compile(
          Pattern.quote(artifactId) + "(?:</artifactId>\\s*<version>|:)(5\\.\\d+\\.\\d+)").matcher(buildFile);
        assertThat(matcher.find())
          .describedAs("Expected a 5.x.y version for %s in %s", artifactId, buildFile)
          .isTrue();
        return matcher.group(1);
    }
}
