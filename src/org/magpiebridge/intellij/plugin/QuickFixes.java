package org.magpiebridge.intellij.plugin;

import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.*;

public class QuickFixes extends AbstractIntentionAction {

    public static class QuickFix {
        private Either<Command, CodeAction> action;

        private LanguageServer server;

        public QuickFix(Command action, LanguageServer server) {
            this(Either.forLeft(action), server);
        }

        public QuickFix(CodeAction action, LanguageServer server) {
            this(Either.forRight(action), server);
        }

        private QuickFix(Either<Command, CodeAction> action, LanguageServer server) {
            this.action = action;
            this.server = server;
        }

        public String toString() {
            return action.isLeft()?
                    action.getLeft().getTitle():
                    action.getRight().getEdit() != null?
                            "replace with '" + action.getRight().getEdit().getChanges().values().iterator().next().iterator().next().getNewText() + "'":
                            action.getRight().getCommand().getTitle();
        }

        public void act(Project project) {
            Util.doAction(action, project, server);
        }
    }

    private static final Map<Document, NavigableMap<Integer, List<QuickFix>>> lowerBound = new LinkedHashMap<>();
    private static final Map<Document, NavigableMap<Integer, List<QuickFix>>> upperBound = new LinkedHashMap<>();

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getText() {
        return "Code actions";
    }

    public void clear() {
        lowerBound.clear();
        upperBound.clear();
    }

    public void addDiagnostic(Document doc, String uri, Diagnostic diag, LanguageServer server) {
        CodeActionParams fixes = new CodeActionParams();
        fixes.setRange(diag.getRange());
        TextDocumentIdentifier id = new TextDocumentIdentifier();
        id.setUri(uri);
        fixes.setTextDocument(id);
        CodeActionContext context = new CodeActionContext();
        context.setDiagnostics(Collections.singletonList(diag));
        fixes.setContext(context);
        server.getTextDocumentService().codeAction(fixes).thenAccept(actions -> {
            addDiagnostic(doc, diag.getRange(), server, actions);
        });
    }

    private void addDiagnostic(Document doc, Range range, LanguageServer server, List<? extends Either<Command, CodeAction>> lenses) {
        Position start = range.getStart();
       int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
       Position end = range.getEnd();
       int endOffset = doc.getLineStartOffset(end.getLine()) + end.getCharacter();

       List<QuickFix> fixes = new ArrayList<>();
       lenses.forEach(c ->  {
           fixes.add(new QuickFix(c, server));
       });

       recordAction(doc, startOffset, endOffset, fixes);
    }

    public void addFix(@NotNull Document doc, @NotNull Range range, QuickFix... newText) {
        Position start = range.getStart();
        int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
        Position end = range.getEnd();
        int endOffset = doc.getLineStartOffset(end.getLine()) + end.getCharacter();

        IPopupChooserBuilder<QuickFix> popup = JBPopupFactory.getInstance().createPopupChooserBuilder(Arrays.asList(newText));

        recordAction(doc, startOffset, endOffset, Arrays.asList(newText));
    }

    public void addCodeActions(Document doc, Range range, LanguageServer server, List<? extends Either<Command, CodeAction>> actions) {
        Position start = range.getStart();
        int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
        Position end = range.getEnd();
        int endOffset = doc.getLineStartOffset(end.getLine()) + end.getCharacter();

        List<QuickFix> fixes = new ArrayList<>();
        actions.forEach(c ->  {
            fixes.add(new QuickFix(c, server));
        });

        recordAction(doc, startOffset, endOffset, (List<QuickFix>) fixes);
    }

    private void recordAction(Document doc, int startOffset, int endOffset, List<QuickFix> fixes) {
        if (!lowerBound.containsKey(doc)) {
            lowerBound.put(doc, new TreeMap<>());
        }
        lowerBound.get(doc).put(startOffset, fixes);
        if (!upperBound.containsKey(doc)) {
            upperBound.put(doc, new TreeMap<>());
        }
        upperBound.get(doc).put(endOffset, fixes);

        Logger.getInstance(getClass()).info("recordAction: fix from " + startOffset + " to " + endOffset + ": " + fixes);
        logStackTrace("recordAction: ");
    }

    @Nullable
    private Map.Entry<Integer,List<QuickFix>>[] getPopup(@NotNull Editor editor) {
        Document doc = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();

        Logger.getInstance(getClass()).info("getPopup: looking at " + offset + " of " + doc);

        if (lowerBound.containsKey(doc) && lowerBound.get(doc).floorEntry(offset) != null) {
            if (upperBound.get(doc).ceilingEntry(offset) != null) {
                Map.Entry<Integer,List<QuickFix>> l = lowerBound.get(doc).floorEntry(offset);
                Map.Entry<Integer,List<QuickFix>> u = upperBound.get(doc).ceilingEntry(offset);
                if (l.getValue() == u.getValue()) {
                    String nm = "getPopup: ";
                    Logger.getInstance(getClass()).info(nm + "found popup at " + u.getKey() + " " + l.getKey());
                    logStackTrace(nm);
                    return new Map.Entry[]{l, u};
                }
            }
        }

        return null;
    }

    private void logStackTrace(String nm) {
        Throwable x = new Throwable();
        x.fillInStackTrace();
        StringWriter w = new StringWriter();
        x.printStackTrace(new PrintWriter(w));
        Logger.getInstance(getClass()).info( nm + w.toString());
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return getPopup(editor) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
        Map.Entry<Integer,List<QuickFix>>[] pes = getPopup(editor);
        assert pes != null;
        List<QuickFix> fixes = pes[0].getValue();

        Logger.getInstance(getClass()).info("invoke: using fixes " + fixes);

        IPopupChooserBuilder<QuickFix> pp = JBPopupFactory.getInstance().createPopupChooserBuilder(fixes);
        pp.setItemChosenCallback(s -> WriteCommandAction.runWriteCommandAction(project, () -> s.act(project)));

        pp.createPopup().showInScreenCoordinates(editor.getComponent(), MouseInfo.getPointerInfo().getLocation());

      }
}
