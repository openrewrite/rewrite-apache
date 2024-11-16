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
package org.openrewrite.codehaus.plexus;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"RedundantSlf4jDefinition", "UnnecessaryLocalVariable"})
class AbstractLogEnabledToSlf4jTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AbstractLogEnabledToSlf4j())
          .parser(JavaParser.fromJavaVersion().classpath("plexus-container-default"));
    }

    @Test
    @DocumentExample
    void addAndUseLoggerField(){
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.plexus.logging.AbstractLogEnabled;
              import org.codehaus.plexus.logging.Logger;

              class A extends AbstractLogEnabled {
                  void method() {
                      getLogger().info("Hello");
                  }
                  void method2() {
                      Logger log = getLogger();
                      log.info("Hello");
                  }
              }
              """,
            """
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;

                class A {
                    private static final Logger logger = LoggerFactory.getLogger(A.class);

                    void method() {
                        logger.info("Hello");
                    }
                    void method2() {
                        Logger log = logger;
                        log.info("Hello");
                    }
                }
                """
          )
        );
    }

    @Test
    void renameFatal(){
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.plexus.logging.AbstractLogEnabled;
              import org.codehaus.plexus.logging.Logger;

              class A extends AbstractLogEnabled {
                  void method() {
                      if (getLogger().isFatalErrorEnabled()) {
                          getLogger().fatalError("Hello");
                      }
                  }
              }
              """,
            """
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;

                class A {
                    private static final Logger logger = LoggerFactory.getLogger(A.class);

                    void method() {
                        if (logger.isErrorEnabled()) {
                            logger.error("Hello");
                        }
                    }
                }
                """
          )
        );
    }

    @Test
    void removeLineWrap(){
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.plexus.logging.AbstractLogEnabled;
              import org.codehaus.plexus.logging.Logger;

              class A extends AbstractLogEnabled {
                  void method() {
                      getLogger()
                          .info("Really long line that caused the previous line to be wrapped, but looks add with field");
                  }
              }
              """,
            """
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;

                class A {
                    private static final Logger logger = LoggerFactory.getLogger(A.class);

                    void method() {
                        logger.info("Really long line that caused the previous line to be wrapped, but looks add with field");
                    }
                }
                """
          )
        );
    }

    @Test
    void removeLocalVariableDeclaration(){
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.plexus.logging.AbstractLogEnabled;
              import org.codehaus.plexus.logging.Logger;

              class A extends AbstractLogEnabled {
                  void method() {
                      Logger logger = getLogger();
                      logger.info("Hello");
                  }
              }
              """,
            """
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;

                class A {
                    private static final Logger logger = LoggerFactory.getLogger(A.class);

                    void method() {
                        logger.info("Hello");
                    }
                }
                """
          )
        );
    }

}