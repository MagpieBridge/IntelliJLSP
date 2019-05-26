package org.magpiebridge.intellij.test;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.magpiebridge.intellij.client.LanguageClient;
import org.magpiebridge.intellij.plugin.Service;

public class TestServer extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project p = e.getProject();
        EchoServer es = new EchoServer();
        new Service(p, es, new LanguageClient(p, es));
    }
}
