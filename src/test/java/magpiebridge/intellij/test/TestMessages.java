package magpiebridge.intellij.test;


import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

import java.util.Arrays;

public class TestMessages extends AnAction {

    public TestMessages() {
        super("Test Messages");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
/*        Project project = e.getData(PlatformDataKeys.PROJECT);

        EchoServer server = new EchoServer();
        LanguageClient lc = new LanguageClient(project, server);
        server.connect(lc);

        MessageParams sm = new MessageParams();
        sm.setMessage("nothing");
        sm.setType(MessageType.Info);

        lc.logMessage(sm);

        ShowMessageRequestParams smr = new ShowMessageRequestParams();
        smr.setMessage("nothing");
        smr.setType(MessageType.Warning);
        MessageActionItem a1 = new MessageActionItem();
        a1.setTitle("don't care");
        MessageActionItem a2 = new MessageActionItem();
        a2.setTitle("do care");
        smr.setActions(Arrays.asList(a1, a2));

        lc.showMessageRequest(smr).thenAccept((r) -> {
            String s = r.getTitle();
            assert s.equals(a1.getTitle()) || s.equals(a2.getTitle());
        });

        lc.showMessage(sm);
 */
    }
}
