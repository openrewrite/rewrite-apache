package org.openrewrite.apache.httpclient5;

import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.apache.hc.core5.reactor.Connecting
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.openrewrite.java.template.RecipeDescriptor;

public class MigrateConnectingIOReactor {
    @RecipeDescriptor(
            name = "ConnectingIOReactor for httpcore5",
            description = "Updates usage of DefaultConnectingIOReactor to swap parameters from `execute(..)` to construction."
    )
    public static class ConnectingIOReactorInvocations {
        @BeforeTemplate
        void beforeTemplate(IOEventHandlerFactory factory) {
            ConnectingIOReactor
        }
    }
}
