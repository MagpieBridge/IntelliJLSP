package magpiebridge.intellij.test;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import magpiebridge.intellij.plugin.ServerLauncher;

import java.io.IOException;

public class TestConfiguredServer extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        ServerLauncher.launch(e.getProject());
    }
}
