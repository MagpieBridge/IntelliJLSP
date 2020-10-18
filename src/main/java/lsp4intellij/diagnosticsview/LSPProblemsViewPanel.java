package lsp4intellij.diagnosticsview;

import com.intellij.ide.errorTreeView.ErrorViewStructure;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.util.List;

public final class LSPProblemsViewPanel extends NewErrorTreeViewPanel {
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

    final DiagnosticGroupingElement groupingElement = new DiagnosticGroupingElement(diagnostic, vf);

    final List<DiagnosticRelatedInformation> diagnosticRelatedInformations = diagnostic.getRelatedInformation();
    if (diagnosticRelatedInformations != null && !diagnosticRelatedInformations.isEmpty()) {
      for (DiagnosticRelatedInformation relatedInformation : diagnosticRelatedInformations) {
        errorViewStructure.addNavigatableMessage(diagnostic.getMessage(), new LSPNavigatableMessageElement(relatedInformation, groupingElement, myProject));
      }
    }

  }


}
