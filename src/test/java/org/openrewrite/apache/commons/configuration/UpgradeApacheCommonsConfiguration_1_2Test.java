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
package org.openrewrite.apache.commons.configuration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeQualityValidation;
import org.openrewrite.test.RecipeQualityValidation.ParserTypes;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.java.Assertions.version;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeApacheCommonsConfiguration_1_2Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "commons-configuration-1", "commons-configuration2", "commons-lang-2"))
          .afterParser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "commons-configuration2"))
          .recipeFromResources("org.openrewrite.apache.commons.configuration.UpgradeApacheCommonsConfiguration_1_2")
          // The package move keeps the 1.x declaring types of the methods it moves, where 2.x declares many of them on
          // another type of its hierarchy, such as Configuration.getString on ImmutableConfiguration
          .recipeQualityValidation(RecipeQualityValidation.builder().parserTypedTrees(ParserTypes.Off).build());
    }

    /**
     * The precondition reads dependencies from a project, which sources outside of one don't belong to, and dependencies
     * are added to the main source set that uses them.
     */
    private void runInProject(SourceSpecs pom, SourceSpecs... sources) {
        rewriteRun(mavenProject("example", pom, srcMainJava(sources)));
    }

    /**
     * The recipe only acts on repositories that depend on commons-configuration, so every source under test sits next
     * to this.
     *
     * @param beanUtils whether the module uses a configuration that 1.x constructed from a file, which gets it Commons
     *                  BeanUtils for the builders
     */
    private static SourceSpecs configurationPom(boolean beanUtils) {
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
                        <groupId>commons-configuration</groupId>
                        <artifactId>commons-configuration</artifactId>
                        <version>1.10</version>
                    </dependency>
                </dependencies>
            </project>
            """,
          spec -> spec.after(pom -> {
              Matcher version = Pattern.compile("<artifactId>commons-configuration2</artifactId>\\s*<version>(2\\.\\d+\\.\\d+)</version>").matcher(pom);
              assertThat(version.find()).describedAs("Expected commons-configuration2 2.x in %s", pom).isTrue();
              assertThat(version.find()).describedAs("Expected commons-configuration2 once in %s", pom).isFalse();
              assertThat(pom).doesNotContain("<artifactId>commons-configuration</artifactId>");
              // commons-configuration2 brings the Commons libraries it needs itself
              assertThat(pom).doesNotContain("<dependencyManagement>");
              if (beanUtils) {
                  assertThat(pom).contains("<artifactId>commons-beanutils</artifactId>");
              } else {
                  assertThat(pom).doesNotContain("<artifactId>commons-beanutils</artifactId>");
              }
              return pom;
          })
        );
    }

    private static SourceSpecs unchangedConfigurationPom() {
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
                        <groupId>commons-configuration</groupId>
                        <artifactId>commons-configuration</artifactId>
                        <version>1.10</version>
                    </dependency>
                </dependencies>
            </project>
            """
        );
    }

    @Nested
    class Construction {

        @DocumentExample
        @Test
        void loadFromFileName() {
            runInProject(
              configurationPom(true),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.Configuration;
                  import org.apache.commons.configuration.ConfigurationException;
                  import org.apache.commons.configuration.PropertiesConfiguration;

                  class Generator {
                      static Configuration getConfig() {
                          try {
                              return new PropertiesConfiguration("generator.properties");
                          } catch (ConfigurationException e) {
                              throw new IllegalStateException("Cannot read the generator configuration", e);
                          }
                      }

                      String packageName() {
                          return getConfig().getString("package");
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.Configuration;
                  import org.apache.commons.configuration2.PropertiesConfiguration;
                  import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
                  import org.apache.commons.configuration2.builder.fluent.Parameters;
                  import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
                  import org.apache.commons.configuration2.ex.ConfigurationException;

                  class Generator {
                      static Configuration getConfig() {
                          try {
                              return new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class)
                                      .configure(new Parameters().properties()
                                              .setFileName("generator.properties")
                                              .setListDelimiterHandler(new DefaultListDelimiterHandler(',')))
                                      .getConfiguration();
                          } catch (ConfigurationException e) {
                              throw new IllegalStateException("Cannot read the generator configuration", e);
                          }
                      }

                      String packageName() {
                          return getConfig().getString("package");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void loadAndSaveLocalVariable() {
            runInProject(
              configurationPom(true),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.ConfigurationException;
                  import org.apache.commons.configuration.PropertiesConfiguration;

                  import java.util.Map;

                  class GeneratorController {
                      void update(Map<String, Object> map) throws ConfigurationException {
                          PropertiesConfiguration conf = new PropertiesConfiguration("generator.properties");
                          conf.setProperty("author", map.get("author"));
                          conf.save();
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.PropertiesConfiguration;
                  import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
                  import org.apache.commons.configuration2.builder.fluent.Parameters;
                  import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
                  import org.apache.commons.configuration2.ex.ConfigurationException;

                  import java.util.Map;

                  class GeneratorController {
                      void update(Map<String, Object> map) throws ConfigurationException {
                          FileBasedConfigurationBuilder<PropertiesConfiguration> confBuilder = new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class)
                                  .configure(new Parameters().properties()
                                          .setFileName("generator.properties")
                                          .setListDelimiterHandler(new DefaultListDelimiterHandler(',')));
                          PropertiesConfiguration conf = confBuilder.getConfiguration();
                          conf.setProperty("author", map.get("author"));
                          confBuilder.save();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fileThatMayNotExist() {
            runInProject(
              configurationPom(true),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.ConfigurationException;
                  import org.apache.commons.configuration.XMLConfiguration;

                  import java.io.File;

                  class Settings {
                      XMLConfiguration settings(File file) throws ConfigurationException {
                          return new XMLConfiguration(file);
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.XMLConfiguration;
                  import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
                  import org.apache.commons.configuration2.builder.fluent.Parameters;
                  import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
                  import org.apache.commons.configuration2.ex.ConfigurationException;

                  import java.io.File;

                  class Settings {
                      XMLConfiguration settings(File file) throws ConfigurationException {
                          return new FileBasedConfigurationBuilder<>(XMLConfiguration.class, null, true)
                                  .configure(new Parameters().xml()
                                          .setFile(file)
                                          .setListDelimiterHandler(new DefaultListDelimiterHandler(',')))
                                  .getConfiguration();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void emptyConfigurationLoadedLater() {
            runInProject(
              configurationPom(true),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.ConfigurationException;
                  import org.apache.commons.configuration.PropertiesConfiguration;

                  import java.io.InputStream;

                  class Loader {
                      PropertiesConfiguration load(InputStream in) throws ConfigurationException {
                          PropertiesConfiguration config = new PropertiesConfiguration();
                          config.load(in);
                          return config;
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.PropertiesConfiguration;
                  import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
                  import org.apache.commons.configuration2.ex.ConfigurationException;
                  import org.apache.commons.configuration2.io.FileHandler;

                  import java.io.InputStream;

                  class Loader {
                      PropertiesConfiguration load(InputStream in) throws ConfigurationException {
                          PropertiesConfiguration config = new PropertiesConfiguration();
                          config.setListDelimiterHandler(new DefaultListDelimiterHandler(','));
                          new FileHandler(config).load(in);
                          return config;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fileNameSetBeforeLoading() {
            runInProject(
              configurationPom(true),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.ConfigurationException;
                  import org.apache.commons.configuration.PropertiesConfiguration;

                  class Loader {
                      PropertiesConfiguration load(String name) throws ConfigurationException {
                          PropertiesConfiguration config = new PropertiesConfiguration();
                          config.setDelimiterParsingDisabled(true);
                          config.setEncoding("UTF-8");
                          config.setFileName(name);
                          config.load();
                          return config;
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.PropertiesConfiguration;
                  import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
                  import org.apache.commons.configuration2.ex.ConfigurationException;
                  import org.apache.commons.configuration2.io.FileHandler;

                  class Loader {
                      PropertiesConfiguration load(String name) throws ConfigurationException {
                          PropertiesConfiguration config = new PropertiesConfiguration();
                          FileHandler configHandler = new FileHandler(config);
                          config.setListDelimiterHandler(DisabledListDelimiterHandler.INSTANCE);
                          configHandler.setEncoding("UTF-8");
                          configHandler.setFileName(name);
                          configHandler.load();
                          return config;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void saveFieldToGivenFile() {
            runInProject(
              configurationPom(true),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.ConfigurationException;
                  import org.apache.commons.configuration.PropertiesConfiguration;

                  class Settings {
                      private PropertiesConfiguration config;

                      void save(String path) throws ConfigurationException {
                          config.save(path);
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.PropertiesConfiguration;
                  import org.apache.commons.configuration2.ex.ConfigurationException;
                  import org.apache.commons.configuration2.io.FileHandler;

                  class Settings {
                      private PropertiesConfiguration config;

                      void save(String path) throws ConfigurationException {
                          new FileHandler(config).save(path);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void sourceBeforeJava7() {
            runInProject(
              configurationPom(true),
              //language=java
              version(
                java(
                  """
                    import org.apache.commons.configuration.ConfigurationException;
                    import org.apache.commons.configuration.PropertiesConfiguration;

                    class Generator {
                        PropertiesConfiguration load() throws ConfigurationException {
                            return new PropertiesConfiguration("generator.properties");
                        }
                    }
                    """,
                  """
                    import org.apache.commons.configuration2.PropertiesConfiguration;
                    import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
                    import org.apache.commons.configuration2.builder.fluent.Parameters;
                    import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
                    import org.apache.commons.configuration2.ex.ConfigurationException;

                    class Generator {
                        PropertiesConfiguration load() throws ConfigurationException {
                            return new FileBasedConfigurationBuilder<PropertiesConfiguration>(PropertiesConfiguration.class)
                                    .configure(new Parameters().properties()
                                            .setFileName("generator.properties")
                                            .setListDelimiterHandler(new DefaultListDelimiterHandler(',')))
                                    .getConfiguration();
                        }
                    }
                    """
                ),
                6
              )
            );
        }

        @Test
        void assignedInTryBlockAndSaved() {
            runInProject(
              configurationPom(true),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.ConfigurationException;
                  import org.apache.commons.configuration.PropertiesConfiguration;

                  class Properties {
                      void update(String path, String name, String value) {
                          PropertiesConfiguration config = null;
                          try {
                              config = new PropertiesConfiguration(path);
                              config.setProperty(name, value);
                              config.save();
                          } catch (ConfigurationException e) {
                              throw new IllegalStateException(e);
                          }
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.PropertiesConfiguration;
                  import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
                  import org.apache.commons.configuration2.builder.fluent.Parameters;
                  import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
                  import org.apache.commons.configuration2.ex.ConfigurationException;

                  class Properties {
                      void update(String path, String name, String value) {
                          PropertiesConfiguration config = null;
                          try {
                              FileBasedConfigurationBuilder<PropertiesConfiguration> configBuilder = new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class)
                                      .configure(new Parameters().properties()
                                              .setFileName(path)
                                              .setListDelimiterHandler(new DefaultListDelimiterHandler(',')));
                              config = configBuilder.getConfiguration();
                              config.setProperty(name, value);
                              configBuilder.save();
                          } catch (ConfigurationException e) {
                              throw new IllegalStateException(e);
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void emptyConfigurationHeldAsInterface() {
            runInProject(
              configurationPom(true),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.Configuration;
                  import org.apache.commons.configuration.PropertiesConfiguration;

                  class Backend {
                      Configuration configure(String host) {
                          Configuration conf = new PropertiesConfiguration();
                          conf.setProperty("host", host);
                          return conf;
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.Configuration;
                  import org.apache.commons.configuration2.PropertiesConfiguration;
                  import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;

                  class Backend {
                      Configuration configure(String host) {
                          Configuration conf = new PropertiesConfiguration();
                          ((PropertiesConfiguration) conf).setListDelimiterHandler(new DefaultListDelimiterHandler(','));
                          conf.setProperty("host", host);
                          return conf;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fieldLoadedFromGivenFile() {
            runInProject(
              configurationPom(true),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.ConfigurationException;
                  import org.apache.commons.configuration.FileConfiguration;
                  import org.apache.commons.configuration.PropertiesConfiguration;

                  class Crawler {
                      final FileConfiguration configuration = new PropertiesConfiguration();

                      void init(String path) throws ConfigurationException {
                          configuration.load(path);
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.FileBasedConfiguration;
                  import org.apache.commons.configuration2.PropertiesConfiguration;
                  import org.apache.commons.configuration2.ex.ConfigurationException;
                  import org.apache.commons.configuration2.io.FileHandler;

                  class Crawler {
                      final FileBasedConfiguration configuration = new PropertiesConfiguration();

                      void init(String path) throws ConfigurationException {
                          new FileHandler(configuration).load(path);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void subclassLoadingFile() {
            runInProject(
              configurationPom(true),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.ConfigurationException;
                  import org.apache.commons.configuration.PropertiesConfiguration;

                  import java.io.File;

                  class Config extends PropertiesConfiguration {
                      Config(String location) throws ConfigurationException {
                          super(location);
                      }

                      Config(File file) throws ConfigurationException {
                          super(file);
                          setProperty("loaded", true);
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.PropertiesConfiguration;
                  import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
                  import org.apache.commons.configuration2.ex.ConfigurationException;
                  import org.apache.commons.configuration2.io.FileHandler;

                  import java.io.File;

                  class Config extends PropertiesConfiguration {
                      Config(String location) throws ConfigurationException {
                          setListDelimiterHandler(new DefaultListDelimiterHandler(','));
                          new FileHandler(this).load(location);
                      }

                      Config(File file) throws ConfigurationException {
                          setListDelimiterHandler(new DefaultListDelimiterHandler(','));
                          if (file.exists()) {
                              new FileHandler(this).load(file);
                          }
                          setProperty("loaded", true);
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class Imports {

        @Test
        void unusedImportOfTypeRemovedIn2() {
            runInProject(
              configurationPom(true),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.ConfigurationFactory.ConfigurationBuilder;
                  import org.apache.commons.configuration.PropertiesConfiguration;

                  class WordCount {
                      PropertiesConfiguration configuration = new PropertiesConfiguration();
                  }
                  """,
                """
                  import org.apache.commons.configuration2.PropertiesConfiguration;

                  class WordCount {
                      PropertiesConfiguration configuration = new PropertiesConfiguration();
                  }
                  """
              )
            );
        }
    }

    @Nested
    class ListDelimiters {

        @Test
        void listDelimiter() {
            runInProject(
              configurationPom(false),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.BaseConfiguration;

                  class Settings {
                      void configure(BaseConfiguration config, boolean split) {
                          config.setListDelimiter(';');
                          config.setDelimiterParsingDisabled(split);
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.BaseConfiguration;
                  import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
                  import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;

                  class Settings {
                      void configure(BaseConfiguration config, boolean split) {
                          config.setListDelimiterHandler(new DefaultListDelimiterHandler(';'));
                          config.setListDelimiterHandler(split ? DisabledListDelimiterHandler.INSTANCE : new DefaultListDelimiterHandler(','));
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class Hierarchical {

        @Test
        void subConfigurationsAndCopies() {
            runInProject(
              configurationPom(true),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.HierarchicalConfiguration;
                  import org.apache.commons.configuration.XMLConfiguration;

                  import java.util.List;

                  class Mappings {
                      HierarchicalConfiguration read(XMLConfiguration config) {
                          List<HierarchicalConfiguration> mappings = config.configurationsAt("mappings");
                          for (HierarchicalConfiguration mapping : mappings) {
                              System.out.println(mapping.getString("name"));
                          }
                          return new HierarchicalConfiguration(config);
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
                  import org.apache.commons.configuration2.HierarchicalConfiguration;
                  import org.apache.commons.configuration2.XMLConfiguration;
                  import org.apache.commons.configuration2.tree.ImmutableNode;

                  import java.util.List;

                  class Mappings {
                      HierarchicalConfiguration<ImmutableNode> read(XMLConfiguration config) {
                          List<HierarchicalConfiguration<ImmutableNode>> mappings = config.configurationsAt("mappings");
                          for (HierarchicalConfiguration<ImmutableNode> mapping : mappings) {
                              System.out.println(mapping.getString("name"));
                          }
                          return new BaseHierarchicalConfiguration(config);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void subConfigurationsOfSubConfigurations() {
            runInProject(
              configurationPom(true),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.HierarchicalConfiguration;
                  import org.apache.commons.configuration.XMLConfiguration;

                  class Channels {
                      void read(XMLConfiguration config) {
                          for (final HierarchicalConfiguration channel : config.configurationsAt("channel")) {
                              for (final HierarchicalConfiguration connection : channel.configurationsAt("connection")) {
                                  System.out.println(connection.getString("host"));
                              }
                          }
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.HierarchicalConfiguration;
                  import org.apache.commons.configuration2.XMLConfiguration;
                  import org.apache.commons.configuration2.tree.ImmutableNode;

                  class Channels {
                      void read(XMLConfiguration config) {
                          for (final HierarchicalConfiguration<ImmutableNode> channel : config.configurationsAt("channel")) {
                              for (final HierarchicalConfiguration<ImmutableNode> connection : channel.configurationsAt("connection")) {
                                  System.out.println(connection.getString("host"));
                              }
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void subclass() {
            runInProject(
              configurationPom(false),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.HierarchicalConfiguration;

                  class Tree extends HierarchicalConfiguration {
                      Tree() {
                          addProperty("root", "value");
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.BaseHierarchicalConfiguration;

                  class Tree extends BaseHierarchicalConfiguration {
                      Tree() {
                          addProperty("root", "value");
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class Subclasses {

        @Test
        void overridesOfMethodsMadeFinal() {
            runInProject(
              configurationPom(false),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.AbstractConfiguration;

                  import java.util.HashMap;
                  import java.util.Iterator;
                  import java.util.Map;

                  class MapBackedConfiguration extends AbstractConfiguration {
                      private final Map<String, Object> values = new HashMap<>();

                      @Override
                      protected void addPropertyDirect(String key, Object value) {
                          values.put(key, value);
                      }

                      @Override
                      public boolean isEmpty() {
                          return values.isEmpty();
                      }

                      @Override
                      public boolean containsKey(String key) {
                          return values.containsKey(key);
                      }

                      @Override
                      public Object getProperty(String key) {
                          return values.get(key);
                      }

                      @Override
                      public Iterator<String> getKeys() {
                          return values.keySet().iterator();
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.AbstractConfiguration;

                  import java.util.HashMap;
                  import java.util.Iterator;
                  import java.util.Map;

                  class MapBackedConfiguration extends AbstractConfiguration {
                      private final Map<String, Object> values = new HashMap<>();

                      @Override
                      protected void addPropertyDirect(String key, Object value) {
                          values.put(key, value);
                      }

                      @Override
                      public boolean isEmptyInternal() {
                          return values.isEmpty();
                      }

                      @Override
                      public boolean containsKeyInternal(String key) {
                          return values.containsKey(key);
                      }

                      @Override
                      public Object getPropertyInternal(String key) {
                          return values.get(key);
                      }

                      @Override
                      public Iterator<String> getKeysInternal() {
                          return values.keySet().iterator();
                      }

                      @Override
                      protected void clearPropertyDirect(String key) {
                      }

                      @Override
                      protected boolean containsValueInternal(Object value) {
                          return contains(getKeys(), value);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void clearPropertyAndSize() {
            runInProject(
              configurationPom(false),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.BaseConfiguration;

                  import java.util.HashSet;
                  import java.util.Set;

                  class Tracking extends BaseConfiguration {
                      private final Set<String> cleared = new HashSet<>();

                      @Override
                      public void clearProperty(String key) {
                          cleared.add(key);
                          super.clearProperty(key);
                      }

                      public int size() {
                          return cleared.size();
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.BaseConfiguration;

                  import java.util.HashSet;
                  import java.util.Set;

                  class Tracking extends BaseConfiguration {
                      private final Set<String> cleared = new HashSet<>();

                      @Override
                      public void clearPropertyDirect(String key) {
                          cleared.add(key);
                          super.clearPropertyDirect(key);
                      }

                      public int sizeInternal() {
                          return cleared.size();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void superCallsOfRenamedOverrides() {
            runInProject(
              configurationPom(false),
              //language=java
              java(
                """
                  import org.apache.commons.configuration.BaseConfiguration;

                  class Defaults extends BaseConfiguration {
                      @Override
                      public Object getProperty(String key) {
                          Object value = super.getProperty(key);
                          return value != null ? value : super.getProperty("default." + key);
                      }
                  }
                  """,
                """
                  import org.apache.commons.configuration2.BaseConfiguration;

                  class Defaults extends BaseConfiguration {
                      @Override
                      public Object getPropertyInternal(String key) {
                          Object value = super.getPropertyInternal(key);
                          return value != null ? value : super.getPropertyInternal("default." + key);
                      }
                  }
                  """
              )
            );
        }
    }

    /**
     * The recipe that adds Configuration 2.x on its own, as only a model that isn't resolved again after the
     * migration changed the dependency, which happens where a dependency's pom can't be downloaded, shows whether it
     * would declare it twice.
     */
    @Nested
    class AddingConfiguration2 {

        @Test
        void moduleDeclaringConfiguration1IsLeftToChangeDependency() {
            rewriteRun(
              spec -> spec.recipeFromResources("org.openrewrite.apache.commons.configuration.AddCommonsConfiguration2Dependency"),
              mavenProject("example",
                unchangedConfigurationPom(),
                srcMainJava(
                  //language=java
                  java(
                    """
                      import org.apache.commons.configuration.BaseConfiguration;

                      class Settings {
                          BaseConfiguration settings = new BaseConfiguration();
                      }
                      """
                  )
                )
              )
            );
        }

        @Test
        void moduleGettingConfiguration1ThroughAnotherDependency() {
            rewriteRun(
              spec -> spec.recipeFromResources("org.openrewrite.apache.commons.configuration.AddCommonsConfiguration2Dependency"),
              mavenProject("example",
                pomXml(
                  //language=xml
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>org.example</groupId>
                        <artifactId>example</artifactId>
                        <version>1.0.0</version>
                        <dependencies>
                            <!-- Brings commons-configuration 1.8 -->
                            <dependency>
                                <groupId>com.netflix.archaius</groupId>
                                <artifactId>archaius-core</artifactId>
                                <version>0.7.6</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(pom -> {
                      assertThat(pom).containsPattern("<artifactId>commons-configuration2</artifactId>\\s*<version>2\\.\\d+\\.\\d+</version>");
                      return pom;
                  })
                ),
                srcMainJava(
                  //language=java
                  java(
                    """
                      import org.apache.commons.configuration.BaseConfiguration;

                      class Settings {
                          BaseConfiguration settings = new BaseConfiguration();
                      }
                      """
                  )
                )
              )
            );
        }
    }

    @Nested
    class SpringXml {

        /**
         * The package move renames the class of a bean. Constructor arguments are left as they are, so a bean that 1.x
         * constructed from a file has to be created with a builder by hand.
         */
        @Test
        void beanOfConfiguration1Class() {
            rewriteRun(
              mavenProject("example",
                configurationPom(false),
                //language=xml
                xml(
                  """
                    <beans xmlns="http://www.springframework.org/schema/beans"
                           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
                        <bean id="settings" class="org.apache.commons.configuration.PropertiesConfiguration"/>
                    </beans>
                    """,
                  """
                    <beans xmlns="http://www.springframework.org/schema/beans"
                           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
                        <bean id="settings" class="org.apache.commons.configuration2.PropertiesConfiguration"/>
                    </beans>
                    """,
                  spec -> spec.path("src/main/resources/beans.xml")
                )
              )
            );
        }
    }

    @Nested
    class Library {

        @Test
        void moduleCallingLibraryOnConfiguration1IsLeftAlone() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "commons-configuration-1", "commons-configuration2", "commons-lang-2")
                //language=java
                .dependsOn(
                  """
                    package org.example.graph;
                    public class GraphFactory {
                        public static Object open(org.apache.commons.configuration.Configuration configuration) {
                            return null;
                        }
                    }
                    """
                )),
              mavenProject("example",
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
                                <groupId>commons-configuration</groupId>
                                <artifactId>commons-configuration</artifactId>
                                <version>1.10</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """
                ),
                //language=java
                java(
                  """
                    import org.apache.commons.configuration.BaseConfiguration;
                    import org.example.graph.GraphFactory;

                    class Graphs {
                        Object open() {
                            BaseConfiguration configuration = new BaseConfiguration();
                            configuration.setProperty("storage.backend", "inmemory");
                            return GraphFactory.open(configuration);
                        }
                    }
                    """
                )
              )
            );
        }

        @Test
        void moduleWhoseConfigurationIsGivenToLibraryByAnotherModuleIsLeftAlone() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "commons-configuration-1", "commons-configuration2", "commons-lang-2")
                //language=java
                .dependsOn(
                  """
                    package org.example.graph;
                    public class GraphFactory {
                        public static Object open(org.apache.commons.configuration.Configuration configuration) {
                            return null;
                        }
                    }
                    """
                )),
              mavenProject("store",
                unchangedConfigurationPom(),
                srcMainJava(
                  //language=java
                  java(
                    """
                      package org.example.store;

                      import org.apache.commons.configuration.BaseConfiguration;

                      public class StoreConfiguration extends BaseConfiguration {
                      }
                      """
                  )
                )
              ),
              mavenProject("graph",
                srcMainJava(
                  //language=java
                  java(
                    """
                      import org.example.graph.GraphFactory;
                      import org.example.store.StoreConfiguration;

                      class Graphs {
                          Object open() {
                              return GraphFactory.open(new StoreConfiguration());
                          }
                      }
                      """
                  )
                )
              )
            );
        }

        @Test
        void moduleCallingMethodsOnWhatLibraryReturnsIsLeftAlone() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "commons-configuration-1", "commons-configuration2", "commons-lang-2")
                //language=java
                .dependsOn(
                  """
                    package org.example.config;
                    public class Config {
                        public static org.apache.commons.configuration.Configuration getInstance() {
                            return null;
                        }
                    }
                    """
                )),
              mavenProject("example",
                unchangedConfigurationPom(),
                srcMainJava(
                  //language=java
                  java(
                    """
                      import org.example.config.Config;

                      class Paths {
                          String apiPath() {
                              return Config.getInstance().getString("api.path", "api");
                          }
                      }
                      """
                  )
                )
              )
            );
        }

        @Test
        void moduleCatchingWhatLibraryThrowsIsLeftAlone() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "commons-configuration-1", "commons-configuration2", "commons-lang-2")
                //language=java
                .dependsOn(
                  """
                    package org.example.pool;
                    public class ClusterPool {
                        public static Object open(String path) throws org.apache.commons.configuration.ConfigurationException {
                            return null;
                        }
                    }
                    """
                )),
              mavenProject("example",
                unchangedConfigurationPom(),
                srcMainJava(
                  //language=java
                  java(
                    """
                      import org.apache.commons.configuration.ConfigurationException;
                      import org.example.pool.ClusterPool;

                      class Clusters {
                          Object open(String path) {
                              try {
                                  return ClusterPool.open(path);
                              } catch (ConfigurationException e) {
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
    }

    /**
     * Configuration 2 needs Commons Lang 3, Text and IO as recent as the ones it declares, and fails at runtime with an
     * older one.
     */
    @Nested
    class CommonsDependencies {

        /**
         * The minor version of the artifact the pom declares or manages, which must be at least the one that
         * commons-configuration2 2.15.1 declares.
         */
        private static int minorVersion(String pom, String artifactId) {
            Matcher version = Pattern.compile("<artifactId>" + artifactId + "</artifactId>\\s*<version>\\d+\\.(\\d+)(\\.\\d+)?</version>").matcher(pom);
            assertThat(version.find()).describedAs("Expected a version of %s in %s", artifactId, pom).isTrue();
            return Integer.parseInt(version.group(1));
        }

        @Test
        void olderVersionFromDependencyDeclaredFirst() {
            rewriteRun(
              mavenProject("example",
                pomXml(
                  //language=xml
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>org.example</groupId>
                        <artifactId>example</artifactId>
                        <version>1.0.0</version>
                        <dependencies>
                            <!-- Brings commons-lang3 3.5, which Maven takes over the one commons-configuration2 brings -->
                            <dependency>
                                <groupId>org.apache.velocity</groupId>
                                <artifactId>velocity-engine-core</artifactId>
                                <version>2.0</version>
                            </dependency>
                            <dependency>
                                <groupId>commons-configuration</groupId>
                                <artifactId>commons-configuration</artifactId>
                                <version>1.10</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(pom -> {
                      assertThat(pom).contains("<dependencyManagement>");
                      assertThat(minorVersion(pom, "commons-lang3")).isGreaterThanOrEqualTo(20);
                      return pom;
                  })
                )
              )
            );
        }

        @Test
        void olderVersionDeclared() {
            rewriteRun(
              mavenProject("example",
                pomXml(
                  //language=xml
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>org.example</groupId>
                        <artifactId>example</artifactId>
                        <version>1.0.0</version>
                        <dependencies>
                            <dependency>
                                <groupId>commons-configuration</groupId>
                                <artifactId>commons-configuration</artifactId>
                                <version>1.10</version>
                            </dependency>
                            <dependency>
                                <groupId>org.apache.commons</groupId>
                                <artifactId>commons-lang3</artifactId>
                                <version>3.17.0</version>
                            </dependency>
                            <dependency>
                                <groupId>commons-io</groupId>
                                <artifactId>commons-io</artifactId>
                                <version>2.14.0</version>
                            </dependency>
                            <dependency>
                                <groupId>commons-beanutils</groupId>
                                <artifactId>commons-beanutils</artifactId>
                                <version>1.8.3</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(pom -> {
                      assertThat(pom).doesNotContain("<dependencyManagement>");
                      assertThat(minorVersion(pom, "commons-lang3")).isGreaterThanOrEqualTo(20);
                      assertThat(minorVersion(pom, "commons-io")).isGreaterThanOrEqualTo(22);
                      assertThat(minorVersion(pom, "commons-beanutils")).isGreaterThanOrEqualTo(11);
                      return pom;
                  })
                )
              )
            );
        }

        @Test
        void olderVersionManagedBySpringBoot() {
            rewriteRun(
              mavenProject("example",
                pomXml(
                  //language=xml
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>2.1.6.RELEASE</version>
                        </parent>
                        <groupId>org.example</groupId>
                        <artifactId>example</artifactId>
                        <version>1.0.0</version>
                        <dependencies>
                            <dependency>
                                <groupId>commons-configuration</groupId>
                                <artifactId>commons-configuration</artifactId>
                                <version>1.10</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(pom -> {
                      assertThat(pom).contains("<dependencyManagement>");
                      assertThat(minorVersion(pom, "commons-lang3")).isGreaterThanOrEqualTo(20);
                      return pom;
                  })
                )
              )
            );
        }

        /**
         * A module that gets Configuration 1.x through a library stays on it, and doesn't need what Configuration 2
         * needs.
         */
        @Test
        void moduleOnConfiguration1ThroughLibraryIsLeftAlone() {
            rewriteRun(
              mavenProject("store",
                pomXml(
                  //language=xml
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>org.example</groupId>
                        <artifactId>store</artifactId>
                        <version>1.0.0</version>
                        <dependencies>
                            <dependency>
                                <groupId>commons-configuration</groupId>
                                <artifactId>commons-configuration</artifactId>
                                <version>1.10</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(pom -> {
                      assertThat(pom).contains("<artifactId>commons-configuration2</artifactId>");
                      return pom;
                  })
                )
              ),
              mavenProject("metrics",
                pomXml(
                  //language=xml
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>org.example</groupId>
                        <artifactId>metrics</artifactId>
                        <version>1.0.0</version>
                        <dependencies>
                            <!-- Brings commons-configuration 1.8 -->
                            <dependency>
                                <groupId>com.netflix.archaius</groupId>
                                <artifactId>archaius-core</artifactId>
                                <version>0.7.6</version>
                            </dependency>
                            <!-- Brings commons-lang3 3.5 -->
                            <dependency>
                                <groupId>org.apache.velocity</groupId>
                                <artifactId>velocity-engine-core</artifactId>
                                <version>2.0</version>
                            </dependency>
                            <dependency>
                                <groupId>org.apache.commons</groupId>
                                <artifactId>commons-lang3</artifactId>
                                <version>3.17.0</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """
                )
              )
            );
        }

        /**
         * Another module may be on Configuration 2.x already, through a library that brings an older release of it.
         */
        @Test
        void moduleWithoutConfiguration1IsLeftAlone() {
            rewriteRun(
              mavenProject("store",
                pomXml(
                  //language=xml
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>org.example</groupId>
                        <artifactId>store</artifactId>
                        <version>1.0.0</version>
                        <dependencies>
                            <dependency>
                                <groupId>commons-configuration</groupId>
                                <artifactId>commons-configuration</artifactId>
                                <version>1.10</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(pom -> {
                      assertThat(pom).contains("<artifactId>commons-configuration2</artifactId>");
                      return pom;
                  })
                )
              ),
              mavenProject("security",
                pomXml(
                  //language=xml
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>org.example</groupId>
                        <artifactId>security</artifactId>
                        <version>1.0.0</version>
                        <dependencies>
                            <!-- Brings commons-text 1.9 -->
                            <dependency>
                                <groupId>org.apache.commons</groupId>
                                <artifactId>commons-configuration2</artifactId>
                                <version>2.8.0</version>
                            </dependency>
                            <dependency>
                                <groupId>org.apache.velocity</groupId>
                                <artifactId>velocity-engine-core</artifactId>
                                <version>2.0</version>
                            </dependency>
                            <dependency>
                                <groupId>org.apache.commons</groupId>
                                <artifactId>commons-lang3</artifactId>
                                <version>3.17.0</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """
                )
              )
            );
        }
    }
}
