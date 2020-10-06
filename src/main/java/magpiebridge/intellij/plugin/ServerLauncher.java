package magpiebridge.intellij.plugin;

import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import lsp4intellij.MyRawCommandServerDefinition;
import lsp4intellij.SocketServerDefinition;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

public class ServerLauncher {

  static void stop(Project project) {
    final IntellijLanguageClient service = ServiceManager.getService(IntellijLanguageClient.class);
    service.getAllServerWrappersFor(project.getBasePath()).forEach(w -> w.stop(false));
  }

  static void start(Project project) {

    PropertiesComponent pc = PropertiesComponent.getInstance();
    if (!pc.isValueSet(Configuration.COMMANDOPTION)) {
      return;
    }

    String lspLanguageExtensionPattern = pc.getValue(Configuration.FILEPATTERN, ".*");
    boolean commandOptionOne = pc.getBoolean(Configuration.COMMANDOPTION);
    LanguageServerDefinition serverDefinition = null;
    if (pc.getBoolean(Configuration.CHANNEL)) {
      String currentPath = System.getenv("PATH");
      String path = pc.getValue(Configuration.PATH, "");
      String newPath = currentPath + File.pathSeparator + path;

      String jarPath = pc.getValue(Configuration.JAR, "");
      String cpPath = pc.getValue(Configuration.CP, "");
      String mainClass = pc.getValue(Configuration.MAIN, "");
      String extraArgs = pc.getValue(Configuration.ARGS, "");
      String dir = pc.getValue(Configuration.DIR, "");
      String jvm = pc.getValue(Configuration.JVM, "");

      List<String> args = new ArrayList<>();
      args.add(jvm);

      if (commandOptionOne && !jarPath.isEmpty()) {

        args.add("-jar");
        args.add(jarPath);

        final File file = new File(jarPath);
        if (!file.exists()) {
          Notifications.Bus.notify(
                  new Notification(
                          "lsp",
                          "Lsp Plugin Error",
                          "The given Language Server Jar \"" + jarPath + "\" is missing.",
                          NotificationType.ERROR),
                  project);
          return;
        }
      } else if (!commandOptionOne && !cpPath.isEmpty()) {
        args.add("-cp");
        args.add(cpPath);

        if (!mainClass.isEmpty()) {
          args.add(mainClass);
        }

      } else {
        // no path to a LSP Server is configured
        return;
      }

      StringTokenizer toks = new StringTokenizer(extraArgs);
      while (toks.hasMoreTokens()) {
        args.add(toks.nextToken());
      }

      serverDefinition = new MyRawCommandServerDefinition(lspLanguageExtensionPattern, args.toArray(new String[0]), newPath);
    } else {
      String host = pc.getValue(Configuration.HOST, "");
      int port = pc.getInt(Configuration.PORT, 0);
      if (host.isEmpty()) {
        return;
      }
      if (port == 0) {
        return;
      }

      // TODO: assumes currently that a server instance is already running! -> dev mode ;)
      serverDefinition = new SocketServerDefinition(lspLanguageExtensionPattern, host, port);
    }

    final IntellijLanguageClient service = ServiceManager.getService(IntellijLanguageClient.class);
    service.addServerDefinition(serverDefinition, project);

    // TODO: [ms] check
    // assign custom configuration entries
    final @Nullable String[] custom_config_keys = pc.getValues("CUSTOM_CONFIG_KEYS");
    final @Nullable String[] custom_config_values = pc.getValues("CUSTOM_CONFIG_VALUES");

    final HashMap<String, String> map = new HashMap<>();
    if (custom_config_keys != null && custom_config_values != null) {
      for (int i = 0; i < custom_config_keys.length; i++) {
        map.put(custom_config_keys[i], custom_config_values[i]);
      }
    }
    service.setConfigParams(Lists.newArrayList(map.entrySet()));
    service.init();
    service.initProjectConnections(project);
  }


}
