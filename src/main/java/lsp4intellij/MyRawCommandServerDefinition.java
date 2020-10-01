package lsp4intellij;

import org.wso2.lsp4intellij.client.connection.ProcessStreamConnectionProvider;
import org.wso2.lsp4intellij.client.connection.StreamConnectionProvider;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.RawCommandServerDefinition;

import java.io.File;
import java.util.Arrays;

public class MyRawCommandServerDefinition extends RawCommandServerDefinition {
  private String path;

  public MyRawCommandServerDefinition(String ext, String[] command, String path) {
    super(ext, command);
    this.path = path;
  }

  @Override
  public StreamConnectionProvider createConnectionProvider(String workingDir) {

    ProcessBuilder procBuilder = new ProcessBuilder(command);
    procBuilder.environment().put("PATH", path);
    procBuilder.directory(new File(workingDir));
    procBuilder.redirectError(new File(workingDir, "MagpieBridgeLSPSupportError.txt"));

    return new ProcessStreamConnectionProvider(procBuilder);
  }

}
