package org.magpiebridge.intellij.test;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.magpiebridge.intellij.plugin.Service;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;

public class TestRemoteServer extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        try {
            Project p = e.getProject();

            PipedInputStream clientIn = new PipedInputStream();
            PipedOutputStream serverOut = new PipedOutputStream(clientIn);

            PipedInputStream serverIn = new PipedInputStream(32767);
            PipedOutputStream clientOut = new PipedOutputStream(serverIn);

            EchoServer es = new EchoServer();
            Launcher<LanguageClient> clientLauncher =
                    LSPLauncher.createServerLauncher(es, serverIn, serverOut);
            es.connect(clientLauncher.getRemoteProxy());
            clientLauncher.startListening();

            org.magpiebridge.intellij.client.LanguageClient client =
                    new org.magpiebridge.intellij.client.LanguageClient(p, es);
            Launcher<LanguageServer> serverLauncher =
                    LSPLauncher.createClientLauncher(client, clientIn, clientOut);
            serverLauncher.startListening();

            new Service(p, serverLauncher.getRemoteProxy(), clientLauncher.getRemoteProxy());
        } catch (IOException ee) {
            assert false : ee.getMessage();
        }
    }
}
