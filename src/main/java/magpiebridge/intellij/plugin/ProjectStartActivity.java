package magpiebridge.intellij.plugin;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public class ProjectStartActivity implements StartupActivity, DumbAware {

  @Override
  public void runActivity(@NotNull Project project) {
    project.getService(ProjectService.class).setProject(project);
    ServerLauncher.start(project);

  }

}