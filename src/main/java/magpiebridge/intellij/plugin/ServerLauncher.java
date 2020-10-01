package magpiebridge.intellij.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import lsp4intellij.MyRawCommandServerDefinition;
import lsp4intellij.SocketServerDefinition;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.RawCommandServerDefinition;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class ServerLauncher {


  static void stop(Project project) {

  }


  static void start(Project project) {

    PropertiesComponent pc = PropertiesComponent.getInstance();
    if (!pc.isValueSet(Configuration.COMMANDOPTION)) {
      return;
    }

    // TODO: [ms] generify (other default) ;)
    String lspLanugageExtensionPattern = "jimple";// pc.getValue(Configuration.EXTENSION_PATTERN, "jimple");

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

      serverDefinition = new MyRawCommandServerDefinition(lspLanugageExtensionPattern, args.toArray(new String[0]), newPath);
    } else {
      String host = pc.getValue(Configuration.HOST, "");
      int port = pc.getInt(Configuration.PORT, 0);
      if (host.isEmpty()) {
        return;
      }
      if (port == 0) {
        return;
      }

      // TODO: assumes currently that a server instance is already running!
      serverDefinition = new SocketServerDefinition(lspLanugageExtensionPattern, host, port);
    }

    IntellijLanguageClient.addServerDefinition(serverDefinition, project);
  }


}
