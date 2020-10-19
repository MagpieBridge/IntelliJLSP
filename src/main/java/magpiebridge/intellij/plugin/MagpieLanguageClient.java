package magpiebridge.intellij.plugin;

import com.google.gson.JsonObject;
import lsp4intellij.HtmlToolWindowFactory;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;
import org.wso2.lsp4intellij.client.ClientContext;
import org.wso2.lsp4intellij.client.DefaultLanguageClient;

public class MagpieLanguageClient extends DefaultLanguageClient implements LanguageClient {
  public MagpieLanguageClient(ClientContext context) {
    super(context);
  }

  @Override
  public InitializeParams getInitParams(String projectRootPath) {
    final InitializeParams initParams = super.getInitParams(projectRootPath);

    JsonObject showHTML = new JsonObject();
    showHTML.addProperty("supportsShowHTML", true);
    initParams.getCapabilities().setExperimental(showHTML);

    return initParams;
  }

  @JsonNotification("magpiebridge/showHTML")
  public void showHTML(String content) {
    HtmlToolWindowFactory.show(getContext().getProject(), content);
  }
}
