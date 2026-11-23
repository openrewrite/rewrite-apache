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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeQualityValidation;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateToCodehausPlexusCipherTest implements RewriteTest {

    /**
     * The API of {@code org.sonatype.plexus:plexus-cipher} and {@code org.sonatype.plexus:plexus-sec-dispatcher} 1.x
     * that the recipe changes: constructors that throw, a dispatcher without a cipher, and the {@code ROLE} constants.
     */
    static final String[] SONATYPE_1_X = {
      //language=java
      """
        package org.sonatype.plexus.components.cipher;
        public class PlexusCipherException extends Exception {
            public PlexusCipherException() {}
            public PlexusCipherException(String message) {}
            public PlexusCipherException(Throwable cause) {}
            public PlexusCipherException(String message, Throwable cause) {}
        }
        """,
      //language=java
      """
        package org.sonatype.plexus.components.cipher;
        public interface PlexusCipher {
            String ROLE = PlexusCipher.class.getName();
            char ENCRYPTED_STRING_DECORATION_START = '{';
            char ENCRYPTED_STRING_DECORATION_STOP = '}';
            String encrypt(String str, String passPhrase) throws PlexusCipherException;
            String encryptAndDecorate(String str, String passPhrase) throws PlexusCipherException;
            String decrypt(String str, String passPhrase) throws PlexusCipherException;
            String decryptDecorated(String str, String passPhrase) throws PlexusCipherException;
            boolean isEncryptedString(String str);
            String unDecorate(String str) throws PlexusCipherException;
            String decorate(String str);
        }
        """,
      //language=java
      """
        package org.sonatype.plexus.components.cipher;
        public class DefaultPlexusCipher implements PlexusCipher {
            public DefaultPlexusCipher() throws PlexusCipherException {}
            public String encrypt(String str, String passPhrase) throws PlexusCipherException { return null; }
            public String encryptAndDecorate(String str, String passPhrase) throws PlexusCipherException { return null; }
            public String decrypt(String str, String passPhrase) throws PlexusCipherException { return null; }
            public String decryptDecorated(String str, String passPhrase) throws PlexusCipherException { return null; }
            public boolean isEncryptedString(String str) { return false; }
            public String unDecorate(String str) throws PlexusCipherException { return null; }
            public String decorate(String str) { return null; }
            public static String[] getServiceTypes() { return null; }
            public static String[] getCryptoImpls(String serviceType) { return null; }
            public static void main(String[] args) {}
        }
        """,
      //language=java
      """
        package org.sonatype.plexus.components.cipher;
        public class PBECipher {
            public PBECipher() throws PlexusCipherException {}
            public String encrypt64(String clearText, String password) throws PlexusCipherException { return null; }
            public String decrypt64(String encryptedText, String password) throws PlexusCipherException { return null; }
        }
        """,
      //language=java
      """
        package org.sonatype.plexus.components.sec.dispatcher;
        public class SecDispatcherException extends Exception {
            public SecDispatcherException() {}
            public SecDispatcherException(String message) {}
            public SecDispatcherException(Throwable cause) {}
            public SecDispatcherException(String message, Throwable cause) {}
        }
        """,
      //language=java
      """
        package org.sonatype.plexus.components.sec.dispatcher;
        public interface SecDispatcher {
            String ROLE = SecDispatcher.class.getName();
            String[] SYSTEM_PROPERTY_MASTER_PASSWORD = new String[]{"settings.master.password", "settings-master-password"};
            String[] SYSTEM_PROPERTY_SERVER_PASSWORD = new String[]{"settings.server.password", "settings-server-password"};
            String decrypt(String str) throws SecDispatcherException;
        }
        """,
      //language=java
      """
        package org.sonatype.plexus.components.sec.dispatcher;
        import java.util.Map;
        public interface PasswordDecryptor {
            String ROLE = PasswordDecryptor.class.getName();
            String decrypt(String str, Map attributes, Map config) throws SecDispatcherException;
        }
        """,
      //language=java
      """
        package org.sonatype.plexus.components.sec.dispatcher;
        import java.util.Map;
        import org.sonatype.plexus.components.cipher.PlexusCipher;
        public class DefaultSecDispatcher implements SecDispatcher {
            public static final String SYSTEM_PROPERTY_SEC_LOCATION = "settings.security";
            public static final String TYPE_ATTR = "type";
            public static final char ATTR_START = '[';
            public static final char ATTR_STOP = ']';
            protected PlexusCipher _cipher;
            protected Map _decryptors;
            protected String _configurationFile = "~/.settings-security.xml";
            public String decrypt(String str) throws SecDispatcherException { return null; }
            public String getConfigurationFile() { return null; }
            public void setConfigurationFile(String file) {}
            public static void main(String[] args) throws Exception {}
        }
        """
    };

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.codehaus.plexus.MigrateToCodehausPlexusCipher")
          .parser(JavaParser.fromJavaVersion().dependsOn(SONATYPE_1_X))
          .afterParser(JavaParser.fromJavaVersion().dependsOn(PlexusSecurityStubs.CODEHAUS_2_0))
          // the code the recipe leaves alone keeps the types of 1.x, whose constructors still declare that they throw
          .recipeQualityValidation(RecipeQualityValidation.builder()
            .parserTypedTrees(RecipeQualityValidation.ParserTypes.Erasure).build());
    }

    //language=xml
    private static SourceSpecs pomDeclaring(String dependencies) {
        return pomXml(
          """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>app</artifactId>
                <version>1.0</version>
                <dependencies>
            %s
                </dependencies>
            </project>
            """.formatted(dependencies.indent(8).stripTrailing()),
          spec -> spec.after(actual -> actual)
        );
    }

    private static final String SONATYPE_CIPHER = """
      <dependency>
          <groupId>org.sonatype.plexus</groupId>
          <artifactId>plexus-cipher</artifactId>
          <version>1.7</version>
      </dependency>""";

    private static final String SONATYPE_SEC_DISPATCHER = """
      <dependency>
          <groupId>org.sonatype.plexus</groupId>
          <artifactId>plexus-sec-dispatcher</artifactId>
          <version>1.4</version>
      </dependency>""";

    @DocumentExample
    @Test
    void changeDependencyAndRemoveCatchOfConstructor() {
        rewriteRun(
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.sonatype.plexus</groupId>
                            <artifactId>plexus-cipher</artifactId>
                            <version>1.7</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-cipher</artifactId>
                            <version>2.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
                  import org.sonatype.plexus.components.cipher.PlexusCipher;
                  import org.sonatype.plexus.components.cipher.PlexusCipherException;

                  class Encryptor {
                      private final PlexusCipher cipher;

                      Encryptor() {
                          try {
                              // the cipher Maven uses
                              cipher = new DefaultPlexusCipher();
                          } catch (PlexusCipherException e) {
                              throw new IllegalStateException(e);
                          }
                      }

                      String encrypt(String password, String master) throws PlexusCipherException {
                          return cipher.encryptAndDecorate(password, master);
                      }
                  }
                  """,
                """
                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
                  import org.sonatype.plexus.components.cipher.PlexusCipher;
                  import org.sonatype.plexus.components.cipher.PlexusCipherException;

                  class Encryptor {
                      private final PlexusCipher cipher;

                      Encryptor() {
                          // the cipher Maven uses
                          cipher = new DefaultPlexusCipher();
                      }

                      String encrypt(String password, String master) throws PlexusCipherException {
                          return cipher.encryptAndDecorate(password, master);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void keepCatchOfMethodThatStillThrows() {
        rewriteRun(
          mavenProject("app",
            pomDeclaring(SONATYPE_CIPHER),
            srcMainJava(
              //language=java
              java(
                """
                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
                  import org.sonatype.plexus.components.cipher.PlexusCipherException;

                  class Decryptor {
                      String decrypt(String value, String master) {
                          try {
                              DefaultPlexusCipher cipher = new DefaultPlexusCipher();
                              return cipher.decryptDecorated(value, master);
                          } catch (PlexusCipherException e) {
                              throw new IllegalStateException(e);
                          }
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void removeAlternativeOfMultiCatch() {
        rewriteRun(
          mavenProject("app",
            pomDeclaring(SONATYPE_CIPHER),
            srcMainJava(
              //language=java
              java(
                """
                  import java.io.File;
                  import java.io.IOException;
                  import java.nio.file.Files;

                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
                  import org.sonatype.plexus.components.cipher.PlexusCipherException;

                  class Settings {
                      boolean encrypted(File file) {
                          try {
                              String content = new String(Files.readAllBytes(file.toPath()));
                              return new DefaultPlexusCipher().isEncryptedString(content);
                          } catch (PlexusCipherException | IOException e) {
                              return false;
                          }
                      }
                  }
                  """,
                """
                  import java.io.File;
                  import java.io.IOException;
                  import java.nio.file.Files;

                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;

                  class Settings {
                      boolean encrypted(File file) {
                          try {
                              String content = new String(Files.readAllBytes(file.toPath()));
                              return new DefaultPlexusCipher().isEncryptedString(content);
                          } catch (IOException e) {
                              return false;
                          }
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void keepTryWithFinally() {
        rewriteRun(
          mavenProject("app",
            pomDeclaring(SONATYPE_CIPHER),
            srcMainJava(
              //language=java
              java(
                """
                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
                  import org.sonatype.plexus.components.cipher.PlexusCipher;
                  import org.sonatype.plexus.components.cipher.PlexusCipherException;

                  class Holder {
                      PlexusCipher cipher;

                      void init() {
                          try {
                              cipher = new DefaultPlexusCipher();
                          } catch (PlexusCipherException e) {
                              throw new RuntimeException(e);
                          } finally {
                              System.out.println("initialized");
                          }
                      }
                  }
                  """,
                """
                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
                  import org.sonatype.plexus.components.cipher.PlexusCipher;

                  class Holder {
                      PlexusCipher cipher;

                      void init() {
                          try {
                              cipher = new DefaultPlexusCipher();
                          } finally {
                              System.out.println("initialized");
                          }
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void keepVariablesInBlockWhenNameIsDeclaredAgainLater() {
        rewriteRun(
          mavenProject("app",
            pomDeclaring(SONATYPE_CIPHER),
            srcMainJava(
              //language=java
              java(
                """
                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
                  import org.sonatype.plexus.components.cipher.PBECipher;
                  import org.sonatype.plexus.components.cipher.PlexusCipherException;

                  class Ciphers {
                      void create() {
                          try {
                              Object cipher = new DefaultPlexusCipher();
                              System.out.println(cipher);
                          } catch (PlexusCipherException e) {
                              throw new IllegalStateException(e);
                          }
                          try {
                              Object cipher = new PBECipher();
                              System.out.println(cipher);
                          } catch (PlexusCipherException e) {
                              throw new IllegalStateException(e);
                          }
                      }
                  }
                  """,
                """
                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
                  import org.sonatype.plexus.components.cipher.PBECipher;

                  class Ciphers {
                      void create() {
                          {
                              Object cipher = new DefaultPlexusCipher();
                              System.out.println(cipher);
                          }
                          Object cipher = new PBECipher();
                          System.out.println(cipher);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void leaveModuleThatGetsSonatypeCipherThroughAnotherDependency() {
        rewriteRun(
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.maven</groupId>
                            <artifactId>maven-settings-builder</artifactId>
                            <version>3.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
                  import org.sonatype.plexus.components.cipher.PlexusCipher;
                  import org.sonatype.plexus.components.cipher.PlexusCipherException;
                  import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;

                  class Encryptor {
                      PlexusCipher cipher() {
                          try {
                              return new DefaultPlexusCipher();
                          } catch (PlexusCipherException e) {
                              throw new IllegalStateException(e);
                          }
                      }

                      DefaultSecDispatcher dispatcher() {
                          return new DefaultSecDispatcher();
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void excludeSonatypeCipherThatAnotherDependencyBrings() {
        rewriteRun(
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.maven</groupId>
                            <artifactId>maven-settings-builder</artifactId>
                            <version>3.0</version>
                        </dependency>
                        <dependency>
                            <groupId>org.sonatype.plexus</groupId>
                            <artifactId>plexus-cipher</artifactId>
                            <version>1.7</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.maven</groupId>
                            <artifactId>maven-settings-builder</artifactId>
                            <version>3.0</version>
                            <exclusions>
                                <exclusion>
                                    <groupId>org.sonatype.plexus</groupId>
                                    <artifactId>plexus-cipher</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-cipher</artifactId>
                            <version>2.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void excludeSonatypeArtifactsThatAnotherGradleDependencyBrings() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("app",
            //language=groovy
            buildGradle(
              """
                plugins {
                    id 'java-library'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'org.apache.maven:maven-settings-builder:3.6.3'
                    implementation 'org.sonatype.plexus:plexus-cipher:1.4'
                    implementation 'org.sonatype.plexus:plexus-sec-dispatcher:1.3'
                }
                """,
              """
                plugins {
                    id 'java-library'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation('org.apache.maven:maven-settings-builder:3.6.3') {
                        exclude group: 'org.sonatype.plexus', module: 'plexus-cipher'
                        exclude group: 'org.sonatype.plexus', module: 'plexus-sec-dispatcher'
                    }
                    implementation 'org.codehaus.plexus:plexus-cipher:2.0'
                    implementation 'org.codehaus.plexus:plexus-sec-dispatcher:2.0'
                }
                """
            )
          )
        );
    }

    @Test
    void upgradeMavenPluginPluginThatCannotReadPlexusUtils3() {
        rewriteRun(
          mavenProject("plugin",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example-maven-plugin</artifactId>
                    <version>1.0</version>
                    <packaging>maven-plugin</packaging>
                    <dependencies>
                        <dependency>
                            <groupId>org.sonatype.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                            <version>1.4</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-plugin-plugin</artifactId>
                                <version>3.4</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example-maven-plugin</artifactId>
                    <version>1.0</version>
                    <packaging>maven-plugin</packaging>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                            <version>2.0</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-plugin-plugin</artifactId>
                                <version>3.5</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            )
          )
        );
    }

    @Test
    void keepMavenPluginPluginThatReadsPlexusUtils3() {
        rewriteRun(
          mavenProject("plugin",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example-maven-plugin</artifactId>
                    <version>1.0</version>
                    <packaging>maven-plugin</packaging>
                    <dependencies>
                        <dependency>
                            <groupId>org.sonatype.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                            <version>1.4</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-plugin-plugin</artifactId>
                                <version>3.5</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example-maven-plugin</artifactId>
                    <version>1.0</version>
                    <packaging>maven-plugin</packaging>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                            <version>2.0</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-plugin-plugin</artifactId>
                                <version>3.5</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            )
          )
        );
    }

    @Test
    void upgradeMavenPluginPluginThatParentManages() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0</version>
                  <packaging>pom</packaging>
                  <modules>
                      <module>plugin</module>
                  </modules>
                  <build>
                      <pluginManagement>
                          <plugins>
                              <plugin>
                                  <groupId>org.apache.maven.plugins</groupId>
                                  <artifactId>maven-plugin-plugin</artifactId>
                                  <version>3.4</version>
                              </plugin>
                          </plugins>
                      </pluginManagement>
                  </build>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0</version>
                  <packaging>pom</packaging>
                  <modules>
                      <module>plugin</module>
                  </modules>
                  <build>
                      <pluginManagement>
                          <plugins>
                              <plugin>
                                  <groupId>org.apache.maven.plugins</groupId>
                                  <artifactId>maven-plugin-plugin</artifactId>
                                  <version>3.5</version>
                              </plugin>
                          </plugins>
                      </pluginManagement>
                  </build>
              </project>
              """
          ),
          mavenProject("plugin",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0</version>
                    </parent>
                    <artifactId>example-maven-plugin</artifactId>
                    <packaging>maven-plugin</packaging>
                    <dependencies>
                        <dependency>
                            <groupId>org.sonatype.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                            <version>1.4</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0</version>
                    </parent>
                    <artifactId>example-maven-plugin</artifactId>
                    <packaging>maven-plugin</packaging>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                            <version>2.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.path("plugin/pom.xml")
            )
          )
        );
    }

    @Test
    void changeSecDispatcherAndPassCipherToConstructor() {
        rewriteRun(
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.sonatype.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                            <version>1.4</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                            <version>2.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;

                  class Settings {
                      DefaultSecDispatcher dispatcher(String securitySettings) {
                          DefaultSecDispatcher dispatcher = new DefaultSecDispatcher();
                          dispatcher.setConfigurationFile(securitySettings);
                          return dispatcher;
                      }
                  }
                  """,
                """
                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
                  import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;

                  class Settings {
                      DefaultSecDispatcher dispatcher(String securitySettings) {
                          DefaultSecDispatcher dispatcher = new DefaultSecDispatcher(new DefaultPlexusCipher());
                          dispatcher.setConfigurationFile(securitySettings);
                          return dispatcher;
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void anonymousSubclassPassesCipherItAssigned() {
        rewriteRun(
          mavenProject("app",
            pomDeclaring(SONATYPE_SEC_DISPATCHER),
            srcMainJava(
              //language=java
              java(
                """
                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
                  import org.sonatype.plexus.components.cipher.PlexusCipherException;
                  import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
                  import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

                  class Utils {
                      public static final SecDispatcher PASSWORD_DECRYPTER = new DefaultSecDispatcher() {
                          {
                              _configurationFile = "~/.m2/settings-security.xml";
                              try {
                                  _cipher = new DefaultPlexusCipher();
                              } catch (PlexusCipherException e) {
                                  throw new RuntimeException(e);
                              }
                          }
                      };
                  }
                  """,
                """
                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
                  import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
                  import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

                  class Utils {
                      public static final SecDispatcher PASSWORD_DECRYPTER = new DefaultSecDispatcher(new DefaultPlexusCipher()) {
                          {
                              _configurationFile = "~/.m2/settings-security.xml";
                          }
                      };
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void subclassConstructorPassesCipherItAssigned() {
        rewriteRun(
          mavenProject("app",
            pomDeclaring(SONATYPE_SEC_DISPATCHER),
            srcMainJava(
              //language=java
              java(
                """
                  import java.io.File;

                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
                  import org.sonatype.plexus.components.cipher.PlexusCipherException;
                  import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;

                  class SettingsReader {
                      private final File homeDir = new File(".");

                      private class SpringBootSecDispatcher extends DefaultSecDispatcher {
                          private static final String SECURITY_XML = "settings-security.xml";

                          SpringBootSecDispatcher() {
                              File file = new File(SettingsReader.this.homeDir, SECURITY_XML);
                              this._configurationFile = file.getAbsolutePath();
                              try {
                                  this._cipher = new DefaultPlexusCipher();
                              }
                              catch (PlexusCipherException ex) {
                                  throw new IllegalStateException(ex);
                              }
                          }
                      }
                  }
                  """,
                """
                  import java.io.File;

                  import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
                  import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;

                  class SettingsReader {
                      private final File homeDir = new File(".");

                      private class SpringBootSecDispatcher extends DefaultSecDispatcher {
                          private static final String SECURITY_XML = "settings-security.xml";

                          SpringBootSecDispatcher() {
                              super(new DefaultPlexusCipher());
                              File file = new File(SettingsReader.this.homeDir, SECURITY_XML);
                              this._configurationFile = file.getAbsolutePath();
                          }
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void subclassConstructorPassesCipherParameter() {
        rewriteRun(
          mavenProject("app",
            pomDeclaring(SONATYPE_SEC_DISPATCHER),
            srcMainJava(
              //language=java
              java(
                """
                  import org.sonatype.plexus.components.cipher.PlexusCipher;
                  import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;

                  class FileSecDispatcher extends DefaultSecDispatcher {
                      FileSecDispatcher(PlexusCipher cipher, String file) {
                          super();
                          _cipher = cipher;
                          _configurationFile = file;
                      }
                  }
                  """,
                """
                  import org.sonatype.plexus.components.cipher.PlexusCipher;
                  import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;

                  class FileSecDispatcher extends DefaultSecDispatcher {
                      FileSecDispatcher(PlexusCipher cipher, String file) {
                          super(cipher);
                          _configurationFile = file;
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void leaveSubclassThatAssignsDecryptors() {
        rewriteRun(
          mavenProject("app",
            pomDeclaring(SONATYPE_SEC_DISPATCHER),
            srcMainJava(
              //language=java
              java(
                """
                  import java.util.HashMap;

                  import org.sonatype.plexus.components.cipher.PlexusCipher;
                  import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;

                  class CustomSecDispatcher extends DefaultSecDispatcher {
                      CustomSecDispatcher(PlexusCipher cipher) {
                          _cipher = cipher;
                          _decryptors = new HashMap();
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void passNullMessageToSecDispatcherException() {
        rewriteRun(
          mavenProject("app",
            pomDeclaring(SONATYPE_SEC_DISPATCHER),
            srcTestJava(
              //language=java
              java(
                """
                  import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
                  import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

                  class FailingDispatcher {
                      SecDispatcher failing() {
                          return str -> { throw new SecDispatcherException(); };
                      }
                  }
                  """,
                """
                  import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
                  import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

                  class FailingDispatcher {
                      SecDispatcher failing() {
                          return str -> { throw new SecDispatcherException((String) null); };
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void replaceRoleConstants() {
        rewriteRun(
          mavenProject("app",
            pomDeclaring(SONATYPE_SEC_DISPATCHER),
            srcMainJava(
              //language=java
              java(
                """
                  import java.util.Map;

                  import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

                  class Lookup {
                      Object dispatcher(Map<String, Object> container) {
                          return container.get(SecDispatcher.ROLE);
                      }
                  }
                  """,
                """
                  import java.util.Map;

                  import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

                  class Lookup {
                      Object dispatcher(Map<String, Object> container) {
                          return container.get(SecDispatcher.class.getName());
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void keepModuleAlreadyOnCodehaus() {
        rewriteRun(
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                            <version>2.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.sonatype.plexus.components.cipher.PlexusCipherException;

                  class Holder {
                      void run() throws PlexusCipherException {
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void leavePomThatOnlyExcludesSonatypeCipher() {
        rewriteRun(
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.maven</groupId>
                            <artifactId>maven-settings-builder</artifactId>
                            <version>3.0</version>
                            <exclusions>
                                <exclusion>
                                    <groupId>org.sonatype.plexus</groupId>
                                    <artifactId>plexus-cipher</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-cipher</artifactId>
                            <version>2.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void changeManagedDependencyOfParent() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0</version>
                  <packaging>pom</packaging>
                  <modules>
                      <module>app</module>
                  </modules>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.sonatype.plexus</groupId>
                              <artifactId>plexus-cipher</artifactId>
                              <version>1.4</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0</version>
                  <packaging>pom</packaging>
                  <modules>
                      <module>app</module>
                  </modules>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.codehaus.plexus</groupId>
                              <artifactId>plexus-cipher</artifactId>
                              <version>2.0</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """
          ),
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0</version>
                    </parent>
                    <artifactId>app</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>org.sonatype.plexus</groupId>
                            <artifactId>plexus-cipher</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0</version>
                    </parent>
                    <artifactId>app</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-cipher</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void replaceStaticallyImportedRoleConstant() {
        rewriteRun(
          mavenProject("app",
            pomDeclaring(SONATYPE_CIPHER),
            srcMainJava(
              //language=java
              java(
                """
                  import java.util.Map;

                  import static org.sonatype.plexus.components.cipher.PlexusCipher.ROLE;

                  class Lookup {
                      Object cipher(Map<String, Object> container) {
                          return container.get(ROLE);
                      }
                  }
                  """,
                """
                  import org.sonatype.plexus.components.cipher.PlexusCipher;

                  import java.util.Map;

                  class Lookup {
                      Object cipher(Map<String, Object> container) {
                          return container.get(PlexusCipher.class.getName());
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void leaveRepositoryWhereClassicPlexusContainerRuns() {
        rewriteRun(
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-container-default</artifactId>
                            <version>1.0-alpha-9-stable-1</version>
                        </dependency>
                        <dependency>
                            <groupId>org.sonatype.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                            <version>1.4</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-container-default</artifactId>
                            <version>1.0-alpha-9-stable-1</version>
                        </dependency>
                        <!--~~(Not migrated, as app runs a classic Plexus container, which can't create the components of 2.0)~~>--><dependency>
                            <groupId>org.sonatype.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                            <version>1.4</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
                  import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

                  class Settings {
                      SecDispatcher dispatcher = new DefaultSecDispatcher();
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void migrateWhereSisuRunsInPlaceOfClassicPlexusContainer() {
        rewriteRun(
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-container-default</artifactId>
                            <version>1.5.5</version>
                        </dependency>
                        <dependency>
                            <groupId>org.eclipse.sisu</groupId>
                            <artifactId>org.eclipse.sisu.plexus</artifactId>
                            <version>0.3.5</version>
                        </dependency>
                        <dependency>
                            <groupId>org.sonatype.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                            <version>1.4</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-container-default</artifactId>
                            <version>1.5.5</version>
                        </dependency>
                        <dependency>
                            <groupId>org.eclipse.sisu</groupId>
                            <artifactId>org.eclipse.sisu.plexus</artifactId>
                            <version>0.3.5</version>
                        </dependency>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                            <version>2.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void markWhatParentManagesForModuleThatRunsClassicPlexusContainer() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0</version>
                  <packaging>pom</packaging>
                  <modules>
                      <module>core</module>
                  </modules>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.sonatype.plexus</groupId>
                              <artifactId>plexus-sec-dispatcher</artifactId>
                              <version>1.4</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0</version>
                  <packaging>pom</packaging>
                  <modules>
                      <module>core</module>
                  </modules>
                  <dependencyManagement>
                      <dependencies>
                          <!--~~(Not migrated, as core runs a classic Plexus container, which can't create the components of 2.0)~~>--><dependency>
                              <groupId>org.sonatype.plexus</groupId>
                              <artifactId>plexus-sec-dispatcher</artifactId>
                              <version>1.4</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """
          ),
          mavenProject("core",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0</version>
                    </parent>
                    <artifactId>core</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-container-default</artifactId>
                            <version>1.0-alpha-9-stable-1</version>
                        </dependency>
                        <dependency>
                            <groupId>org.sonatype.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0</version>
                    </parent>
                    <artifactId>core</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-container-default</artifactId>
                            <version>1.0-alpha-9-stable-1</version>
                        </dependency>
                        <!--~~(Not migrated, as core runs a classic Plexus container, which can't create the components of 2.0)~~>--><dependency>
                            <groupId>org.sonatype.plexus</groupId>
                            <artifactId>plexus-sec-dispatcher</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.path("core/pom.xml")
            )
          )
        );
    }

    @Test
    void leaveGradleProjectWhereClassicPlexusContainerRuns() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("app",
            //language=groovy
            buildGradle(
              """
                plugins {
                    id 'java-library'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'org.codehaus.plexus:plexus-container-default:1.0-alpha-9-stable-1'
                    implementation 'org.sonatype.plexus:plexus-sec-dispatcher:1.4'
                }
                """,
              """
                /*~~(Not migrated, as app runs a classic Plexus container, which can't create the components of 2.0)~~>*/plugins {
                    id 'java-library'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'org.codehaus.plexus:plexus-container-default:1.0-alpha-9-stable-1'
                    implementation 'org.sonatype.plexus:plexus-sec-dispatcher:1.4'
                }
                """
            )
          )
        );
    }
}
