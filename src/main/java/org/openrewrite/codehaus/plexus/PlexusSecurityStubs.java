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

/**
 * The API of {@code org.codehaus.plexus:plexus-cipher:2.0} and {@code org.codehaus.plexus:plexus-sec-dispatcher:2.0},
 * which keep the {@code org.sonatype.plexus.components} packages of the Sonatype artifacts they replace, for the
 * templates that write code against it.
 */
final class PlexusSecurityStubs {

    private PlexusSecurityStubs() {
    }

    static final String[] CODEHAUS_2_0 = {
            //language=java
            "package org.sonatype.plexus.components.cipher;\n" +
            "public class PlexusCipherException extends Exception {\n" +
            "    public PlexusCipherException() {}\n" +
            "    public PlexusCipherException(String message) {}\n" +
            "    public PlexusCipherException(Throwable cause) {}\n" +
            "    public PlexusCipherException(String message, Throwable cause) {}\n" +
            "}",
            //language=java
            "package org.sonatype.plexus.components.cipher;\n" +
            "public interface PlexusCipher {\n" +
            "    char ENCRYPTED_STRING_DECORATION_START = '{';\n" +
            "    char ENCRYPTED_STRING_DECORATION_STOP = '}';\n" +
            "    String encrypt(String str, String passPhrase) throws PlexusCipherException;\n" +
            "    String encryptAndDecorate(String str, String passPhrase) throws PlexusCipherException;\n" +
            "    String decrypt(String str, String passPhrase) throws PlexusCipherException;\n" +
            "    String decryptDecorated(String str, String passPhrase) throws PlexusCipherException;\n" +
            "    boolean isEncryptedString(String str);\n" +
            "    String unDecorate(String str) throws PlexusCipherException;\n" +
            "    String decorate(String str);\n" +
            "}",
            //language=java
            "package org.sonatype.plexus.components.cipher;\n" +
            "public class DefaultPlexusCipher implements PlexusCipher {\n" +
            "    public DefaultPlexusCipher() {}\n" +
            "    public String encrypt(String str, String passPhrase) throws PlexusCipherException { return null; }\n" +
            "    public String encryptAndDecorate(String str, String passPhrase) throws PlexusCipherException { return null; }\n" +
            "    public String decrypt(String str, String passPhrase) throws PlexusCipherException { return null; }\n" +
            "    public String decryptDecorated(String str, String passPhrase) throws PlexusCipherException { return null; }\n" +
            "    public boolean isEncryptedString(String str) { return false; }\n" +
            "    public String unDecorate(String str) throws PlexusCipherException { return null; }\n" +
            "    public String decorate(String str) { return null; }\n" +
            "    public static String[] getServiceTypes() { return null; }\n" +
            "    public static String[] getCryptoImpls(String serviceType) { return null; }\n" +
            "    public static void main(String[] args) {}\n" +
            "}",
            //language=java
            "package org.sonatype.plexus.components.cipher;\n" +
            "public class PBECipher {\n" +
            "    public String encrypt64(String clearText, String password) throws PlexusCipherException { return null; }\n" +
            "    public String decrypt64(String encryptedText, String password) throws PlexusCipherException { return null; }\n" +
            "}",
            //language=java
            "package org.sonatype.plexus.components.sec.dispatcher;\n" +
            "public class SecDispatcherException extends Exception {\n" +
            "    public SecDispatcherException(String message) {}\n" +
            "    public SecDispatcherException(Throwable cause) {}\n" +
            "    public SecDispatcherException(String message, Throwable cause) {}\n" +
            "}",
            //language=java
            "package org.sonatype.plexus.components.sec.dispatcher;\n" +
            "public interface SecDispatcher {\n" +
            "    String[] SYSTEM_PROPERTY_MASTER_PASSWORD = new String[]{\"settings.master.password\", \"settings-master-password\"};\n" +
            "    String[] SYSTEM_PROPERTY_SERVER_PASSWORD = new String[]{\"settings.server.password\", \"settings-server-password\"};\n" +
            "    String decrypt(String str) throws SecDispatcherException;\n" +
            "}",
            //language=java
            "package org.sonatype.plexus.components.sec.dispatcher;\n" +
            "import java.util.Map;\n" +
            "public interface PasswordDecryptor {\n" +
            "    String decrypt(String str, Map attributes, Map config) throws SecDispatcherException;\n" +
            "}",
            //language=java
            "package org.sonatype.plexus.components.sec.dispatcher;\n" +
            "import java.util.Map;\n" +
            "import org.sonatype.plexus.components.cipher.PlexusCipher;\n" +
            "public class DefaultSecDispatcher implements SecDispatcher {\n" +
            "    public static final String SYSTEM_PROPERTY_SEC_LOCATION = \"settings.security\";\n" +
            "    public static final String TYPE_ATTR = \"type\";\n" +
            "    public static final char ATTR_START = '[';\n" +
            "    public static final char ATTR_STOP = ']';\n" +
            "    protected final PlexusCipher _cipher;\n" +
            "    protected final Map<String, PasswordDecryptor> _decryptors;\n" +
            "    protected String _configurationFile;\n" +
            "    public DefaultSecDispatcher(PlexusCipher _cipher, Map<String, PasswordDecryptor> _decryptors, String _configurationFile) {\n" +
            "        this._cipher = _cipher;\n" +
            "        this._decryptors = _decryptors;\n" +
            "    }\n" +
            "    public DefaultSecDispatcher(PlexusCipher _cipher) {\n" +
            "        this(_cipher, null, null);\n" +
            "    }\n" +
            "    public String decrypt(String str) throws SecDispatcherException { return null; }\n" +
            "    public String getConfigurationFile() { return null; }\n" +
            "    public void setConfigurationFile(String file) {}\n" +
            "    public static void main(String[] args) throws Exception {}\n" +
            "}"
    };
}
