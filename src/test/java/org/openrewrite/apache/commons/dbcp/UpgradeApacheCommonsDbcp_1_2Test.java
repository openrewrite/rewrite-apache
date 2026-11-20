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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeApacheCommonsDbcp_1_2Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "commons-dbcp-1", "commons-dbcp2", "commons-pool-1", "commons-pool2"))
          .recipeFromResources("org.openrewrite.apache.commons.dbcp.UpgradeApacheCommonsDbcp_1_2");
    }

    /**
     * The precondition reads dependencies from a project, which sources outside of one don't belong to.
     */
    private void runInProject(SourceSpecs... sources) {
        rewriteRun(mavenProject("example", sources));
    }

    /**
     * The recipe only acts on repositories that depend on commons-dbcp, so every source under test sits next to this.
     */
    private static SourceSpecs dbcpPom() {
        return pomXml(
          //language=xml
          """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>org.example</groupId>
                <artifactId>example</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>commons-dbcp</groupId>
                        <artifactId>commons-dbcp</artifactId>
                        <version>1.4</version>
                    </dependency>
                </dependencies>
            </project>
            """,
          spec -> spec.after(pom -> {
              Matcher version = Pattern.compile("<version>(2\\.\\d+\\.\\d+)</version>").matcher(pom);
              assertThat(version.find()).describedAs("Expected 2.x in %s", pom).isTrue();
              //language=xml
              return """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.commons</groupId>
                            <artifactId>commons-dbcp2</artifactId>
                            <version>%s</version>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(version.group(1));
          })
        );
    }

    @Nested
    class JavaSources {

        @DocumentExample
        @Test
        void renamedBasicDataSourceProperties() {
            runInProject(
              dbcpPom(),
              //language=java
              java(
                """
                  import org.apache.commons.dbcp.BasicDataSource;

                  class Test {
                      BasicDataSource dataSource(String url) {
                          BasicDataSource ds = new BasicDataSource();
                          ds.setUrl(url);
                          ds.setMaxActive(20);
                          ds.setMaxIdle(5);
                          ds.setMaxWait(3000L);
                          ds.setRemoveAbandoned(true);
                          ds.setRemoveAbandonedTimeout(60);
                          return ds;
                      }

                      String describe(BasicDataSource ds) {
                          return ds.getMaxActive() + "/" + ds.getMaxWait() + "/" + ds.getRemoveAbandoned();
                      }
                  }
                  """,
                """
                  import org.apache.commons.dbcp2.BasicDataSource;

                  class Test {
                      BasicDataSource dataSource(String url) {
                          BasicDataSource ds = new BasicDataSource();
                          ds.setUrl(url);
                          ds.setMaxTotal(20);
                          ds.setMaxIdle(5);
                          ds.setMaxWaitMillis(3000L);
                          ds.setRemoveAbandonedOnBorrow(true);
                          ds.setRemoveAbandonedTimeout(60);
                          return ds;
                      }

                      String describe(BasicDataSource ds) {
                          return ds.getMaxTotal() + "/" + ds.getMaxWaitMillis() + "/" + ds.getRemoveAbandonedOnBorrow();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void subclassOverridesAndUnqualifiedCalls() {
            runInProject(
              dbcpPom(),
              //language=java
              java(
                """
                  import org.apache.commons.dbcp.BasicDataSource;

                  class PooledDataSource extends BasicDataSource {
                      PooledDataSource() {
                          setMaxActive(50);
                      }

                      @Override
                      public synchronized void setMaxWait(long maxWait) {
                          super.setMaxWait(maxWait * 2);
                      }
                  }
                  """,
                """
                  import org.apache.commons.dbcp2.BasicDataSource;

                  class PooledDataSource extends BasicDataSource {
                      PooledDataSource() {
                          setMaxTotal(50);
                      }

                      @Override
                      public synchronized void setMaxWaitMillis(long maxWait) {
                          super.setMaxWaitMillis(maxWait * 2);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void sharedPoolDataSource() {
            runInProject(
              dbcpPom(),
              //language=java
              java(
                """
                  import org.apache.commons.dbcp.cpdsadapter.DriverAdapterCPDS;
                  import org.apache.commons.dbcp.datasources.SharedPoolDataSource;

                  class Test {
                      SharedPoolDataSource dataSource(DriverAdapterCPDS cpds) {
                          SharedPoolDataSource ds = new SharedPoolDataSource();
                          ds.setConnectionPoolDataSource(cpds);
                          ds.setMaxActive(10);
                          ds.setMaxIdle(2);
                          ds.setMaxWait(50);
                          return ds;
                      }

                      long describe(SharedPoolDataSource ds) {
                          return ds.getMaxActive() + ds.getMaxIdle() + ds.getMaxWait();
                      }
                  }
                  """,
                """
                  import org.apache.commons.dbcp2.cpdsadapter.DriverAdapterCPDS;
                  import org.apache.commons.dbcp2.datasources.SharedPoolDataSource;

                  class Test {
                      SharedPoolDataSource dataSource(DriverAdapterCPDS cpds) {
                          SharedPoolDataSource ds = new SharedPoolDataSource();
                          ds.setConnectionPoolDataSource(cpds);
                          ds.setMaxTotal(10);
                          ds.setDefaultMaxIdle(2);
                          ds.setDefaultMaxWaitMillis(50);
                          return ds;
                      }

                      long describe(SharedPoolDataSource ds) {
                          return ds.getMaxTotal() + ds.getDefaultMaxIdle() + ds.getDefaultMaxWaitMillis();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void removedExceptions() {
            runInProject(
              dbcpPom(),
              //language=java
              java(
                """
                  import org.apache.commons.dbcp.DbcpException;
                  import org.apache.commons.dbcp.SQLNestedException;

                  class Test {
                      void checked(Exception cause) throws SQLNestedException {
                          throw new SQLNestedException("failed", cause);
                      }

                      void unchecked(Exception cause) {
                          throw new DbcpException("failed", cause);
                      }
                  }
                  """,
                """
                  import java.sql.SQLException;

                  class Test {
                      void checked(Exception cause) throws SQLException {
                          throw new SQLException("failed", cause);
                      }

                      void unchecked(Exception cause) {
                          throw new RuntimeException("failed", cause);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void abandonedConfigMovedToCommonsPool2() {
            runInProject(
              dbcpPom(),
              //language=java
              java(
                """
                  import org.apache.commons.dbcp.AbandonedConfig;

                  class Test {
                      AbandonedConfig config() {
                          AbandonedConfig config = new AbandonedConfig();
                          config.setRemoveAbandoned(true);
                          config.setLogAbandoned(config.getRemoveAbandoned());
                          return config;
                      }
                  }
                  """,
                """
                  import org.apache.commons.pool2.impl.AbandonedConfig;

                  class Test {
                      AbandonedConfig config() {
                          AbandonedConfig config = new AbandonedConfig();
                          config.setRemoveAbandonedOnBorrow(true);
                          config.setLogAbandoned(config.getRemoveAbandonedOnBorrow());
                          return config;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void classNamesInStringLiterals() {
            runInProject(
              dbcpPom(),
              //language=java
              java(
                """
                  class Test {
                      static final String DATA_SOURCE = "org.apache.commons.dbcp.BasicDataSource";
                      static final String FACTORY = "org.apache.commons.dbcp.BasicDataSourceFactory";

                      void load() throws Exception {
                          Class.forName("org.apache.commons.dbcp.PoolingDriver");
                          Class.forName("org.apache.commons.dbcp.DelegatingConnection");
                      }
                  }
                  """,
                """
                  class Test {
                      static final String DATA_SOURCE = "org.apache.commons.dbcp2.BasicDataSource";
                      static final String FACTORY = "org.apache.commons.dbcp2.BasicDataSourceFactory";

                      void load() throws Exception {
                          Class.forName("org.apache.commons.dbcp2.PoolingDriver");
                          Class.forName("org.apache.commons.dbcp2.DelegatingConnection");
                      }
                  }
                  """
              )
            );
        }

        /**
         * The 15 constructors of {@code PoolableConnectionFactory} became one, and the pool now has to be created after
         * the factory instead of before. That is a port to commons-pool2 with no mechanical rewrite, so the recipe
         * moves the package and leaves the wiring to fail at compile time, on the lines that need a person.
         */
        @Test
        void manualPoolWiringIsLeftForAPerson() {
            runInProject(
              dbcpPom(),
              //language=java
              java(
                """
                  import org.apache.commons.dbcp.ConnectionFactory;
                  import org.apache.commons.dbcp.DriverManagerConnectionFactory;
                  import org.apache.commons.dbcp.PoolableConnectionFactory;
                  import org.apache.commons.dbcp.PoolingDataSource;
                  import org.apache.commons.pool.impl.GenericObjectPool;

                  import javax.sql.DataSource;

                  class Test {
                      DataSource dataSource(String url) {
                          GenericObjectPool pool = new GenericObjectPool(null);
                          ConnectionFactory factory = new DriverManagerConnectionFactory(url, null);
                          new PoolableConnectionFactory(factory, pool, null, null, false, true);
                          return new PoolingDataSource(pool);
                      }
                  }
                  """,
                """
                  import org.apache.commons.dbcp2.ConnectionFactory;
                  import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
                  import org.apache.commons.dbcp2.PoolableConnectionFactory;
                  import org.apache.commons.dbcp2.PoolingDataSource;
                  import org.apache.commons.pool.impl.GenericObjectPool;

                  import javax.sql.DataSource;

                  class Test {
                      DataSource dataSource(String url) {
                          GenericObjectPool pool = new GenericObjectPool(null);
                          ConnectionFactory factory = new DriverManagerConnectionFactory(url, null);
                          new PoolableConnectionFactory(factory, pool, null, null, false, true);
                          return new PoolingDataSource(pool);
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class SpringXml {

        @Test
        void renamedProperties() {
            runInProject(
              dbcpPom(),
              //language=xml
              xml(
                """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <beans xmlns="http://www.springframework.org/schema/beans"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
                      <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource" destroy-method="close">
                          <property name="url" value="${jdbc.url}"/>
                          <property name="maxActive" value="${jdbc.maxActive}"/>
                          <property name="maxIdle" value="5"/>
                          <property name="maxWait">
                              <value>3000</value>
                          </property>
                          <property name="removeAbandoned" value="true"/>
                          <property name="removeAbandonedTimeout" value="60"/>
                      </bean>
                  </beans>
                  """,
                """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <beans xmlns="http://www.springframework.org/schema/beans"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
                      <bean id="dataSource" class="org.apache.commons.dbcp2.BasicDataSource" destroy-method="close">
                          <property name="url" value="${jdbc.url}"/>
                          <property name="maxTotal" value="${jdbc.maxActive}"/>
                          <property name="maxIdle" value="5"/>
                          <property name="maxWaitMillis">
                              <value>3000</value>
                          </property>
                          <property name="removeAbandonedOnBorrow" value="true"/>
                          <property name="removeAbandonedTimeout" value="60"/>
                      </bean>
                  </beans>
                  """,
                spec -> spec.path("src/main/resources/applicationContext.xml")
              )
            );
        }

        @Test
        void pNamespaceAttributes() {
            runInProject(
              dbcpPom(),
              //language=xml
              xml(
                """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <beans xmlns="http://www.springframework.org/schema/beans"
                         xmlns:p="http://www.springframework.org/schema/p"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
                      <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource"
                            p:url="${jdbc.url}"
                            p:maxActive="100"
                            p:maxWait="10000"
                            p:removeAbandoned="true"/>
                  </beans>
                  """,
                """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <beans xmlns="http://www.springframework.org/schema/beans"
                         xmlns:p="http://www.springframework.org/schema/p"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
                      <bean id="dataSource" class="org.apache.commons.dbcp2.BasicDataSource"
                            p:url="${jdbc.url}"
                            p:maxTotal="100"
                            p:maxWaitMillis="10000"
                            p:removeAbandonedOnBorrow="true"/>
                  </beans>
                  """,
                spec -> spec.path("src/main/resources/applicationContext.xml")
              )
            );
        }

        @Test
        void prefixedBeansNamespace() {
            runInProject(
              dbcpPom(),
              //language=xml
              xml(
                """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <beans:beans xmlns:beans="http://www.springframework.org/schema/beans"
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                               xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
                      <beans:bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
                          <beans:property name="maxActive" value="20"/>
                      </beans:bean>
                  </beans:beans>
                  """,
                """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <beans:beans xmlns:beans="http://www.springframework.org/schema/beans"
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                               xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
                      <beans:bean id="dataSource" class="org.apache.commons.dbcp2.BasicDataSource">
                          <beans:property name="maxTotal" value="20"/>
                      </beans:bean>
                  </beans:beans>
                  """,
                spec -> spec.path("src/main/resources/applicationContext.xml")
              )
            );
        }

        /**
         * Without a beans schema location the document is not one {@code ChangePackage} recognizes as Spring, and a
         * class left behind would only fail at runtime, with the jar it names gone.
         */
        @Test
        void documentWithoutSchemaLocation() {
            runInProject(
              dbcpPom(),
              //language=xml
              xml(
                """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN//EN" "http://www.springframework.org/dtd/spring-beans.dtd">
                  <beans>
                      <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
                          <property name="maxActive" value="20"/>
                      </bean>
                      <bean id="adapter" class="org.apache.commons.dbcp.cpdsadapter.DriverAdapterCPDS"/>
                  </beans>
                  """,
                """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN//EN" "http://www.springframework.org/dtd/spring-beans.dtd">
                  <beans>
                      <bean id="dataSource" class="org.apache.commons.dbcp2.BasicDataSource">
                          <property name="maxTotal" value="20"/>
                      </bean>
                      <bean id="adapter" class="org.apache.commons.dbcp2.cpdsadapter.DriverAdapterCPDS"/>
                  </beans>
                  """,
                spec -> spec.path("src/main/resources/applicationContext.xml")
              )
            );
        }

        /**
         * Not only Spring reads a class name out of XML, and the name would fail at runtime with the jar it needs gone.
         */
        @Test
        void otherXmlFormatsNamingTheClass() {
            runInProject(
              dbcpPom(),
              //language=xml
              xml(
                """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <ddal-config>
                      <dataNodes>
                          <datasource id="db1m" class="org.apache.commons.dbcp.BasicDataSource">
                              <property name="url" value="jdbc:h2:mem:db1"/>
                              <property name="maxActive" value="10"/>
                          </datasource>
                      </dataNodes>
                  </ddal-config>
                  """,
                """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <ddal-config>
                      <dataNodes>
                          <datasource id="db1m" class="org.apache.commons.dbcp2.BasicDataSource">
                              <property name="url" value="jdbc:h2:mem:db1"/>
                              <property name="maxTotal" value="10"/>
                          </datasource>
                      </dataNodes>
                  </ddal-config>
                  """,
                spec -> spec.path("src/test/resources/config/ddal-config.xml")
              )
            );
        }

        /**
         * Druid and the Tomcat JDBC pool name these properties the same as DBCP 1.x did, and still mean them.
         */
        @Test
        void otherConnectionPoolsKeepTheirProperties() {
            runInProject(
              dbcpPom(),
              //language=xml
              xml(
                """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <beans xmlns="http://www.springframework.org/schema/beans"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
                      <bean id="druid" class="com.alibaba.druid.pool.DruidDataSource">
                          <property name="maxActive" value="20"/>
                          <property name="maxWait" value="3000"/>
                      </bean>
                      <bean id="dbcp" class="org.apache.commons.dbcp.BasicDataSource">
                          <property name="maxActive" value="20"/>
                      </bean>
                      <bean id="tomcat" class="org.apache.tomcat.jdbc.pool.DataSource">
                          <property name="maxActive" value="20"/>
                          <property name="removeAbandoned" value="true"/>
                      </bean>
                  </beans>
                  """,
                """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <beans xmlns="http://www.springframework.org/schema/beans"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
                      <bean id="druid" class="com.alibaba.druid.pool.DruidDataSource">
                          <property name="maxActive" value="20"/>
                          <property name="maxWait" value="3000"/>
                      </bean>
                      <bean id="dbcp" class="org.apache.commons.dbcp2.BasicDataSource">
                          <property name="maxTotal" value="20"/>
                      </bean>
                      <bean id="tomcat" class="org.apache.tomcat.jdbc.pool.DataSource">
                          <property name="maxActive" value="20"/>
                          <property name="removeAbandoned" value="true"/>
                      </bean>
                  </beans>
                  """,
                spec -> spec.path("src/main/resources/applicationContext.xml")
              )
            );
        }
    }

    /**
     * {@code createDataSource(Properties)} ignores the old keys without so much as a logged warning, leaving a pool of
     * 8 connections that waits forever for one, whatever the file says.
     */
    @Nested
    class BasicDataSourceFactoryProperties {

        @Test
        void propertiesFileNamedNextToTheFactory() {
            runInProject(
              dbcpPom(),
              //language=java
              java(
                """
                  import org.apache.commons.dbcp.BasicDataSourceFactory;

                  import javax.sql.DataSource;
                  import java.util.Properties;

                  class Pool {
                      DataSource dataSource() throws Exception {
                          Properties properties = new Properties();
                          properties.load(Pool.class.getResourceAsStream("/write.db.properties"));
                          return BasicDataSourceFactory.createDataSource(properties);
                      }
                  }
                  """,
                """
                  import org.apache.commons.dbcp2.BasicDataSourceFactory;

                  import javax.sql.DataSource;
                  import java.util.Properties;

                  class Pool {
                      DataSource dataSource() throws Exception {
                          Properties properties = new Properties();
                          properties.load(Pool.class.getResourceAsStream("/write.db.properties"));
                          return BasicDataSourceFactory.createDataSource(properties);
                      }
                  }
                  """
              ),
              properties(
                """
                  driverClassName=com.mysql.jdbc.Driver
                  maxActive=500
                  maxIdle=20
                  maxWait=10000
                  removeAbandoned=true
                  removeAbandonedTimeout=60
                  """,
                """
                  driverClassName=com.mysql.jdbc.Driver
                  maxTotal=500
                  maxIdle=20
                  maxWaitMillis=10000
                  removeAbandonedOnBorrow=true
                  removeAbandonedTimeout=60
                  """,
                spec -> spec.path("src/main/resources/write.db.properties")
              )
            );
        }

        /**
         * Keys in a file of placeholders are the application's own, and {@code ${maxActive}} would no longer resolve.
         */
        @Test
        void propertiesTheFactoryIsNotGiven() {
            runInProject(
              dbcpPom(),
              //language=java
              java(
                """
                  import org.apache.commons.dbcp.BasicDataSourceFactory;

                  import javax.sql.DataSource;
                  import java.util.Properties;

                  class Pool {
                      DataSource dataSource() throws Exception {
                          Properties properties = new Properties();
                          properties.load(Pool.class.getResourceAsStream("/dbcp.properties"));
                          return BasicDataSourceFactory.createDataSource(properties);
                      }
                  }
                  """,
                """
                  import org.apache.commons.dbcp2.BasicDataSourceFactory;

                  import javax.sql.DataSource;
                  import java.util.Properties;

                  class Pool {
                      DataSource dataSource() throws Exception {
                          Properties properties = new Properties();
                          properties.load(Pool.class.getResourceAsStream("/dbcp.properties"));
                          return BasicDataSourceFactory.createDataSource(properties);
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  import java.util.Properties;

                  class Settings {
                      Properties settings() throws Exception {
                          Properties properties = new Properties();
                          properties.load(Settings.class.getResourceAsStream("/jdbc.properties"));
                          return properties;
                      }
                  }
                  """
              ),
              properties(
                """
                  maxActive=20
                  """,
                spec -> spec.path("src/main/resources/jdbc.properties")
              )
            );
        }
    }

    @Nested
    class Dependencies {

        @Test
        void mavenVersionInProperty() {
            runInProject(
              //language=xml
              pomXml(
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                      <properties>
                          <commons-dbcp.version>1.4</commons-dbcp.version>
                      </properties>
                      <dependencies>
                          <dependency>
                              <groupId>commons-dbcp</groupId>
                              <artifactId>commons-dbcp</artifactId>
                              <version>${commons-dbcp.version}</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """,
                spec -> spec.after(pom -> {
                    Matcher version = Pattern.compile("<commons-dbcp\\.version>(2\\.\\d+\\.\\d+)</commons-dbcp\\.version>").matcher(pom);
                    assertThat(version.find()).describedAs("Expected 2.x in %s", pom).isTrue();
                    //language=xml
                    return """
                      <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>org.example</groupId>
                          <artifactId>example</artifactId>
                          <version>1.0.0</version>
                          <properties>
                              <commons-dbcp.version>%s</commons-dbcp.version>
                          </properties>
                          <dependencies>
                              <dependency>
                                  <groupId>org.apache.commons</groupId>
                                  <artifactId>commons-dbcp2</artifactId>
                                  <version>${commons-dbcp.version}</version>
                              </dependency>
                          </dependencies>
                      </project>
                      """.formatted(version.group(1));
                })
              )
            );
        }

        /**
         * commons-pool came with commons-dbcp, and goes with it unless something still asks for it.
         */
        @Test
        void keepCommonsPoolThatCameWithCommonsDbcp() {
            runInProject(
              //language=xml
              pomXml(
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                          <dependency>
                              <groupId>commons-dbcp</groupId>
                              <artifactId>commons-dbcp</artifactId>
                              <version>1.4</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """,
                spec -> spec.after(pom -> {
                    assertThat(pom).contains("<artifactId>commons-dbcp2</artifactId>");
                    assertThat(pom).contains("<groupId>commons-pool</groupId>");
                    assertThat(pom).doesNotContain("<artifactId>commons-dbcp</artifactId>");
                    return pom;
                })
              ),
              srcMainJava(
                //language=java
                java(
                  """
                    import org.apache.commons.pool.ObjectPool;

                    class Test {
                        ObjectPool pool;
                    }
                    """
                )
              )
            );
        }

        /**
         * A managed version leaves the module with nothing to resolve the dependency against once the management moved
         * on, so module and parent have to change together, whatever scope the module asks for.
         */
        @Test
        void managedInParentAndProvidedAndOptionalInModule() {
            rewriteRun(
              mavenProject("parent",
                //language=xml
                pomXml(
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>org.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0.0</version>
                        <packaging>pom</packaging>
                        <modules>
                            <module>core</module>
                            <module>sample</module>
                        </modules>
                        <dependencyManagement>
                            <dependencies>
                                <dependency>
                                    <groupId>commons-dbcp</groupId>
                                    <artifactId>commons-dbcp</artifactId>
                                    <version>1.4</version>
                                </dependency>
                            </dependencies>
                        </dependencyManagement>
                    </project>
                    """,
                  spec -> spec.after(pom -> {
                      assertThat(pom).contains("<artifactId>commons-dbcp2</artifactId>");
                      assertThat(pom).doesNotContain("<artifactId>commons-dbcp</artifactId>");
                      return pom;
                  })
                ),
                mavenProject("core",
                  //language=xml
                  pomXml(
                    """
                      <project>
                          <modelVersion>4.0.0</modelVersion>
                          <parent>
                              <groupId>org.example</groupId>
                              <artifactId>parent</artifactId>
                              <version>1.0.0</version>
                          </parent>
                          <artifactId>core</artifactId>
                          <dependencies>
                              <dependency>
                                  <groupId>commons-dbcp</groupId>
                                  <artifactId>commons-dbcp</artifactId>
                              </dependency>
                          </dependencies>
                      </project>
                      """,
                    """
                      <project>
                          <modelVersion>4.0.0</modelVersion>
                          <parent>
                              <groupId>org.example</groupId>
                              <artifactId>parent</artifactId>
                              <version>1.0.0</version>
                          </parent>
                          <artifactId>core</artifactId>
                          <dependencies>
                              <dependency>
                                  <groupId>org.apache.commons</groupId>
                                  <artifactId>commons-dbcp2</artifactId>
                              </dependency>
                          </dependencies>
                      </project>
                      """
                  )
                ),
                mavenProject("sample",
                  //language=xml
                  pomXml(
                    """
                      <project>
                          <modelVersion>4.0.0</modelVersion>
                          <parent>
                              <groupId>org.example</groupId>
                              <artifactId>parent</artifactId>
                              <version>1.0.0</version>
                          </parent>
                          <artifactId>sample</artifactId>
                          <version>${project.parent.version}</version>
                          <dependencies>
                              <dependency>
                                  <groupId>commons-dbcp</groupId>
                                  <artifactId>commons-dbcp</artifactId>
                                  <scope>provided</scope>
                                  <optional>true</optional>
                              </dependency>
                          </dependencies>
                      </project>
                      """,
                    spec -> spec.after(pom -> {
                        assertThat(pom).contains("<artifactId>commons-dbcp2</artifactId>");
                        assertThat(pom).doesNotContain("<artifactId>commons-dbcp</artifactId>");
                        return pom;
                    })
                  )
                )
              )
            );
        }

        @Test
        void addCommonsDbcp2WhenCommonsDbcpOnlyCameTransitively() {
            runInProject(
              //language=xml
              pomXml(
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.springframework.batch</groupId>
                              <artifactId>spring-batch-test</artifactId>
                              <version>2.2.7.RELEASE</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """,
                spec -> spec.after(pom -> {
                    assertThat(pom).contains("<artifactId>spring-batch-test</artifactId>");
                    assertThat(pom).contains("<artifactId>commons-dbcp2</artifactId>");
                    return pom;
                })
              ),
              srcMainJava(
                //language=java
                java(
                  """
                    import org.apache.commons.dbcp.BasicDataSource;

                    class Test {
                        BasicDataSource ds;
                    }
                    """,
                  """
                    import org.apache.commons.dbcp2.BasicDataSource;

                    class Test {
                        BasicDataSource ds;
                    }
                    """
                )
              )
            );
        }
    }

    @Nested
    class NoChange {

        /**
         * A repository that holds {@code org.apache.commons.dbcp} classes of its own, as a shim that stands in for the
         * library, or that gets the library from somewhere a recipe cannot change, is not one to move to dbcp2.
         */
        @Test
        void repositoryWithoutTheDependency() {
            runInProject(
              //language=xml
              pomXml(
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                  </project>
                  """
              ),
              //language=java
              java(
                """
                  import org.apache.commons.dbcp.BasicDataSource;

                  class Test {
                      void configure(BasicDataSource ds) {
                          ds.setMaxActive(20);
                      }
                  }
                  """
              ),
              //language=xml
              xml(
                """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <beans xmlns="http://www.springframework.org/schema/beans"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
                      <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
                          <property name="maxActive" value="20"/>
                      </bean>
                  </beans>
                  """,
                spec -> spec.path("src/main/resources/applicationContext.xml")
              )
            );
        }

        @Test
        void alreadyOnDbcp2() {
            runInProject(
              //language=xml
              pomXml(
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.commons</groupId>
                              <artifactId>commons-dbcp2</artifactId>
                              <version>2.9.0</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """
              ),
              //language=java
              java(
                """
                  import org.apache.commons.dbcp2.BasicDataSource;

                  class Test {
                      void configure(BasicDataSource ds) {
                          ds.setMaxTotal(20);
                      }
                  }
                  """
              )
            );
        }
    }
}
