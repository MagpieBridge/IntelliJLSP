package lsp4intellij;

import org.wso2.lsp4intellij.client.connection.StreamConnectionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;

public class SocketStreamConnectionProvider implements StreamConnectionProvider {

  private final String host;
  private final int port;
  private Socket socket;
  private InputStream inputStream;
  private OutputStream outputStream;

  public SocketStreamConnectionProvider(String host, int port) {
    this.host = host;
    this.port = port;
  }

  @Override
  public void start() throws IOException {
    socket = new Socket(host, port);
    inputStream = socket.getInputStream();
    outputStream = socket.getOutputStream();
  }

  @Override
  public InputStream getInputStream() {
    return inputStream;
  }

  @Override
  public OutputStream getOutputStream() {
    return outputStream;
  }

  @Override
  public void stop() {
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException e) {
      }
    }
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    return result ^ Objects.hashCode(this.port);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof SocketStreamConnectionProvider)) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    SocketStreamConnectionProvider other = (SocketStreamConnectionProvider) obj;
    return Objects.equals(this.port, other.port) && Objects.equals(this.socket, other.socket);
  }

  @Override
  public String toString() {
    return "ProcessOverSocketStreamConnectionProvider{" +
            "port=" + port +
            ", socket=" + socket +
            '}';
  }
}
