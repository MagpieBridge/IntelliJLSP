package lsp4intellij;

import com.intellij.build.FileNavigatable;
import com.intellij.build.FilePosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.io.File;

public class LSPNavigatable extends FileNavigatable {

  public LSPNavigatable(Project project, Location location) {
    this(project, getFile(location), location.getRange());
  }

  public LSPNavigatable(Project project, File file, Range range) {
    super(project, new FilePosition(file, range.getStart().getLine(), range.getStart().getCharacter(), range.getEnd().getLine(), range.getEnd().getCharacter()));
  }

  public static File getFile(Location location) {
    final VirtualFile vf = FileUtils.virtualFileFromURI(location.getUri());
    if(vf == null){
      // TODO: improve!
      return new File("file-not-found");
    }
    return vf.toNioPath().toFile();
  }
}
