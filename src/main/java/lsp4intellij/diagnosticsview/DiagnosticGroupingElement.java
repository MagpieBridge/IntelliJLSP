package lsp4intellij.diagnosticsview;

import com.intellij.ide.errorTreeView.ErrorTreeElementKind;
import com.intellij.ide.errorTreeView.GroupingElement;
import com.intellij.ide.errorTreeView.NavigatableErrorTreeElement;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.utils.FileUtils;

import javax.swing.*;

public final class DiagnosticGroupingElement extends GroupingElement implements NavigatableErrorTreeElement {

  private final Diagnostic diag;
  private Navigatable navigatable;

  public DiagnosticGroupingElement(Diagnostic diag, Navigatable navigatable, VirtualFile file) {
    super(diag.getMessage()+file.toNioPath().toString(), null, file);
    this.diag = diag;
    this.navigatable = navigatable;
    setKind(getType(diag.getSeverity()));
  }


  @Override
  public String[] getText() {
    return new String[]{diag.getMessage()};
  }

  private static ErrorTreeElementKind getType(DiagnosticSeverity severity) {
    switch (severity) {
      case Error:
        return ErrorTreeElementKind.ERROR;
      case Warning:
        return ErrorTreeElementKind.WARNING;
      case Information:
        return ErrorTreeElementKind.INFO;
      case Hint:
        return ErrorTreeElementKind.NOTE;
    }
    return ErrorTreeElementKind.GENERIC;
  }


  final CustomizeColoredTreeCellRenderer customizeColoredTreeCellRenderer = new CustomizeColoredTreeCellRenderer() {
    @Override
    public void customizeCellRenderer(SimpleColoredComponent renderer,
                                      JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      renderer.setIcon(getKind().getIcon());

      final String[] text = getText();
      if (text != null) {
        // FIXME: render multiple lines
        renderer.append(text[0], SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      renderer.append(" ");
      renderer.append(FileUtils.shortenFileUri(getFile().getName()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      renderer.append("(" + diag.getRange().getStart().getLine() + "," + diag.getRange().getStart().getCharacter() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);

    }
  };

  @Override
  public CustomizeColoredTreeCellRenderer getLeftSelfRenderer() {
    return customizeColoredTreeCellRenderer;
  }

  final CustomizeColoredTreeCellRenderer customizeColoredTreeCellRenderer2 = new CustomizeColoredTreeCellRenderer() {
    @Override
    public void customizeCellRenderer(SimpleColoredComponent renderer,
                                      JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      renderer.append(diag.getSource(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      renderer.setOpaque(true);
      renderer.setTransparentIconBackground(true);
    }
  };
  @Override
  public CustomizeColoredTreeCellRenderer getRightSelfRenderer() {
    return customizeColoredTreeCellRenderer2;
  }

  @Override
  public @NotNull Navigatable getNavigatable() {
    return navigatable;
  }
}
