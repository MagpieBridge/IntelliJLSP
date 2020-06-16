package org.magpiebridge.intellij.plugin;

import com.ibm.wala.util.collections.HashSetFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class HoverProvider extends MagpieDocumentationProvider {

    @Nullable
    @Override
    public String generateInternal(@NotNull PsiElement element, @Nullable PsiElement originalElement) throws InterruptedException, ExecutionException, TimeoutException {
        int startOffset = originalElement.getTextRange().getStartOffset();
        int endOffset = originalElement.getTextRange().getEndOffset();
        int midOffset = (endOffset + startOffset) / 2;

        VirtualFile vf = originalElement.getContainingFile().getVirtualFile();
        Document doc = FileDocumentManager.getInstance().getDocument(vf);

        int line = doc.getLineNumber(midOffset);
        int col = midOffset - doc.getLineStartOffset(line);

        TextDocumentIdentifier id = new TextDocumentIdentifier();
        String uri = Util.fixUrl(vf.getUrl());
        id.setUri(uri);

        TextDocumentPositionParams pos = new TextDocumentPositionParams();
        Position mp = new Position();
        mp.setLine(line);
        mp.setCharacter(col);
        pos.setPosition(mp);
        pos.setTextDocument(id);

        Hover h = service.server.getTextDocumentService().hover(pos).get(5000, TimeUnit.MILLISECONDS);

        Set<String> kinds = HashSetFactory.make();
        String text = "";
        if (h != null) {
            if (h.getContents().isLeft()) {
                for (Either<String, MarkedString> str : h.getContents().getLeft()) {
                    if (str.isRight()) {
                        MarkedString ms = str.getRight();
                        kinds.add(ms.getLanguage());
                        text += ms.getValue();
                    } else {
                        text += str.getLeft();
                    }
                }
            } else {
                MarkupContent mc = h.getContents().getRight();
                kinds.add(mc.getKind());
                text += mc.getValue();
            }

            if ("".equals(text)) {
                return null;
            }

            if (kinds.size() == 1 && kinds.iterator().next().equals(MarkupKind.MARKDOWN)) {
                Parser parser = Parser.builder().build();
                Node document = parser.parse(text);
                HtmlRenderer renderer = HtmlRenderer.builder().build();
                text = renderer.render(document);
            }

            return text;

        }
        return null;
    }
}
