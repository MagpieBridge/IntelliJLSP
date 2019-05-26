package org.magpiebridge.intellij.plugin;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.services.LanguageServer;
import org.magpiebridge.intellij.test.EchoServer;

public class State {
    public static State getInstance() {
        return ServiceManager.getService(State.class);
    }

    public LanguageServer getServer(Project project) {
        EchoServer server = new EchoServer();
        server.connect(new org.magpiebridge.intellij.client.LanguageClient(project, server));
        return server;
    }
}
