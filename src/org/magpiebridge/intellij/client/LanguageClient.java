// line to hack
// another line to hack
package org.magpiebridge.intellij.client;

import com.intellij.codeInsight.daemon.impl.DaemonTooltipUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.compiler.ProblemsView;
import com.intellij.compiler.impl.FileSetCompileScope;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.awt.RelativePoint;
import com.sun.javafx.application.PlatformImpl;
import javafx.embed.swing.JFXPanel;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.web.WebView;
import magpiebridge.core.MagpieLanguageClient;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.magpiebridge.intellij.plugin.DiagnosticProvider;
import org.magpiebridge.intellij.plugin.Inlays;
import org.magpiebridge.intellij.plugin.QuickFixes;
import org.magpiebridge.intellij.plugin.Util;

import javax.swing.*;
import java.awt.Color;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class LanguageClient implements MagpieLanguageClient {
    private final Project project;
    private LanguageServer server;
    private final ProblemsView pv;
    private final QuickFixes intentions;
    public final Map<VirtualFile, List<Diagnostic>> publishedDiagnostics = new HashMap<>();
    private Inlays codeLenses;

    private final class GutterActions implements EditorGutterAction {
        public GutterActions(List<? extends CodeLens> lenses) {
            this.lenses = new LinkedHashMap<>();
            lenses.forEach(cl -> this.lenses.put(cl.getRange().getStart().getLine(), cl));
        }

        private final Map<Integer, CodeLens> lenses;

        @Override
        public void doAction(int i) {
            if (lenses.get(i) != null) {
                Command c = lenses.get(i).getCommand();
                ExecuteCommandParams params = new ExecuteCommandParams();
                params.setCommand(c.getCommand());
                params.setArguments(c.getArguments());
                server.getWorkspaceService().executeCommand(params);
            }
        }

        @Override
        public Cursor getCursor(int i) {
            return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
    }

    private final class GutterAnnotations implements TextAnnotationGutterProvider {

        public GutterAnnotations(List<? extends CodeLens> lenses) {
            this.lenses = new LinkedHashMap<>();
            lenses.forEach(cl -> this.lenses.put(cl.getRange().getStart().getLine(), cl));
        }

        private final Map<Integer, CodeLens> lenses;

        @Nullable
        @Override
        public String getToolTip(int i, Editor editor) {
            if (lenses.containsKey(i)) {
                final Command cmd = lenses.get(i).getCommand();
                StringBuffer msg = new StringBuffer(cmd.getCommand());
                msg.append("(");
                if (cmd.getArguments() != null) {
                    cmd.getArguments().forEach(s -> {
                        msg.append(s.toString()).append(" ");
                    });
                }
                msg.append(")");
                return msg.toString();
            } else {
                return null;
            }
        }

        @Nullable
        @Override
        public String getLineText(int i, Editor editor) {
            return lenses.containsKey(i) ? lenses.get(i).getCommand().getTitle() : null;
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
                    Command c = lenses.get(i).getCommand();
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

    public LanguageClient(Project project, LanguageServer server) {
        this(project);
        this.server = server;
    }

    public LanguageClient(Project project) {
        this.project = project;
        this.intentions = project.getComponent(QuickFixes.class);
        this.codeLenses = project.getComponent(Inlays.class);
        ProblemsView hack;
        try {
            hack = ProblemsView.SERVICE.getInstance(project);
        } catch (NoClassDefFoundError e) {
            hack = null;
        }
        this.pv = hack;
    }

    public void connect(LanguageServer server) {
        this.server = server;
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
            params.getEdit().getChanges().forEach((file, edits) -> Util.applyEdit(file, edits)));
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

        project.getComponent(DiagnosticProvider.class).updateDiagnostics();
    }

    public void showDiagnostics(VirtualFile vf){
        if (!publishedDiagnostics.containsKey(vf)){
            return;
        }
        Document doc = Util.getDocument(vf);
        List<Diagnostic> diagnostics = publishedDiagnostics.get(vf);

        if (pv != null) {
            UUID myUUID = UUID.randomUUID();
            CompileScope s = new FileSetCompileScope(Collections.singleton(vf), new Module[0]);
            pv.clearOldMessages(s, myUUID);
            diagnostics.forEach(diag -> {
                Range rng = diag.getRange();
                Position start = rng.getStart();
                int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
                Navigatable x = new OpenFileDescriptor(project, vf, startOffset);
                CompilerMessage msg = new CompilerMessage() {
                    @NotNull
                    @Override
                    public CompilerMessageCategory getCategory() {
                        DiagnosticSeverity severity = diag.getSeverity();
                        if (severity.equals(DiagnosticSeverity.Error))
                            return CompilerMessageCategory.ERROR;
                        if (severity.equals(DiagnosticSeverity.Warning))
                            return CompilerMessageCategory.WARNING;
                        return CompilerMessageCategory.INFORMATION;
                    }

                    @Override
                    public String getMessage() {
                        if (diag.getSource() != null)
                            return diag.getMessage() + " [" + diag.getSource() + "]";
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
                pv.addMessage(msg, myUUID);
            });
        }

        intentions.clear();
        diagnostics.forEach(diag -> intentions.addDiagnostic(doc, vf.getUrl(), diag, server));

        Editor[] editors = EditorFactory.getInstance().getEditors(doc, project);
        for (Editor editor : editors) {
            clearMarkup(editor);
        }
        editorListeners.clear();

        for (Editor editor : editors) {
 //           MarkupModel markup = editor.getMarkupModel();
 //           markup.removeAllHighlighters();
            diagnostics.forEach(diag -> {
                showMarkup(diag, editor, doc);
            });
        }

        CodeLensParams clp = new CodeLensParams();
        TextDocumentIdentifier tdi = new TextDocumentIdentifier();
        tdi.setUri(vf.getUrl());
        clp.setTextDocument(tdi);
        server.getTextDocumentService().codeLens(clp).thenAccept(cls -> {

            codeLenses.clear();
            cls.forEach(cl -> {
                codeLenses.addLens(doc, cl, server);
            });

            ApplicationManager.getApplication().invokeLater(() -> {
                GutterAnnotations gutter = new GutterAnnotations(cls);
                GutterActions actions = new GutterActions(cls);
                for (Editor e : editors) {
                    e.getGutter().closeAllAnnotations();
                    e.getGutter().registerTextAnnotation(gutter, actions);
                }
            });
        });
    }

    private Balloon currentHint = null;
    private Diagnostic currHintDiag = null;
    private TooltipGroup magpieGroup = new TooltipGroup("magpie", 1);

    private void showMarkup(Diagnostic diag, Editor editor, Document doc) {
        int flags = 0;// HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_OTHER_HINT | HintManager.HIDE_BY_SCROLLING;

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
                        if (msg.length() > 200) {
                            msg = msg.substring(0, 95) + "...";
                        }
                        if(diag.getSource()!=null)
                            msg="["+ diag.getSource()+"] "+msg;
                        JLabel label= new JLabel(msg);
                        label.setBackground(Color.lightGray);
                        JComponent hintText =label;
                        //show related information
                        List<DiagnosticRelatedInformation> info = diag.getRelatedInformation();
                        if (info != null && info.size() > 0) {
                            JPanel p = new JPanel();
                            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
                            p.add(hintText);
                            //p.add(new JLabel("Related Information"));
                            JPanel relInfoPanel = new JPanel();
                            relInfoPanel.setLayout(new GridLayout(0,2));
                            p.add(relInfoPanel);
                            info.sort(Comparator.comparingInt(d -> d.getLocation().getRange().getStart().getLine()));
                            info.forEach(d->{
                                String fileName = d.getLocation().getUri();
                                String[] pathParts = fileName.split("/|\\\\");
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
//                        LightweightHint hint = new LightweightHint(hintText);
////                        Point p = HintManagerImpl.getHintPosition(hint, editor, editor.offsetToLogicalPosition(offset), HintManager.ABOVE);
////                        HintManagerImpl.getInstanceImpl().showEditorHint(
////                                hint,
////                                editor,
////                                p,
////                                flags,
////                                100000,
////                                false,
////                                HintManagerImpl.createHintHint(editor,
////                                p,
////                                hint,
////                                HintManager.ABOVE).setContentActive(true));

                        HighlightInfo.Builder hi = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION);
                        hi.unescapedToolTip("tip");
                        hi.range(10, 250);
                        HighlightInfo h = hi.create();
                        DaemonTooltipUtil.showInfoTooltip(h, editor, 0);

                        if (currentHint != null){
                            currentHint.dispose();
                        }
                        currentHint = JBPopupFactory.getInstance().createBalloonBuilder(hintText).createBalloon();
                        currentHint.show(new RelativePoint(e.getMouseEvent()), Balloon.Position.above);
                        currHintDiag = diag;
                        currentHint.addListener(new JBPopupListener() {
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

        if (! editorListeners.containsKey(editor)) {
            editorListeners.put(editor, new HashSet<>());
        }

        editorListeners.get(editor).add(l);
    }

    private void clearMarkup(Editor editor) {
        if (editorListeners.containsKey(editor)) {
            editorListeners.remove(editor).forEach(l -> editor.removeEditorMouseMotionListener(l));
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
        try {
            SwingUtilities.invokeAndWait(() -> {
                List<String> options = new ArrayList<>();
                params.getActions().forEach((a) -> options.add(a.getTitle()));
                String[] x = options.toArray(new String[params.getActions().size()]);
                choice[0] = Messages.showDialog(params.getMessage(),
                        params.getType().toString(),
                        x,
                        0,
                        Messages.getQuestionIcon());
            });
        } catch (InterruptedException | InvocationTargetException e) {
            assert false :  e;
        }
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

    @Override
    public void showHTML(MessageParams messageParams) {
        JFrame frame = new JFrame(messageParams.getType().toString());
        frame.setUndecorated(true);
        frame.setOpacity(.7F);
        frame.setResizable(true);

        JFXPanel jfxPanel = new JFXPanel();
        frame.add(jfxPanel);

        frame.setSize(200, 200);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Point loc = MouseInfo.getPointerInfo().getLocation();
        frame.setLocation(loc.x, loc.y);

        PlatformImpl.runLater(() -> {
             WebView view = new WebView();
             Scene scene = new Scene(view);
             jfxPanel.setScene(scene);
             view.getEngine().loadContent(messageParams.getMessage());
             int wd = Math.max((int)view.widthProperty().floatValue(), 400);
             int hl = Math.max((int)view.heightProperty().floatValue(), 300);
             frame.setSize(wd,hl);
            frame.setVisible(true);
            frame.setAlwaysOnTop(true);

            EventHandler<? super KeyEvent> h = scene.getOnKeyPressed();
             scene.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode()== KeyCode.ESCAPE) {
                    frame.setVisible(false);
                } else {
                    if (h != null) {
                        h.handle(e);
                    }
                }
             });
        });
    }
}
