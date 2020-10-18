package magpiebridge.intellij.plugin;

import com.google.common.collect.Lists;
import com.intellij.analysis.problemsView.toolWindow.ProblemsView;
import com.intellij.icons.AllIcons;
import com.intellij.ide.errorTreeView.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import lsp4intellij.LSPNavigatable;
import lsp4intellij.MyRawCommandServerDefinition;
import lsp4intellij.SocketServerDefinition;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import org.wso2.lsp4intellij.utils.FileUtils;

import javax.annotation.Nullable;
import javax.swing.*;
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
    service.addExtensionManager(serverDefinition.ext, new MagpieLSPExtensionManager());


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


    // TESTING AREA

    final Diagnostic diagnostic = new Diagnostic();
    diagnostic.setMessage("This is a Jimple Error. ");
    diagnostic.setRange(new Range(new Position(2, 0), new Position(2, 10)));
    diagnostic.setSource("Jimpleparser");
    diagnostic.setSeverity(DiagnosticSeverity.Error);

    List<DiagnosticRelatedInformation> related = new ArrayList<>();
    related.add(new DiagnosticRelatedInformation(new Location("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/src/invalid.jimple", new Range(new Position(4, 4), new Position(4, 10))), "problem A"));
    related.add(new DiagnosticRelatedInformation(new Location("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/src/invalid.jimple", new Range(new Position(6, 6), new Position(6, 12))), "problem B"));
    related.add(new DiagnosticRelatedInformation(new Location("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/src/invalid.jimple", new Range(new Position(8, 8), new Position(8, 14))), "problem C"));
    diagnostic.setRelatedInformation(related);


    ApplicationManager.getApplication().invokeLater(() -> {
      final ContentManager contentManager = ProblemsView.getToolWindow(project).getContentManager();
      final Content selectedContent = contentManager.getSelectedContent();

      final LSPProblemsViewPanel component = new LSPProblemsViewPanel(project);
      // TODO: add all diagnostics of that file
      component.addDiagnostic(diagnostic, "file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/src/helloworld.jimple");
      component.expandAll();

      final Content content = contentManager.getFactory().createContent(component, "Diagnostics", false);
      selectedContent.getManager().addContent(content);
      selectedContent.getManager().setSelectedContent(content, true, true);

    });

  }


  public static final class LSPProblemsViewPanel extends NewErrorTreeViewPanel {
    public LSPProblemsViewPanel(Project project) {
      super(project, null);
    }

    @Override
    protected boolean canHideWarnings() {
      return false;
    }


    public void addDiagnostic(Diagnostic diagnostic, String fileUri) {
      final VirtualFile vf = FileUtils.virtualFileFromURI(fileUri);
      final ErrorViewStructure errorViewStructure = getErrorViewStructure();

      final GroupingElement groupingElement = new DiagGroupingElement(diagnostic.getMessage(), diagnostic, vf);

      final List<DiagnosticRelatedInformation> diagnosticRelatedInformations = diagnostic.getRelatedInformation();
      if (diagnosticRelatedInformations != null && !diagnosticRelatedInformations.isEmpty()) {
        for (DiagnosticRelatedInformation relatedInformation : diagnosticRelatedInformations) {
          errorViewStructure.addNavigatableMessage(diagnostic.getMessage(), new LSPNavigatableMessageElement(relatedInformation, groupingElement, myProject));
        }


      }

    }


  }

  private static class LSPNavigatableMessageElement extends NavigatableMessageElement {
/*
    public LSPNavigatableMessageElement(Diagnostic diagnostic, GroupingElement group, String uri, Project project) {
      super(getType(diagnostic.getSeverity()), group, new String[]{diagnostic.getMessage()}, new LSPNavigatable(project, uri, diagnostic.getRange()), "", "");
    }
    */

    public LSPNavigatableMessageElement(DiagnosticRelatedInformation relatedInfo, GroupingElement group, Project project) {
      super(group.getKind(), group, new String[]{relatedInfo.getMessage(), "new line 1", "new two"}, new LSPNavigatable(project, relatedInfo.getLocation()), "", "");
    }

    /*
    private static ErrorTreeElementKind getType(DiagnosticSeverity severity) {
      return ErrorTreeElementKind.ERROR;
    }
    */

    @Override
    public @org.jetbrains.annotations.Nullable CustomizeColoredTreeCellRenderer getLeftSelfRenderer() {
      return new CustomizeColoredTreeCellRenderer() {
        @Override
        public void customizeCellRenderer(SimpleColoredComponent renderer,
                                          JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
          renderer.setIcon(AllIcons.General.Error);

          final String[] text = getText();
          if (text != null) {
            // FIXME: render multiple lines
            renderer.append(text[0], SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          renderer.append(" ");
          renderer.append(FileUtils.shortenFileUri("bka/bla/helloworld.jimple:12:34"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      };
    }

    @Override
    public @NotNull String getPresentableText() {
      return "";
    }

  }


  private static final class DiagGroupingElement extends GroupingElement {

    public DiagGroupingElement(String name, Object data, VirtualFile file) {
      super(name, data, file);
      setKind(ErrorTreeElementKind.ERROR); // TODO
    }

    @Override
    public @org.jetbrains.annotations.Nullable CustomizeColoredTreeCellRenderer getLeftSelfRenderer() {
      return new CustomizeColoredTreeCellRenderer() {
        @Override
        public void customizeCellRenderer(SimpleColoredComponent renderer,
                                          JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
          renderer.setIcon(AllIcons.General.Error);

          final String[] text = getText();
          if (text != null) {
            // FIXME: render multiple lines
            renderer.append(text[0], SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          renderer.append(" ");
          renderer.append(FileUtils.shortenFileUri("bka/bla/helloworld.jimple:12:34"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      };
    }

    @Override
    public @org.jetbrains.annotations.Nullable CustomizeColoredTreeCellRenderer getRightSelfRenderer() {
      return new CustomizeColoredTreeCellRenderer() {
        @Override
        public void customizeCellRenderer(SimpleColoredComponent renderer,
                                          JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {

          // FIXME -> getSource
          renderer.append("JimpleParser", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      };
    }
  }


}
