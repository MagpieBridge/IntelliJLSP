package org.magpiebridge.intellij.plugin;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;

public class Util {

    public static String fixUrl(String urlStr) {
        try {
            URL url = new URL(urlStr.replace(":", "%3A"));
            URI uri = Paths.get(url.toURI()).toUri();
            if (uri.getScheme().equalsIgnoreCase("file")) {
                uri = Paths.get(uri).toUri();
            }
            return uri.toString();
        } catch (MalformedURLException | URISyntaxException e) {
            return urlStr;
        }
     }

    public static Document getDocument(VirtualFile vf) {
        Document[] hack = new Document[1];
        ReadAction.run(() -> { hack[0] = FileDocumentManager.getInstance().getDocument(vf); });
        return hack[0];
    }

    @NotNull
    public static VirtualFile getVirtualFile(String file) {
        VirtualFile vf = VirtualFileManager.getInstance().findFileByUrl(file);
        if (vf == null) {
            vf = LocalFileSystem.getInstance().findFileByIoFile(new File(file));
        }
        if (vf == null && file.startsWith("file:")) {
            File ugh = new File(file.substring(5));
            if (ugh.exists()) {
                vf = LocalFileSystem.getInstance().findFileByIoFile(ugh);
            }
        }
        assert vf != null;
        return vf;
    }

    public static void applyEdit(String file, List<TextEdit> edits) {
        VirtualFile vf = getVirtualFile(file);
        Document doc = FileDocumentManager.getInstance().getDocument(vf);
        for(int i = edits.size()-1; i >= 0; i--) {
            TextEdit edit = edits.get(i);
            Range rng = edit.getRange();
            Position start = rng.getStart();
            int startOffset = doc.getLineStartOffset(start.getLine()) + start.getCharacter();
            Position end = rng.getEnd();
            int endOffset = doc.getLineStartOffset(end.getLine()) + end.getCharacter();
            doc.replaceString(startOffset, endOffset, edit.getNewText());
        }
    }

    public static void doAction(Either<Command, CodeAction> c, Project project, LanguageServer server) {
        Command cmd;
        if (c.isRight()) {
            CodeAction ca = c.getRight();
            if (ca.getEdit() != null) {
                ca.getEdit().getChanges().forEach((file, edits) ->
                        WriteCommandAction.runWriteCommandAction(project, () ->
                                applyEdit(file, edits)));

                return;
            } else {
                cmd = ca.getCommand();
            }
        } else {
            cmd = c.getLeft();
        }

        ExecuteCommandParams params = new ExecuteCommandParams();
        params.setCommand(cmd.getCommand());
        params.setArguments(cmd.getArguments());
        server.getWorkspaceService().executeCommand(params);
    }

    com.intellij.lang.documentation.DocumentationProvider concat(com.intellij.lang.documentation.DocumentationProvider... ps) {
        return new DocumentationProvider() {
            @Nullable
            @Override
            public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
                return null;
            }

            @Nullable
            @Override
            public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
                return null;
            }

            @Nullable
            @Override
            public String generateHoverDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
                return null;
            }
        };
    }

}
