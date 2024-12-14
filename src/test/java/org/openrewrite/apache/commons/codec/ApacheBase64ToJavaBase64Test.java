/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.apache.commons.codec;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ApacheBase64ToJavaBase64Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new ApacheBase64ToJavaBase64())
          .parser(JavaParser.fromJavaVersion().classpath("commons-codec"));
    }

    @DocumentExample
    @Test
    void toJavaBase64() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.codec.binary.Base64;
                            
              class Test {
                  static byte[] decodeBytes(byte[] encodedBytes) {
                      return Base64.decodeBase64(encodedBytes);
                  }
                  static byte[] decodeToBytes(String encodedString) {
                      return Base64.decodeBase64(encodedString);
                  }
                  static String encodeToString(byte[] decodedByteArr) {
                      return Base64.encodeBase64String(decodedByteArr);
                  }
                  static byte[] encodeBase64(byte[] binaryData) {
                      return Base64.encodeBase64(binaryData);
                  }
                  static byte[] encodeBytesUrlSafe(byte [] encodeBytes) {
                      return Base64.encodeBase64URLSafe(encodeBytes);
                  }
                  static String encodeBytesUrlSafeString(byte [] encodeBytes) {
                      return Base64.encodeBase64URLSafeString(encodeBytes);
                  }
              }
              """,
            """
              import java.util.Base64;

              class Test {
                  static byte[] decodeBytes(byte[] encodedBytes) {
                      return Base64.getDecoder().decode(encodedBytes);
                  }
                  static byte[] decodeToBytes(String encodedString) {
                      return Base64.getDecoder().decode(encodedString);
                  }
                  static String encodeToString(byte[] decodedByteArr) {
                      return Base64.getEncoder().encodeToString(decodedByteArr);
                  }
                  static byte[] encodeBase64(byte[] binaryData) {
                      return Base64.getEncoder().encode(binaryData);
                  }
                  static byte[] encodeBytesUrlSafe(byte [] encodeBytes) {
                      return Base64.getUrlEncoder().withoutPadding().encode(encodeBytes);
                  }
                  static String encodeBytesUrlSafeString(byte [] encodeBytes) {
                      return Base64.getUrlEncoder().withoutPadding().encodeToString(encodeBytes);
                  }
              }
              """
          )
        );
    }
}
