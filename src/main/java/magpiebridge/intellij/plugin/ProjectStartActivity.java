package magpiebridge.intellij.plugin;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.IntellijLanguageClient;

public class ProjectStartActivity implements StartupActivity, DumbAware {

  @Override
  public void runActivity(@NotNull Project project) {
    ServiceManager.getService(ProjectService.class).setProject(project);
    ServerLauncher.start(project);
  }

}