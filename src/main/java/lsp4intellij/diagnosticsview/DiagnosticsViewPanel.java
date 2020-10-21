package lsp4intellij.diagnosticsview;

import com.intellij.analysis.problemsView.toolWindow.ProblemsView;
import com.intellij.ide.errorTreeView.ErrorTreeElement;
import com.intellij.ide.errorTreeView.ErrorViewStructure;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.JBUI;
import magpiebridge.intellij.plugin.ProjectService;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.utils.ApplicationUtils;
import org.wso2.lsp4intellij.utils.FileUtils;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DiagnosticsViewPanel extends NewErrorTreeViewPanel {
  private Map<String, List<ErrorTreeElement>> diagnosticMap = null;
  private ActionToolbar myLeftToolbar;
  private boolean fileDiagnosticScope = true;

  public DiagnosticsViewPanel(Project project) {
    super(project, null, true, true);
    createToolbarPanel();
  }

  private JPanel createToolbarPanel() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.addSeparator();
    group.add(new ToggleAllDiagnosticsAction());

    ActionManager actionManager = ActionManager.getInstance();
    myLeftToolbar = actionManager.createActionToolbar(ActionPlaces.COMPILER_MESSAGES_TOOLBAR, group, false);
    return JBUI.Panels.simplePanel(myLeftToolbar.getComponent());
  }


  public void initialize(@NotNull Project project){

    final ToolWindow toolWindow = ProblemsView.getToolWindow(project);

    final ContentManager contentManager = toolWindow.getContentManager();
    final DiagnosticsViewPanel component = project.getService(ProjectService.class).getDiagnosticsViewPanel();

    ApplicationUtils.invokeLater( () -> {
      final Content content = contentManager.getFactory().createContent(component, "Diagnostics", false);
      contentManager.addContent(content);
      contentManager.setSelectedContent(content, true, true);
    });

    Disposer.register( project.getService(ProjectService.class) , component);
  }


  @Override
  protected boolean canHideWarnings() {
    return false;
  }


  public void setDiagnosticForFile(Project project, PublishDiagnosticsParams diagnosticParams) {
    if( diagnosticMap == null){
      initialize(project);
      diagnosticMap = new HashMap<>();
    }

    // TODO: filter Diagnostics for current file vs show for all files
//    final FileEditorManager instance = FileEditorManager.getInstance(project);
//    Editor editor = instance.getSelectedTextEditor();


    final ErrorViewStructure errorViewStructure = getErrorViewStructure();
    List<ErrorTreeElement> addedlist = new ArrayList<>();

    // add the new batch of Diagnostics
    for (Diagnostic diagnostic : diagnosticParams.getDiagnostics()) {

      final VirtualFile vf = FileUtils.virtualFileFromURI(diagnosticParams.getUri());
      final DiagnosticGroupingElement groupingElement = new DiagnosticGroupingElement(diagnostic, vf);

      final List<DiagnosticRelatedInformation> diagnosticRelatedInformations = diagnostic.getRelatedInformation();
      if (diagnosticRelatedInformations != null && !diagnosticRelatedInformations.isEmpty()) {
        for (DiagnosticRelatedInformation relatedInformation : diagnosticRelatedInformations) {
          final DiagnosticNavigatableMessageElement navigatableMessageElement = new DiagnosticNavigatableMessageElement(relatedInformation, groupingElement, myProject);
          final String groupName = diagnostic.getMessage() + vf.toNioPath().toString();
          errorViewStructure.addNavigatableMessage(groupName, navigatableMessageElement);
          addedlist.add(navigatableMessageElement);
        }
      }
    }

    List<ErrorTreeElement> overriddenDiagnosticList = diagnosticMap.put(diagnosticParams.getUri(), addedlist);
    // remove old entires from view
    if (overriddenDiagnosticList !=null){
      for (ErrorTreeElement diagnostic : overriddenDiagnosticList) {
        errorViewStructure.removeElement(diagnostic);
      }
    }

    expandAll();
  }


  private final class ToggleAllDiagnosticsAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      fileDiagnosticScope = !fileDiagnosticScope;
    }
  }
}
