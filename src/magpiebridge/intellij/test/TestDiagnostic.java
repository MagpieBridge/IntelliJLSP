// a line to edit
// another line to edit
package magpiebridge.intellij.test;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import magpiebridge.intellij.client.LanguageClient;
import org.eclipse.lsp4j.*;

import java.util.Collections;

public class TestDiagnostic extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        FileEditor editor = e.getData(PlatformDataKeys.FILE_EDITOR);

        EchoServer server = new EchoServer();
        LanguageClient lc = new LanguageClient(project, server);
        server.connect(lc);

        PublishDiagnosticsParams params = new PublishDiagnosticsParams();
        params.setUri(editor.getFile().getCanonicalPath());
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
        DiagnosticRelatedInformation rel = new DiagnosticRelatedInformation();
        Position relp = new Position();
        relp.setLine(12);
        relp.setCharacter(2);
        Location relloc = new Location();
        Range relr = new Range();
        relr.setStart(relp);
        relr.setEnd(relp);
        relloc.setRange(relr);
        rel.setLocation(relloc);
        rel.setMessage("some message");
        diag.setRelatedInformation(Collections.singletonList(rel));
        params.setDiagnostics(Collections.singletonList(diag));

        lc.publishDiagnostics(params);
    }
}
