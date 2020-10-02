package lsp4intellij.extension;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import magpiebridge.intellij.plugin.ProjectService;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.client.ClientContext;
import org.wso2.lsp4intellij.client.DefaultLanguageClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MyLanguageClient extends DefaultLanguageClient {

  final Logger log = Logger.getInstance(getClass());

  public MyLanguageClient(ClientContext context) {
    super(context);
  }

  @Override
  public void showMessage(@NotNull MessageParams messageParams) {
    // showMessage(messageParams.getType() + ": " + messageParams.getMessage());
    NotificationType type;
    switch (messageParams.getType()) {
      case Warning:
        type = NotificationType.WARNING;
        break;
      case Error:
        type = NotificationType.ERROR;
        break;
      case Log:
      case Info:
      default:
        type = NotificationType.INFORMATION;
        break;
    }

    Notifications.Bus.notify(
            new Notification(
                    "lsp", messageParams.getType().toString(), messageParams.getMessage(), type),
            ServiceManager.getService(ProjectService.class).getProject());
  }


  @Override
  public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
    PropertiesComponent pc = PropertiesComponent.getInstance();
    final HashMap<String, String> map = new HashMap<>();
    final @Nullable String[] custom_config_keys = pc.getValues("CUSTOM_CONFIG_KEYS");
    final @Nullable String[] custom_config_values = pc.getValues("CUSTOM_CONFIG_VALUES");
    if (custom_config_keys == null || custom_config_values == null) {
      return null;
    }

    for (int i = 0; i < custom_config_keys.length; i++) {
      map.put(custom_config_keys[i], custom_config_values[i]);
    }

    final List<ConfigurationItem> items = configurationParams.getItems();
    List<Object> configItems = new ArrayList<>(items.size());
    for (ConfigurationItem item : items) {
      // TODO: respect: "scopeUri?: DocumentUri;"
      final String found = map.get(item.getSection());
      if (found != null) {
        configItems.add(found);
      }
    }

    return CompletableFuture.completedFuture(configItems);
  }

  @Override
  public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
    @NotNull Project project = ServiceManager.getService(ProjectService.class).getProject();
    final @NotNull Module[] modules = ModuleManager.getInstance(project).getModules();
    List<WorkspaceFolder> folders = new ArrayList<>(modules.length + 1);
    for (Module module : modules) {
      folders.add(new WorkspaceFolder(ModuleUtil.getModuleDirPath(module), module.getName()));
    }
    WorkspaceFolder root = new WorkspaceFolder(project.getBasePath(), "root");
    folders.add(root);
    return CompletableFuture.completedFuture(folders);
  }

  @Override
  public void telemetryEvent(Object o) {
    log.info(o.toString());
  }

}
