package magpiebridge.intellij.test;

import com.intellij.compiler.ProblemsView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import java.util.UUID;

public class TestProblemView extends AnAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    FileEditor editor = e.getData(PlatformDataKeys.FILE_EDITOR);

    ProblemsView pv = ServiceManager.getService(project, ProblemsView.class);
    UUID uuid = UUID.randomUUID();
    VirtualFile file = editor.getFile();

    pv.clearOldMessages(null, uuid);

    Navigatable x = new OpenFileDescriptor(project, file, 3);
    pv.addMessage(0, new String[] {"xx"}, "xyz", x, "s1", "s2", uuid);
  }
}
