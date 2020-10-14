// line to hack
// another line to hack
package magpiebridge.intellij.client;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.compiler.ProblemsView;
import com.intellij.compiler.impl.OneProjectItemCompileScope;
import com.intellij.compiler.progress.CompilerTask;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompilerMessage;
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
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
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
import com.intellij.pom.Navigatable;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.awt.RelativePoint;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import magpiebridge.intellij.plugin.QuickFixes;
import magpiebridge.intellij.plugin.Util;
import netscape.javascript.JSObject;
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
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LanguageClient implements org.eclipse.lsp4j.services.LanguageClient {

    private static final int informationType = CompilerTask.translateCategory(CompilerMessageCategory.INFORMATION);

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
            lenses.forEach(cl -> this.lenses.put(line, cl.isLeft() ? cl.getLeft() : cl.getRight().getCommand()));
        }

        private final Map<Integer, Command> lenses;

        @Nullable
        @Override
        public String getToolTip(int i, Editor editor) {
            if (lenses.containsKey(i)) {
                StringBuffer msg = new StringBuffer(lenses.get(i).getCommand());
                if (lenses.get(i).getArguments() != null) {
                    msg.append("(");
                    lenses.get(i).getArguments().forEach(s -> {
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
            return Collections.singletonList(new AnAction() {
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
        public void gutterClosed() {

        }
    }

    private final Project project;
    private LanguageServer server;
    private final ProblemsView problemView;
    private final QuickFixes quickFixes;
    private final Map<VirtualFile, List<Diagnostic>> publishedDiagnostics = new HashMap<>();
    private WebView htmlViewer;

    public LanguageClient(Project project, LanguageServer server) {
        this(project);
        this.server = server;
    }

    public LanguageClient(Project project) {
        this.project = project;
        this.problemView = ProblemsView.SERVICE.getInstance(project);
        this.quickFixes = project.getComponent(QuickFixes.class);
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).registerToolWindow("MagpieBridge Control Panel", false, ToolWindowAnchor.BOTTOM);
        JFXPanel fxPanel = new JFXPanel();
        JComponent component = toolWindow.getComponent();
        Platform.setImplicitExit(false);
        Platform.runLater(() -> {
            Group root  =  new Group();
            Scene scene  =  new  Scene(root, javafx.scene.paint.Color.WHITE);
            htmlViewer = new WebView();
            htmlViewer.getEngine().loadContent("<html>Hello</html>");
            htmlViewer.setPrefWidth(1200);
            root.getChildren().add(htmlViewer);
            fxPanel.setScene(scene);
        });
        component.getParent().add(fxPanel);

    }

    public void connect(LanguageServer server) {
        this.server = server;
    }

    private void applyEdit(String file, List<TextEdit> edits) {
        VirtualFile vf = Util.getVirtualFile(file);
        Document doc = FileDocumentManager.getInstance().getDocument(vf);
        for(TextEdit edit : edits) {
            Range rng = edit.getRange();
            Position start = rng.getStart();
            int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
            Position end = rng.getEnd();
            int endOffset = doc.getLineStartOffset(end.getLine()) + end.getCharacter();
            doc.replaceString(startOffset, endOffset, edit.getNewText());
        }
    }

    private void setSelection(String file, Range range){
        VirtualFile vf = Util.getVirtualFile(file);
        Document doc = Util.getDocument(vf);
        Editor[] editors = EditorFactory.getInstance().getEditors(doc, project);
        Position start = range.getStart();
        int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
        Position end = range.getEnd();
        int endOffset = doc.getLineStartOffset(end.getLine()) + end.getCharacter();

        Editor activeEditor;
        if (editors.length > 0)
            activeEditor = editors[0];
        else {
            activeEditor = EditorFactory.getInstance().createEditor(doc, project,EditorKind.MAIN_EDITOR);
        }
        activeEditor.getSelectionModel().setSelection(startOffset, endOffset);
        activeEditor.getCaretModel().moveToOffset(startOffset);


    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        WriteCommandAction.runWriteCommandAction(project, () ->
                params.getEdit().getChanges().forEach((file, edits) -> applyEdit(file, edits)));
        ApplyWorkspaceEditResponse ret = new ApplyWorkspaceEditResponse();
        ret.setApplied(true);
        return CompletableFuture.completedFuture(ret);
    }

    @Override
    public void logMessage(MessageParams messageParams) {
        Logger.getInstance(getClass()).info(messageParams.getMessage());
    }

    private final Map<Editor,Set<EditorMouseMotionListener>> editorListeners = new HashMap<>();

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams params) {

        String file = params.getUri();
        VirtualFile vf = Util.getVirtualFile(file);

        this.publishedDiagnostics.put(vf, params.getDiagnostics());
        showDiagnostics(vf);
    }

    public void showDiagnostics(VirtualFile vf){
        if (!publishedDiagnostics.containsKey(vf)){
            return;
        }
        Document doc = Util.getDocument(vf);
        List<Diagnostic> diagnostics = publishedDiagnostics.get(vf);
        UUID uuid =  UUID.randomUUID();
        //clean up old messages in problem view before adding new ones
        problemView.clearOldMessages(new OneProjectItemCompileScope(project,vf),uuid);
        diagnostics.forEach((diag) -> {
            Range rng = diag.getRange();
            Position start = rng.getStart();
            int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
            Navigatable x = new OpenFileDescriptor(project, vf, startOffset);
            CompilerMessage msg = new CompilerMessage() {
                @NotNull
                @Override
                public CompilerMessageCategory getCategory() {
                    DiagnosticSeverity severity=diag.getSeverity();
                    if(severity.equals(DiagnosticSeverity.Error))
                        return CompilerMessageCategory.ERROR;
                    if(severity.equals(DiagnosticSeverity.Warning))
                        return CompilerMessageCategory.WARNING;
                    return CompilerMessageCategory.INFORMATION;
                }

                @Override
                public String getMessage() {
                    if(diag.getSource()!=null)
                        return diag.getMessage()+" ["+diag.getSource()+"]";
                    return diag.getMessage();
                }

                @Nullable
                @Override
                public Navigatable getNavigatable() {
                    return x;
                }

                @Override
                public VirtualFile getVirtualFile() {
                    return vf;
                }

                @Override
                public String getExportTextPrefix() {
                    return null;
                }

                @Override
                public String getRenderTextPrefix() {
                    return "";
                }
            };
            problemView.addMessage(msg, uuid);
        });

        //clean old quickFixes and add new ones
        quickFixes.clear();
        diagnostics.forEach((diag) -> quickFixes.addDiagnostic(doc, vf.getUrl(), diag, server));

        //clean old markup in editors and add new ones
        Editor[] editors = EditorFactory.getInstance().getEditors(doc, project);
        for (Editor editor : editors) {
            clearMarkup(editor);
        }
        diagnostics.forEach((diag) -> {
            for (Editor editor : editors) {
                showMarkup(diag, editor, doc);
            }
        });
    }

    private Balloon currentHint = null;
    private Diagnostic currHintDiag = null;

    private void showMarkup(Diagnostic diag, Editor editor, Document doc) {
        int flags =0;// HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_OTHER_HINT | HintManager.HIDE_BY_SCROLLING;

        final TextAttributes attr = new TextAttributes();
        attr.setEffectColor(JBColor.RED);
        attr.setEffectType(EffectType.WAVE_UNDERSCORE);

        Range rng = diag.getRange();
        Position start = rng.getStart();
        int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
        Position end = rng.getEnd();
        int endOffset = doc.getLineStartOffset(end.getLine()) + end.getCharacter();

        WriteCommandAction.runWriteCommandAction(project, () -> {
            MarkupModel markup = editor.getMarkupModel();
            markup.addRangeHighlighter(startOffset,
                    endOffset,
                    HighlighterLayer.WEAK_WARNING,
                    attr,
                    HighlighterTargetArea.EXACT_RANGE);
        });

        EditorMouseMotionListener l;
        editor.addEditorMouseMotionListener(l = new EditorMouseMotionListener() {
            private void handleEvent(EditorMouseEvent e) {
                if (e.getArea().equals(EditorMouseEventArea.EDITING_AREA)) {
                    int offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(e.getMouseEvent().getPoint()));
                    if (offset >= startOffset && offset <= endOffset) {
                        if (diag == currHintDiag && currentHint != null){
                            return;
                        }
                        String msg = diag.getMessage();
                        if(diag.getSource()!=null)
                            msg="["+ diag.getSource()+"] "+msg;
                        JLabel label = new JLabel(msg);
                        label.setBackground(Color.lightGray);
                        JComponent hintText = label;
                        boolean showRelatedInfo = false;
                        //show related information
                        List<DiagnosticRelatedInformation> relatedInfo = diag.getRelatedInformation();
                        if (relatedInfo != null && relatedInfo.size() > 0) {
                            showRelatedInfo = true;
                            JPanel p = new JPanel();
                            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
                            p.add(hintText);
                            //p.add(new JLabel("Related Information"));
                            JPanel relInfoPanel = new JPanel();
                            relInfoPanel.setLayout(new GridLayout(0,2));
                            p.add(relInfoPanel);
                            relatedInfo.sort(Comparator.comparingInt(d -> d.getLocation().getRange().getStart().getLine()));
                            relatedInfo.forEach(d->{
                                String fileName = d.getLocation().getUri();
                                String[] pathParts = fileName.split("[/\\\\]");
                                fileName = pathParts[pathParts.length-1];
                                Range codeRange = d.getLocation().getRange();
                                int line=codeRange.getStart().getLine()+1;
                                int column=codeRange.getStart().getCharacter()+1;
                                JButton gotoButton = new JButton(fileName.concat("("+line+", "+column+"):"));
                                gotoButton.setOpaque(false);
                                gotoButton.setBorderPainted(false);
                                gotoButton.addActionListener(click->{
                                    setSelection(d.getLocation().getUri(),codeRange);
                                    currentHint.dispose();
                                });
                                relInfoPanel.add(gotoButton);
                                relInfoPanel.add(new JLabel(d.getMessage()));
                            });
                            hintText = p;
                        }
                        if (currentHint != null){
                            currentHint.dispose();
                        }
                        if(showRelatedInfo)
                            currentHint = JBPopupFactory.getInstance().createBalloonBuilder(hintText).createBalloon();
                        else {
                            msg = msg.replaceAll("(\r\n|\n)", "<br />");
                            String html ="<html><body>"+msg+"</body></html>";
                            currentHint = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(html,  getMessageType(diag.getSeverity()), null).createBalloon();
                        }
                        currentHint.show(new RelativePoint(e.getMouseEvent()), Balloon.Position.above);
                        currHintDiag = diag;
                        currentHint.addListener(new JBPopupListener() {
                            @Override
                            public void onClosed(@NotNull LightweightWindowEvent event) {
                                if(currentHint!=null) {
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

        if (! editorListeners.containsKey(editor)) {
            editorListeners.put(editor, new HashSet<>());
        }

        editorListeners.get(editor).add(l);
    }

    private MessageType getMessageType(DiagnosticSeverity severity)
    {
        if(severity.equals(DiagnosticSeverity.Error))
            return  MessageType.ERROR;
        else if(severity.equals(DiagnosticSeverity.Warning))
            return MessageType.WARNING;
        else
            return MessageType.INFO;
    }
    private void clearMarkup(Editor editor) {
        if (editorListeners.containsKey(editor)) {
            editorListeners.remove(editor).forEach(editor::removeEditorMouseMotionListener);
        }
        WriteCommandAction.runWriteCommandAction(project, () -> {
            MarkupModel markup = editor.getMarkupModel();
            markup.removeAllHighlighters();
        });
    }

    @Override
    public void showMessage(@NotNull MessageParams messageParams) {
        //showMessage(messageParams.getType() + ": " + messageParams.getMessage());
        NotificationType type;
        switch (messageParams.getType()){
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
        Notifications.Bus.notify(new Notification("lsp", messageParams.getType().toString(), messageParams.getMessage(),type), project);
    }

    public void showMessage(String msg) {
        WriteCommandAction.runWriteCommandAction(project, () -> {

            for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
                int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_OTHER_HINT | HintManager.HIDE_BY_SCROLLING;
                JComponent hintText = new JLabel(msg);
                LightweightHint hint = new LightweightHint(hintText);
                Point p = HintManagerImpl.getHintPosition(hint, editor, editor.xyToLogicalPosition(MouseInfo.getPointerInfo().getLocation()), HintManager.ABOVE);
                HintManagerImpl.getInstanceImpl().showEditorHint(hint,
                        editor,
                        p,
                        flags,
                        -1,
                        true,
                        HintManagerImpl.createHintHint(editor,
                                p,
                                hint,
                                HintManager.ABOVE).setContentActive(true));
/*
                Messages.showMessageDialog(project,
                        messageParams.getMessage(),
                        messageParams.getType().toString(),
                        Messages.getInformationIcon());
 */
            }
        });
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams params) {
        int[] choice = new int[1];
        WriteCommandAction.runWriteCommandAction(project, () -> {
            List<String> options = new ArrayList<>();
            params.getActions().forEach((a) -> options.add(a.getTitle()));
            String[] x = options.toArray(new String[params.getActions().size()]);
            choice[0] = Messages.showDialog(params.getMessage(),
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
        Platform.runLater(()->{
            htmlViewer.getEngine().setJavaScriptEnabled(true);
            htmlViewer.getEngine().loadContent(content);
        });
    }

}