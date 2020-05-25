package magpiebridge.intellij.plugin;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Define a project-level LSP service.
 */
public class ProjectLSPService implements ProjectComponent {
    private final Project project;

    public ProjectLSPService(Project project) {
        this.project = project;
    }

    public static ProjectLSPService getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, ProjectLSPService.class);
    }

    @Override
    public void projectOpened() {
        ServerLauncher.launch(project);
    }

    @Override
    public void projectClosed() {
        ServerLauncher.closeProject(project);
    }
}
