package lsp4intellij.diagnosticsview;

import com.intellij.ide.errorTreeView.GroupingElement;
import com.intellij.ide.errorTreeView.NavigatableMessageElement;
import com.intellij.openapi.project.Project;
import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import lsp4intellij.LSPNavigatable;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.utils.FileUtils;

import javax.swing.*;

public class LSPNavigatableMessageElement extends NavigatableMessageElement {

  private final DiagnosticRelatedInformation relatedInfo;

  public LSPNavigatableMessageElement(DiagnosticRelatedInformation relatedInfo, GroupingElement group, Project project) {
    super(group.getKind(), group, new String[]{relatedInfo.getMessage(), "new line 1", "new two"}, new LSPNavigatable(project, relatedInfo.getLocation()), "", "");
    this.relatedInfo = relatedInfo;
  }

  @Override
  public CustomizeColoredTreeCellRenderer getLeftSelfRenderer() {
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

        final String[] text = getText();
        if (text != null) {
          // FIXME: render multiple lines
          renderer.append(text[0], SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }

        renderer.append(" ");
        renderer.append(FileUtils.shortenFileUri(relatedInfo.getLocation().getUri()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        renderer.append(" (" + relatedInfo.getLocation().getRange().getStart().getLine() + "," + relatedInfo.getLocation().getRange().getStart().getCharacter() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);

      }
    };
  }

  @Override
  public @NotNull String getPresentableText() {
    return "";
  }

}
