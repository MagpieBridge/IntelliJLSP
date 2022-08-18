package magpiebridge.intellij.plugin;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Define a project-level LSP service. */
public class ProjectLSPService implements StartupActivity, Disposable {
  private Project project;

  @Override
  public void dispose() {
    ServerLauncher.closeProject(project);
  }

  @Override
  public void runActivity(@NotNull Project project) {
    this.project = project;
    Disposer.register(project, this);

    try {
      ServerLauncher.launch(project);
    } catch (Exception | Error e) {
      StringWriter errors = new StringWriter();
      e.printStackTrace(new PrintWriter(errors));
      Notifications.Bus.notify(
              new Notification(
                      "lsp",
                      "Error",
                              "Could not launch language server:\n" + errors.toString(),
                      NotificationType.ERROR),
              project);
    }
  }
}
