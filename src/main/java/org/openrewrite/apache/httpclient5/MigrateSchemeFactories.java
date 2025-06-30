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

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.auth.DigestSchemeFactory;
import org.openrewrite.java.template.RecipeDescriptor;

import java.nio.charset.Charset;

public class MigrateSchemeFactories {
    @RecipeDescriptor(
            name = "Removes the charset argument from `BasicSchemeFactory()` constructor calls",
            description = "RFC 7617 mandates UTF-8 for basic auth. That constructor is now deprecated and httpclient v5 ignores the passed charset, ensuring that only UTF-8 is used.")
    static class MigrateBasicSchemeFactory {
        @BeforeTemplate
        BasicSchemeFactory before(Charset charset) {
            //noinspection deprecation
            return new BasicSchemeFactory(charset);
        }

        @AfterTemplate
        BasicSchemeFactory after() {
            return new BasicSchemeFactory();
        }
    }

    @RecipeDescriptor(
            name = "Removes the charset argument from `DigestSchemeFactory()` constructor calls",
            description = "RFC 7616 mandates UTF-8 for basic auth. That constructor is now deprecated and httpclient v5 ignores the passed charset, ensuring that only UTF-8 is used.")
    static class MigrateDigestSchemeFactory {
        @BeforeTemplate
        DigestSchemeFactory before(Charset charset) {
            //noinspection deprecation
            return new DigestSchemeFactory(charset);
        }

        @AfterTemplate
        DigestSchemeFactory after() {
            return new DigestSchemeFactory();
        }
    }
}
