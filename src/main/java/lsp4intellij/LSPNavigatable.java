package lsp4intellij;

import com.intellij.build.FileNavigatable;
import com.intellij.build.FilePosition;
import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.io.File;
import java.util.Objects;

public class LSPNavigatable extends FileNavigatable {

  public LSPNavigatable(Project project, Location location) {
    this(project, getFile(location), location.getRange());
  }

  @NotNull
  public static File getFile(Location location) {
    return Objects.requireNonNull(FileUtils.virtualFileFromURI(location.getUri())).toNioPath().toFile();
  }

  public LSPNavigatable(Project project, File file, Range range) {
    super(project, new FilePosition(file, range.getStart().getLine(), range.getStart().getCharacter(), range.getEnd().getLine(), range.getEnd().getCharacter()));
  }
}
