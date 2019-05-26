package org.magpiebridge.intellij.test;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageServer;
import org.magpiebridge.intellij.plugin.QuickFixes;
import org.magpiebridge.intellij.plugin.QuickFixes.QuickFix;

import java.util.Arrays;
import java.util.Collections;

public class TestQuickFix extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        QuickFixes qf = project.getComponent(QuickFixes.class);

        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        Document doc = editor.getDocument();

        LanguageServer server = new EchoServer();

        FileEditor fe = e.getData(PlatformDataKeys.FILE_EDITOR);

        Diagnostic diag = new Diagnostic();
        diag.setMessage("nothing");
        Position s = new Position();
        s.setLine(10);
        s.setCharacter(2);
        Position x = new Position();
        x.setLine(10);
        x.setCharacter(8);
        Range r = new Range();
        r.setStart(s);
        r.setEnd(x);
        diag.setRange(r);

        Command cmd = new Command();
        cmd.setTitle("replace with 'XXXXXXXX'");
        cmd.setCommand("FIX");
        cmd.setArguments(Arrays.asList(r));
        QuickFix qf1 = new QuickFix(cmd, server);

        CodeAction ca = new CodeAction();
        ca.setDiagnostics(Arrays.asList(diag));
        ca.setCommand(cmd);
        ca.setTitle("replace with 'XXXXXXXX'");
        ca.setKind("Quick Fix");
        QuickFix qf2 = new QuickFix(ca, server);

        CodeAction cwe = new CodeAction();
        WorkspaceEdit we = new WorkspaceEdit();
        TextEdit te = new TextEdit();
        te.setRange(r);
        te.setNewText("XXXXXXXX");
        we.setChanges(Collections.singletonMap(fe.getFile().getUrl(), Collections.singletonList(te)));
        cwe.setEdit(we);
        QuickFix qf3 = new QuickFix(cwe, server);

        qf.addFix(doc, r, qf1, qf2, qf3);
    }
}
