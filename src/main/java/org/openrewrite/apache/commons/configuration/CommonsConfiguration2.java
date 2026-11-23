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

import org.jspecify.annotations.Nullable;
import org.openrewrite.java.tree.JavaType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

/**
 * Names of the Configuration 2 API, and of the 1.x API as the package move renames it.
 */
final class CommonsConfiguration2 {

    static final String PACKAGE = "org.apache.commons.configuration2.";

    static final String BUILDER = PACKAGE + "builder.FileBasedConfigurationBuilder";
    static final String PARAMETERS = PACKAGE + "builder.fluent.Parameters";
    static final String DEFAULT_LIST_DELIMITER_HANDLER = PACKAGE + "convert.DefaultListDelimiterHandler";
    static final String DISABLED_LIST_DELIMITER_HANDLER = PACKAGE + "convert.DisabledListDelimiterHandler";
    static final String FILE_HANDLER = PACKAGE + "io.FileHandler";
    static final String FILE_BASED = PACKAGE + "io.FileBased";

    /**
     * The file-based configurations whose 1.x constructors loaded a file, with the method of {@code Parameters} that
     * creates their builder parameters.
     */
    static final Map<String, String> PARAMETERS_BY_CLASS;

    /**
     * The builder parameter that sets the location 1.x constructors took, by its type.
     */
    static final Map<String, String> LOCATION_SETTERS;

    /**
     * The 1.x types that declared methods to load, save and locate a configuration.
     */
    static final Set<String> FILE_CONFIGURATION_TYPES;

    /**
     * Methods of 1.x file-based configurations that a {@code FileHandler} has, by the same name and parameters.
     */
    static final Set<String> FILE_HANDLER_OPERATIONS = unmodifiableSet(new HashSet<>(Arrays.asList(
            "load", "save",
            "getFileName", "setFileName", "getFile", "setFile", "getURL", "setURL", "getPath", "setPath",
            "getBasePath", "setBasePath", "getEncoding", "setEncoding", "getFileSystem", "setFileSystem")));

    /**
     * Methods of 1.x file-based configurations that only a {@code FileBasedConfigurationBuilder} has.
     */
    static final Set<String> BUILDER_OPERATIONS = unmodifiableSet(new HashSet<>(Arrays.asList("setAutoSave")));

    /**
     * {@code AbstractConfiguration} and the 1.x classes that extend it, by their names in the package. The package move
     * gives the supertype of a class it renames no supertypes of its own, so a class's hierarchy has to be recognized
     * by these names.
     */
    static final Set<String> ABSTRACT_CONFIGURATION_TYPES = unmodifiableSet(new HashSet<>(Arrays.asList(
            "AbstractConfiguration",
            "AbstractFileConfiguration",
            "AbstractHierarchicalFileConfiguration",
            "BaseConfiguration",
            "CombinedConfiguration",
            "CompositeConfiguration",
            "DataConfiguration",
            "DatabaseConfiguration",
            "DefaultConfigurationBuilder",
            "DynamicCombinedConfiguration",
            "EnvironmentConfiguration",
            "HierarchicalConfiguration",
            "HierarchicalINIConfiguration",
            "HierarchicalReloadableConfiguration",
            "HierarchicalXMLConfiguration",
            "INIConfiguration",
            "JNDIConfiguration",
            "MapConfiguration",
            "MultiFileHierarchicalConfiguration",
            "PatternSubtreeConfigurationWrapper",
            "PropertiesConfiguration",
            "SubnodeConfiguration",
            "SubsetConfiguration",
            "SystemConfiguration",
            "XMLConfiguration",
            "XMLPropertiesConfiguration",
            "plist.PropertyListConfiguration",
            "plist.XMLPropertyListConfiguration",
            "web.AppletConfiguration",
            "web.ServletConfiguration",
            "web.ServletContextConfiguration",
            "web.ServletFilterConfiguration",
            "web.ServletRequestConfiguration")));

    static final Set<String> DELIMITER_METHODS = unmodifiableSet(new HashSet<>(Arrays.asList(
            "setDelimiterParsingDisabled", "setListDelimiter", "setListDelimiterHandler")));

    static {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(PACKAGE + "PropertiesConfiguration", "properties");
        parameters.put(PACKAGE + "XMLConfiguration", "xml");
        parameters.put(PACKAGE + "INIConfiguration", "ini");
        parameters.put(PACKAGE + "XMLPropertiesConfiguration", "fileBased");
        PARAMETERS_BY_CLASS = unmodifiableMap(parameters);

        Map<String, String> setters = new HashMap<>();
        setters.put("java.lang.String", "setFileName");
        setters.put("java.io.File", "setFile");
        setters.put("java.net.URL", "setURL");
        LOCATION_SETTERS = unmodifiableMap(setters);

        Set<String> fileConfigurations = new HashSet<>(parameters.keySet());
        fileConfigurations.add(PACKAGE + "FileConfiguration");
        fileConfigurations.add(PACKAGE + "AbstractFileConfiguration");
        fileConfigurations.add(PACKAGE + "AbstractHierarchicalFileConfiguration");
        FILE_CONFIGURATION_TYPES = unmodifiableSet(fileConfigurations);
    }

    /**
     * Whether the type is or extends {@code AbstractConfiguration}, by its 1.x name or by the name the package move
     * gave it.
     */
    static boolean extendsAbstractConfiguration(JavaType.@Nullable FullyQualified type) {
        for (JavaType.FullyQualified t = type; t != null; t = t.getSupertype()) {
            String name = t.getFullyQualifiedName();
            for (String prefix : new String[]{PACKAGE, "org.apache.commons.configuration."}) {
                if (name.startsWith(prefix) && ABSTRACT_CONFIGURATION_TYPES.contains(name.substring(prefix.length()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private CommonsConfiguration2() {
    }
}
