
package magpiebridge.intellij.test;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import magpiebridge.intellij.client.MagpieLanguageClient;
import org.eclipse.lsp4j.*;

import java.util.Collections;

public class TestApplyEdit extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        FileEditor editor = e.getData(PlatformDataKeys.FILE_EDITOR);

        EchoServer server = new EchoServer();
        MagpieLanguageClient lc = new MagpieLanguageClient(project, server);
        server.connect(lc);

        ApplyWorkspaceEditParams params = new ApplyWorkspaceEditParams();
        WorkspaceEdit edit = new WorkspaceEdit();
        TextEdit te = new TextEdit();
        te.setNewText("something random, like " + Math.random());
        Position s = new Position();
        s.setLine(1);
        s.setCharacter(2);
        Position x = new Position();
        x.setLine(1);
        x.setCharacter(8);
        Range r = new Range();
        r.setStart(s);
        r.setEnd(x);
        te.setRange(r);
        edit.setChanges(Collections.singletonMap(editor.getFile().getCanonicalPath(), Collections.singletonList(te)));
        params.setEdit(edit);

        lc.applyEdit(params);
    }
}
