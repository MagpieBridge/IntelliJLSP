package magpiebridge.intellij.plugin;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Define a project-level LSP service.
 */
@Service
public final class ProjectLSPService implements StartupActivity, Disposable {
    private Project project;

    @Override
    public void dispose() {
        ServerLauncher.closeProject(project);
    }

    @Override
    public void runActivity(@NotNull Project project) {
        this.project = project;
        Disposer.register(project, this);
        ServerLauncher.launch(project);
    }
}
