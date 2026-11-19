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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateEntityContentTypeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient-4", "httpcore-4", "httpclient5", "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @DocumentExample
    @Test
    void entityConstructorsWithoutContentType() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ByteArrayEntity;
              import org.apache.http.entity.FileEntity;
              import org.apache.http.entity.InputStreamEntity;

              import java.io.File;
              import java.io.InputStream;

              class A {
                  void entities(HttpPost request, byte[] bytes, InputStream in, File file) {
                      request.setEntity(new ByteArrayEntity(bytes));
                      request.setEntity(new ByteArrayEntity(bytes, 0, 4));
                      request.setEntity(new InputStreamEntity(in));
                      request.setEntity(new InputStreamEntity(in, 42L));
                      request.setEntity(new FileEntity(file));
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.classic.methods.HttpPost;
              import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
              import org.apache.hc.core5.http.io.entity.FileEntity;
              import org.apache.hc.core5.http.io.entity.InputStreamEntity;

              import java.io.File;
              import java.io.InputStream;

              class A {
                  void entities(HttpPost request, byte[] bytes, InputStream in, File file) {
                      request.setEntity(new ByteArrayEntity(bytes, null));
                      request.setEntity(new ByteArrayEntity(bytes, 0, 4, null));
                      request.setEntity(new InputStreamEntity(in, null));
                      request.setEntity(new InputStreamEntity(in, 42L, null));
                      request.setEntity(new FileEntity(file, null));
                  }
              }
              """
          )
        );
    }

    @Test
    void contentTypeAndEncodingAreStrings() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpResponse;
              import org.apache.http.entity.StringEntity;

              class A {
                  private HttpResponse response;

                  public String getContentEncoding() {
                      return (response == null || response.getEntity() == null || response.getEntity().getContentEncoding() == null) ? null : response.getEntity().getContentEncoding().getValue();
                  }

                  public String getContentType(StringEntity entity) {
                      return entity.getContentType().getValue();
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ClassicHttpResponse;
              import org.apache.hc.core5.http.io.entity.StringEntity;

              class A {
                  private ClassicHttpResponse response;

                  public String getContentEncoding() {
                      return (response == null || response.getEntity() == null || response.getEntity().getContentEncoding() == null) ? null : response.getEntity().getContentEncoding();
                  }

                  public String getContentType(StringEntity entity) {
                      return entity.getContentType();
                  }
              }
              """
          )
        );
    }
}
