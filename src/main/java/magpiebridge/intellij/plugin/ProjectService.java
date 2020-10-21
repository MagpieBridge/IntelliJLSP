package magpiebridge.intellij.plugin;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import lsp4intellij.diagnosticsview.DiagnosticsViewPanel;
import org.jetbrains.annotations.NotNull;

@Service
public final class ProjectService implements Disposable {

  private Project project;
  private DiagnosticsViewPanel diagnosticsViewPanel;

  public ProjectService() {
  }

  public void setProject(@NotNull Project myProject) {
    project = myProject;
    diagnosticsViewPanel = new DiagnosticsViewPanel(myProject);
  }


  void startServerConnection() {
    ServerLauncher.start(project);
  }

  void stopServerConnection() {
    ServerLauncher.stop(project);
  }

  void restartServerConnection() {
    stopServerConnection();
    startServerConnection();
  }

  @Override
  public void dispose() {
    stopServerConnection();
  }

  public Project getProject() {
    return project;
  }

  public DiagnosticsViewPanel getDiagnosticsViewPanel() {
    return diagnosticsViewPanel;
  }
}