// line to hack
// another line to hack
package magpiebridge.intellij.client;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import com.intellij.compiler.progress.CompilerTask;
import com.intellij.ide.errorTreeView.ErrorViewStructure;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.MessageCategory;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import magpiebridge.intellij.plugin.QuickFixes;
import magpiebridge.intellij.plugin.Util;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static javax.swing.event.HyperlinkEvent.EventType.ACTIVATED;
import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;

public class MagpieLanguageClient implements org.eclipse.lsp4j.services.LanguageClient {

  private static final int informationType =
      CompilerTask.translateCategory(CompilerMessageCategory.INFORMATION);

  private final class GutterActions implements EditorGutterAction {
    public GutterActions(Diagnostic diag, List<? extends Either<Command, CodeAction>> lenses) {
      this.lenses = new LinkedHashMap<>();
      int line = diag.getRange().getStart().getLine();
      lenses.forEach(cl -> this.lenses.put(line, cl));
    }

    private final Map<Integer, Either<Command, CodeAction>> lenses;

    @Override
    public void doAction(int i) {
      Either<Command, CodeAction> c = lenses.get(i);
      Util.doAction(c, project, server);
    }

    @Override
    public Cursor getCursor(int i) {
      return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }
  }

  private final class GutterAnnotations implements TextAnnotationGutterProvider {

    public GutterAnnotations(Diagnostic diag, List<? extends Either<Command, CodeAction>> lenses) {
      this.lenses = new LinkedHashMap<>();
      int line = diag.getRange().getStart().getLine();
      lenses.forEach(
          cl -> this.lenses.put(line, cl.isLeft() ? cl.getLeft() : cl.getRight().getCommand()));
    }

    private final Map<Integer, Command> lenses;

    @Nullable
    @Override
    public String getToolTip(int i, Editor editor) {
      if (lenses.containsKey(i)) {
        StringBuffer msg = new StringBuffer(lenses.get(i).getCommand());
        if (lenses.get(i).getArguments() != null) {
          msg.append("(");
          lenses
              .get(i)
              .getArguments()
              .forEach(
                  s -> {
                    msg.append(s.toString()).append(" ");
                  });
          msg.append(")");
        }
        return msg.toString();
      } else {
        return null;
      }
    }

    @Nullable
    @Override
    public String getLineText(int i, Editor editor) {
      return lenses.containsKey(i) ? lenses.get(i).getCommand() : null;
    }

    @Override
    public EditorFontType getStyle(int i, Editor editor) {
      return EditorFontType.BOLD;
    }

    @Nullable
    @Override
    public ColorKey getColor(int i, Editor editor) {
      return ColorKey.createColorKey("LSP", Color.BLUE);
    }

    @Nullable
    @Override
    public Color getBgColor(int i, Editor editor) {
      return editor.getColorsScheme().getDefaultBackground();
    }

    @Override
    public List<AnAction> getPopupActions(int i, Editor editor) {
      return Collections.singletonList(
          new AnAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
              Command c = lenses.get(i);
              ExecuteCommandParams params = new ExecuteCommandParams();
              params.setCommand(c.getCommand());
              params.setArguments(c.getArguments());
              server.getWorkspaceService().executeCommand(params);
            }
          });
    }

    @Override
    public void gutterClosed() {}
  }

  private final Project project;
  private LanguageServer server;
  private final QuickFixes quickFixes;
  private final Map<VirtualFile, List<Diagnostic>> publishedDiagnostics = new HashMap<>();
  private WebView htmlViewer;
  private NewErrorTreeViewPanel diagViewPanel;
  private ToolWindow diagViewWindow;
  private ToolWindow controlViewWindow;
  private String diagViewID = "MagpieBridge LSP Diagnostics";
  private String controlViewID = "MagpieBridge Control Panel";

  public MagpieLanguageClient(Project project, LanguageServer server) {
    this(project);
    this.server = server;
  }

  public MagpieLanguageClient(Project project) {
      this.project = project;
      this.quickFixes = project.getComponent(QuickFixes.class);
      JFXPanel fxPanel = new JFXPanel();
      this.diagViewPanel = new NewErrorTreeViewPanel(project, null);
      SwingUtilities.invokeLater(() -> {
          // add control panel window
          if (ToolWindowManager.getInstance(project).getToolWindow(controlViewID) == null) {
              this.controlViewWindow =
                  ToolWindowManager.getInstance(project)
                                   .registerToolWindow(controlViewID, false, ToolWindowAnchor.BOTTOM);
          } else
              this.controlViewWindow = ToolWindowManager.getInstance(project).getToolWindow(controlViewID);
          this.controlViewWindow.getComponent().getParent().add(fxPanel);
          // add diagnostics window
          if (ToolWindowManager.getInstance(project).getToolWindow(diagViewID) == null){
              this.diagViewWindow =
                  ToolWindowManager.getInstance(project)
                                   .registerToolWindow(diagViewID, false, ToolWindowAnchor.BOTTOM);
          }
          else {
              this.diagViewWindow = ToolWindowManager.getInstance(project).getToolWindow(diagViewID);
          }
          this.diagViewWindow.getComponent().add(diagViewPanel);
      });
      Platform.setImplicitExit(false);
      Platform.runLater(
          () -> {
              Group root = new Group();
              Scene scene = new Scene(root, javafx.scene.paint.Color.WHITE);
              htmlViewer = new WebView();
              htmlViewer.getEngine().loadContent("<html>Loading</html>");
              htmlViewer.setPrefWidth(1200);
              root.getChildren().add(htmlViewer);
              fxPanel.setScene(scene);
          });
  }

  public void connect(LanguageServer server) {
    this.server = server;
  }

  private void applyEdit(String file, List<TextEdit> edits) {
    VirtualFile vf = Util.getVirtualFile(file);
    Document doc = FileDocumentManager.getInstance().getDocument(vf);
    for (TextEdit edit : edits) {
      Range rng = edit.getRange();
      Position start = rng.getStart();
      int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
      Position end = rng.getEnd();
      int endOffset = doc.getLineStartOffset(end.getLine()) + end.getCharacter();
      doc.replaceString(startOffset, endOffset, edit.getNewText());
    }
  }

  /**
   * This method select code range in the given file.
   *
   * @param file
   * @param range
   */
  private void setSelection(String file, Range range) {
    VirtualFile vf = Util.getVirtualFile(file);
    Document doc = Util.getDocument(vf);
    Editor[] editors = EditorFactory.getInstance().getEditors(doc, project);
    Position start = range.getStart();
    int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
    Position end = range.getEnd();
    int endOffset = doc.getLineStartOffset(end.getLine()) + end.getCharacter();
    Editor activeEditor;
    if (editors.length > 0) activeEditor = editors[0];
    else {
      activeEditor = EditorFactory.getInstance().createEditor(doc, project, EditorKind.MAIN_EDITOR);
    }
    activeEditor.getSelectionModel().setSelection(startOffset, endOffset);
    activeEditor.getCaretModel().moveToOffset(startOffset);
  }

  @Override
  public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
    WriteCommandAction.runWriteCommandAction(
        project,
        () -> params.getEdit().getChanges().forEach((file, edits) -> applyEdit(file, edits)));
    ApplyWorkspaceEditResponse ret = new ApplyWorkspaceEditResponse();
    ret.setApplied(true);
    return CompletableFuture.completedFuture(ret);
  }

  @Override
  public void logMessage(MessageParams messageParams) {
    Logger.getInstance(getClass()).info(messageParams.getMessage());
  }

  private final Map<Editor, Set<EditorMouseMotionListener>> editorListeners = new HashMap<>();

  @Override
  public void publishDiagnostics(PublishDiagnosticsParams params) {
    String file = params.getUri();
    VirtualFile vf = Util.getVirtualFile(file);
    this.publishedDiagnostics.put(vf, params.getDiagnostics());
    showDiagnostics(vf);
  }

  public void showDiagnostics(VirtualFile vf) {
    if (!publishedDiagnostics.containsKey(vf)) {
      return;
    }
    // clean up the diagnostics for current file at first
    ErrorViewStructure structure = this.diagViewPanel.getErrorViewStructure();
    structure.removeGroup(vf.getPath());
    this.diagViewPanel.reload();
    Document doc = Util.getDocument(vf);
    List<Diagnostic> diagnostics = publishedDiagnostics.get(vf);
    diagnostics.forEach(
        (diag) -> {
          Range rng = diag.getRange();
          Position start = rng.getStart();
          String message = diag.getMessage();
          if (diag.getSource() != null) message = diag.getMessage() + " [" + diag.getSource() + "]";
          MessageType type = getMessageType(diag.getSeverity());
          int t =
              type == MessageType.ERROR
                  ? MessageCategory.ERROR
                  : type == MessageType.WARNING
                      ? MessageCategory.WARNING
                      : MessageCategory.INFORMATION;
          this.diagViewPanel.addMessage(
              t, new String[] {message}, vf, start.getLine(), start.getLine(), vf);
        });
    // clean old quickFixes and add new ones
    quickFixes.clear();
    diagnostics.forEach((diag) -> quickFixes.addDiagnostic(doc, vf.getUrl(), diag, server));

    // clean old markup in editors and add new ones
    Editor[] editors = EditorFactory.getInstance().getEditors(doc, project);
    for (Editor editor : editors) {
      clearMarkup(editor);
    }
    diagnostics.forEach(
        (diag) -> {
          for (Editor editor : editors) {
            showMarkup(diag, editor, doc);
          }
        });
  }

  private Balloon currentHint = null;
  private Diagnostic currHintDiag = null;

  private void showMarkup(Diagnostic diag, Editor editor, Document doc) {
    int flags =
        0; // HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE |
           // HintManager.HIDE_BY_OTHER_HINT | HintManager.HIDE_BY_SCROLLING;

    final TextAttributes attr = new TextAttributes();
    attr.setEffectColor(JBColor.RED);
    attr.setEffectType(EffectType.WAVE_UNDERSCORE);

    Range rng = diag.getRange();
    Position start = rng.getStart();
    int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
    Position end = rng.getEnd();
    int endOffset = doc.getLineStartOffset(end.getLine()) + end.getCharacter();

    WriteCommandAction.runWriteCommandAction(
        project,
        () -> {
          MarkupModel markup = editor.getMarkupModel();
          markup.addRangeHighlighter(
              startOffset,
              endOffset,
              HighlighterLayer.WEAK_WARNING,
              attr,
              HighlighterTargetArea.EXACT_RANGE);
        });

    EditorMouseMotionListener l;
    editor.addEditorMouseMotionListener(
        l =
            new EditorMouseMotionListener() {
              private void handleEvent(EditorMouseEvent e) {
                if (e.getArea().equals(EditorMouseEventArea.EDITING_AREA)) {
                  int offset =
                      editor.logicalPositionToOffset(
                          editor.xyToLogicalPosition(e.getMouseEvent().getPoint()));
                  if (offset >= startOffset && offset <= endOffset) {
                    if (diag == currHintDiag && currentHint != null) {
                      return;
                    }
                    if (currentHint != null) {
                      currentHint.dispose();
                    }
                    String html = buildHoverHTML(diag);
                    MessageType messageType = getMessageType(diag.getSeverity());
                    currentHint =
                        JBPopupFactory.getInstance()
                            .createHtmlTextBalloonBuilder(
                                html,
                                messageType.getDefaultIcon(),
                                messageType.getPopupBackground(),
                                new HyperlinkListener() {
                                  @Override
                                  public void hyperlinkUpdate(HyperlinkEvent e) {
                                    if (!(e.getEventType() == ACTIVATED)) {
                                      return;
                                    }
                                    String[] splits = e.getURL().toString().split("->");
                                    String url = splits[0];
                                    final int startLine = Integer.parseInt(splits[1]);
                                    final int startCharacter = Integer.parseInt(splits[2]);
                                    final int endLine = Integer.parseInt(splits[3]);
                                    final int endCharacter = Integer.parseInt(splits[4]);
                                    Range range =
                                        new Range(
                                            new Position(startLine, startCharacter),
                                            new Position(endLine, endCharacter));
                                    setSelection(url, range);
                                  }
                                })
                            .createBalloon();
                    currentHint.show(new RelativePoint(e.getMouseEvent()), Balloon.Position.below);
                    currHintDiag = diag;
                    currentHint.addListener(
                        new JBPopupListener() {
                          @Override
                          public void onClosed(@NotNull LightweightWindowEvent event) {
                            if (currentHint != null) {
                              currentHint.dispose();
                              currentHint = null;
                              currHintDiag = null;
                            }
                          }
                        });
                  }
                }
              }

              @Override
              public void mouseMoved(@NotNull EditorMouseEvent e) {
                handleEvent(e);
              }

              @Override
              public void mouseDragged(@NotNull EditorMouseEvent e) {
                handleEvent(e);
              }
            });

    if (!editorListeners.containsKey(editor)) {
      editorListeners.put(editor, new HashSet<>());
    }

    editorListeners.get(editor).add(l);
  }

  /**
   * This method creates hover message for diagnostic.
   *
   * @param diagnostic
   * @return
   */
  private String buildHoverHTML(Diagnostic diagnostic) {
    StringBuilder sb = new StringBuilder();
    sb.append("<html><div style='margin:5px 0;'>")
        .append(escapeHtml(diagnostic.getMessage()).replaceAll("\n", "<br>"))
        .append("</div>");

    if (diagnostic.getRelatedInformation() != null
        && !diagnostic.getRelatedInformation().isEmpty()) {

      for (DiagnosticRelatedInformation related : diagnostic.getRelatedInformation()) {
        final VirtualFile hrefVf = Util.getVirtualFile(related.getLocation().getUri());
        final Range range = related.getLocation().getRange();
        final int startLine = range.getStart().getLine();
        final int startCharacter = range.getStart().getCharacter();
        final int endLine = range.getEnd().getLine();
        final int endCharactor = range.getEnd().getCharacter();
        if (hrefVf != null) {
          sb.append("<a href=\"")
              .append(related.getLocation().getUri())
              .append("->")
              .append(startLine)
              .append("->")
              .append(startCharacter)
              .append("->")
              .append(endLine)
              .append("->")
              .append(endCharactor)
              .append("\">")
              .append(Util.shortenFileUri(related.getLocation().getUri()))
              .append(" ")
              .append(Util.positionToString(new Position(startLine + 1, startCharacter)))
              .append("</a> ");
        } else {
          sb.append("<span color='GRAY'>")
              .append(escapeHtml(Util.shortenFileUri(related.getLocation().getUri())))
              .append(" ")
              .append(
                  escapeHtml(Util.positionToString(new Position(startLine + 1, startCharacter))))
              .append("</span> ");
        }
        sb.append(" ").append(escapeHtml(related.getMessage())).append("<br>");
      }
    }

    String code = "";
    boolean hasCode = false, hasSource = false;
    if (diagnostic.getCode() != null) {
      code = diagnostic.getCode().get().toString();
      if (!code.isEmpty()) {
        hasCode = true;
      }
    }
    final String source = diagnostic.getSource();
    if (source != null && !source.isEmpty()) {
      hasSource = true;
    }
    if (hasCode || hasSource) {
      sb.append("<div style='color:GRAY;text-align:right;'>");
      if (hasCode) {
        sb.append(escapeHtml(code)).append(" ");
      }
      if (hasSource) {
        sb.append(escapeHtml(source));
      }
      sb.append("</div>");
    }
    sb.append("</html>");
    return sb.toString();
  }

  private MessageType getMessageType(DiagnosticSeverity severity) {
    if (severity.equals(DiagnosticSeverity.Error)) return MessageType.ERROR;
    else if (severity.equals(DiagnosticSeverity.Warning)) return MessageType.WARNING;
    else return MessageType.INFO;
  }

  private void clearMarkup(Editor editor) {
    if (editorListeners.containsKey(editor)) {
      editorListeners.remove(editor).forEach(editor::removeEditorMouseMotionListener);
    }
    WriteCommandAction.runWriteCommandAction(
        project,
        () -> {
          MarkupModel markup = editor.getMarkupModel();
          markup.removeAllHighlighters();
        });
  }

  @Override
  public void showMessage(@NotNull MessageParams messageParams) {
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
        project);
  }

  @Override
  public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams params) {
    int[] choice = new int[1];
    WriteCommandAction.runWriteCommandAction(
        project,
        () -> {
          List<String> options = new ArrayList<>();
          params.getActions().forEach((a) -> options.add(a.getTitle()));
          String[] x = options.toArray(new String[params.getActions().size()]);
          choice[0] =
              Messages.showDialog(
                  params.getMessage(),
                  params.getType().toString(),
                  x,
                  0,
                  Messages.getQuestionIcon());
        });
    return CompletableFuture.completedFuture(params.getActions().get(choice[0]));
  }

  @Override
  public void telemetryEvent(Object o) {
    Logger.getInstance(getClass()).info(o.toString());
  }

  @Override
  public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
    WorkspaceFolder root = new WorkspaceFolder();
    root.setName("root");
    root.setUri(project.getBasePath());
    return CompletableFuture.completedFuture(Collections.singletonList(root));
  }

  @JsonNotification("magpiebridge/showHTML")
  public void showHTML(String content) {
    Platform.runLater(
        () -> {
          htmlViewer.getEngine().setJavaScriptEnabled(true);
          htmlViewer.getEngine().loadContent(content);
        });
  }

   @JsonRequest("magpiebridge/showInputBox")
   public CompletableFuture<Map<String, String>> showInputBox(String... messages){
        Map<String, String> inputValues=new HashMap<>();
        WriteCommandAction.runWriteCommandAction(
            project,
            () -> {
               for(String message: messages) {
                   String title = "Input required";
                   Pattern p1 = Pattern.compile("\\b(\\w*(P|p)(A|a)(S|s)(S|s)(W|w)(O|o)(R|r)(D|d)\\w*)\\b");
                   Pattern p2 = Pattern.compile("\\b(\\w*(P|p)(A|a)(S|s)(S|s)(P|p)(H|h)(R|r)(A|a)(S|s)(E|e)\\w*)\\b");
                   String input = null;
                   if(p1.matcher(message).find()||p2.matcher(message).find())
                       input = Messages.showPasswordDialog(message, title);
                   else
                       input  = Messages.showInputDialog(message,title,Messages.getQuestionIcon());
                   inputValues.put(message,input);
               }
            });
        return CompletableFuture.completedFuture(inputValues);
    }
}
