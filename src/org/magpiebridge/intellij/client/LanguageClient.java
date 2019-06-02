// line to hack
// another line to hack
package org.magpiebridge.intellij.client;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.compiler.ProblemsView;
import com.intellij.compiler.impl.CompileScopeUtil;
import com.intellij.compiler.progress.CompilerTask;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.components.ServiceManager;
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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.table.JBTable;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.magpiebridge.intellij.plugin.QuickFixes;
import org.magpiebridge.intellij.plugin.Util;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.Color;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    private final ProblemsView pv;
    private final QuickFixes intentions;

    public LanguageClient(Project project, LanguageServer server) {
        this(project);
        this.server = server;
    }

    public LanguageClient(Project project) {
        this.project = project;
        this.pv = ProblemsView.SERVICE.getInstance(project);
        this.intentions = project.getComponent(QuickFixes.class);
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
        int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_OTHER_HINT | HintManager.HIDE_BY_SCROLLING;

        final TextAttributes attr = new TextAttributes();
        attr.setEffectColor(JBColor.RED);
        attr.setEffectType(EffectType.WAVE_UNDERSCORE);

        String file = params.getUri();
        VirtualFile vf = Util.getVirtualFile(file);

        Document doc = Util.getDocument(vf);

        Editor[] editors = EditorFactory.getInstance().getEditors(doc, project);

        UUID uuid =  UUID.randomUUID();
        pv.clearOldMessages(null, uuid);
        params.getDiagnostics().forEach((diag) -> {
            Range rng = diag.getRange();
            Position start = rng.getStart();
            int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
            Navigatable x = new OpenFileDescriptor(project, vf, startOffset);
            CompilerMessage msg = new CompilerMessage() {
                @NotNull
                @Override
                public CompilerMessageCategory getCategory() {
                    return  CompilerMessageCategory.INFORMATION;
                }

                @Override
                public String getMessage() {
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
                    return "MagpieBridge";
                }
            };
            pv.addMessage(msg, uuid);
        });

        intentions.clear();
        params.getDiagnostics().forEach((diag) -> {
            intentions.addDiagnostic(doc, params.getUri(), diag, server);
        });

        for (Editor editor : editors) {
            if (editorListeners.containsKey(editor)) {
                editorListeners.get(editor).forEach(l -> editor.removeEditorMouseMotionListener(l));
            }
            WriteCommandAction.runWriteCommandAction(project, () -> {
                MarkupModel markup = editor.getMarkupModel();
                markup.removeAllHighlighters();
            });
        }
        editorListeners.clear();

        params.getDiagnostics().forEach((diag) -> {
            Range rng = diag.getRange();
            Position start = rng.getStart();
            int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
            Position end = rng.getEnd();
            int endOffset = doc.getLineStartOffset(end.getLine()) + end.getCharacter();

            for (Editor editor : editors) {
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
                                String msg = diag.getMessage();
                                if (msg.length() > 100) {
                                    msg = msg.substring(0, 95) + "...";
                                }
                                JComponent hintText = new JLabel(msg);
                                if (diag.getRelatedInformation() != null && diag.getRelatedInformation().size() > 0) {
                                    List<DiagnosticRelatedInformation> info = diag.getRelatedInformation();
                                    JPanel p = new JPanel();
                                    p.setLayout(new GridLayout(3, 1));
                                    p.add(hintText);
                                    p.add(new JSeparator());
                                    p.add(
                                            new JBTable(new AbstractTableModel() {
                                                public int getColumnCount() {
                                                    return 2;
                                                }

                                                public int getRowCount() {
                                                    return info.size();
                                                }

                                                public Object getValueAt(int row, int col) {
                                                    DiagnosticRelatedInformation d = diag.getRelatedInformation().get(row);
                                                    return col == 0 ? d.getLocation().toString() : d.getMessage();
                                                }
                                            }));
                                    hintText = p;
                                }
                                LightweightHint hint = new LightweightHint(hintText);
                                Point p = HintManagerImpl.getHintPosition(hint, editor, editor.offsetToLogicalPosition(offset), HintManager.ABOVE);
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
        });
    }

    @Override
    public void showMessage(@NotNull MessageParams messageParams) {
        showMessage(messageParams.getType() + ": " + messageParams.getMessage());
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

}
