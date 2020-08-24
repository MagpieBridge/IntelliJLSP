package magpiebridge.intellij.plugin;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

public class ProjectStart implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    ProjectLSPService plsp = project.getService(ProjectLSPService.class);
    plsp.runActivity(project);
  }
}
