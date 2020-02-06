package org.magpiebridge.intellij.plugin;

import com.intellij.codeInsight.daemon.impl.quickfix.ReplacePrimitiveWithBoxedTypeAction;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class Trigger implements ProjectComponent {
    private final Project project;

    public Trigger(Project project) {
        this.project = project;
    }

    public static Trigger getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, Trigger.class);
    }

    @Override
    public void projectOpened() {
        try {
            Launcher.launch(project);
        } catch (IOException e) {
            assert false : e;
        }
    }

    @Override
    public void projectClosed() {
        Launcher.shutDown(project);
    }
}
