package lsp4intellij;

import org.wso2.lsp4intellij.client.connection.ProcessStreamConnectionProvider;
import org.wso2.lsp4intellij.client.connection.StreamConnectionProvider;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.RawCommandServerDefinition;

import java.io.File;
import java.util.Collections;

public class SocketServerDefinition extends LanguageServerDefinition {

  private String host;
  private int port;

  public SocketServerDefinition(String ext, String host, int port) {
    this.languageIds = Collections.emptyMap();
    this.ext = ext;

    this.host = host;
    this.port = port;
  }

  @Override
  public StreamConnectionProvider createConnectionProvider(String workingDir) {
    return new SocketStreamConnectionProvider(host, port);
  }
}
