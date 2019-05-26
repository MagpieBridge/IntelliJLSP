package org.magpiebridge.intellij.test;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.magpiebridge.intellij.plugin.Launcher;

import java.io.IOException;

public class TestConfiguredServer extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        try {
            Launcher.launch(e.getProject());
       } catch (IOException exc) {
            assert false : exc.getMessage();
        }
    }
}
