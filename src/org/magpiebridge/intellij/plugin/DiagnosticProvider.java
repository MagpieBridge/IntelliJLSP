package org.magpiebridge.intellij.plugin;

import com.ibm.wala.util.collections.HashMapFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class DiagnosticProvider extends MagpieDocumentationProvider {

    private final Map<Document, NavigableMap<Integer, String>> lowerToHTML = HashMapFactory.make();
    private final Map<Document, NavigableMap<Integer, String>> upperToHTML = HashMapFactory.make();

    public void updateDiagnostics() {
        service.client.publishedDiagnostics.entrySet().forEach(e -> {
            Document doc = Util.getDocument(e.getKey());

            if (lowerToHTML.containsKey(doc)) {
                lowerToHTML.remove(doc);
                upperToHTML.remove(doc);
            }

            for (Diagnostic diag : e.getValue()) {
                addDiagnostic(doc, diag);
            }
        });
    }

    private void addDiagnostic(Document doc, Diagnostic diag) {
        Range rng = diag.getRange();
        Position start = rng.getStart();
        int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
        Position end = rng.getEnd();
        int endOffset = doc.getLineStartOffset(end.getLine()) + end.getCharacter();

        String msg = diag.getMessage();
        if (diag.getSource()!=null) {
            msg = "[" + diag.getSource() + "] " + msg;
        }
        if (msg.length() > 200) {
            msg = msg.substring(0, 95) + "...";
        }

        if (!lowerToHTML.containsKey(doc)) {
            lowerToHTML.put(doc, new TreeMap<>());
        }
        lowerToHTML.get(doc).put(startOffset, msg);

        if (!upperToHTML.containsKey(doc)) {
            upperToHTML.put(doc, new TreeMap<>());
        }
        upperToHTML.get(doc).put(endOffset, msg);
    }

    @Override
    protected String generateInternal(@NotNull PsiElement element, @Nullable PsiElement originalElement) throws Exception {
        int startOffset = originalElement.getTextRange().getStartOffset();
        int endOffset = originalElement.getTextRange().getEndOffset();
        int offset = (endOffset + startOffset) / 2;

        VirtualFile vf = originalElement.getContainingFile().getVirtualFile();
        Document doc = FileDocumentManager.getInstance().getDocument(vf);

        if (lowerToHTML.containsKey(doc) && lowerToHTML.get(doc).floorEntry(offset) != null) {
            if (upperToHTML.get(doc).ceilingEntry(offset) != null) {
                String l = lowerToHTML.get(doc).floorEntry(offset).getValue();
                String u = upperToHTML.get(doc).ceilingEntry(offset).getValue();
                if (l != null && l.equals(u)) {
                    return l;
                }
            }
        }

        return null;
    }
}
