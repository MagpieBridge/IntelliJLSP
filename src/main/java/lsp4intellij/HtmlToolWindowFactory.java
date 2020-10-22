package lsp4intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.utils.ApplicationUtils;

import javax.annotation.Nonnull;
import javax.swing.*;

// [ms] hint: if we implement a Condition class and reference in plugin.xml we can show the tab only if there is sth to show.
public final class HtmlToolWindowFactory implements ToolWindowFactory {
  private static final String ID = "Magpie Control Panel";    // needs to be the same as in plugin.xml

  // private static WebView htmlViewer = null;
  private static JBCefBrowser browser = null;
  private static String htmlcontent = null;

  public static void show(@Nonnull Project project, @Nonnull String content) {

    boolean isHidden = true;
    final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(HtmlToolWindowFactory.ID);
    if( toolWindow != null) {
      toolWindow.show(() -> {
        if (htmlcontent != null) {
          // load cached html when users opens the tool window -> lazyload
          ApplicationUtils.invokeLater(() -> {
            browser.loadHTML(htmlcontent);
            htmlcontent = null;
          });
        }
      });
      isHidden = toolWindow.isActive() && !toolWindow.isVisible();
    }

    // not initialized/opened yet or hidden -> lazyload: update HTML when user opens the panel
    if( browser == null || isHidden){
      htmlcontent = content;
      showUiUpdate(project);
    }else {
      ApplicationUtils.invokeLater(() -> {
        browser.loadHTML(content);
      });

      /*
      Platform.runLater(()->{
        htmlViewer.getEngine().setJavaScriptEnabled(true);
        htmlViewer.getEngine().loadContent(content);
      });
      */
    }
  }

  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
      final JComponent browserComponent;
      if (JBCefApp.isSupported()) {
        browserComponent = initBrowser(project);
        Disposer.register(toolWindow.getDisposable(), browser);
      }else{
        browserComponent = new JLabel("JCEF Client is not supported in your IDE. Please Upgrade.");
      }
      ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
      Content content = contentFactory.createContent(browserComponent, "", false);
      toolWindow.getContentManager().addContent(content);

  }

    static JComponent initBrowser(@NotNull Project project) {
      browser = new JBCefBrowser();
    browser.loadHTML(htmlcontent != null ? htmlcontent : "<html> Nothing to show. </html>");
      return browser.getComponent();

    /*
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(HtmlToolWindowFactory.ID, false, ToolWindowAnchor.BOTTOM);
    toolWindow.getComponent().add( browser.getComponent());

    JFXPanel fxPanel = new JFXPanel();
    JComponent component = toolWindow.getComponent();
    Platform.setImplicitExit(false);
    Platform.runLater(() -> {
      Group root  =  new Group();
      Scene scene  =  new  Scene(root, javafx.scene.paint.Color.WHITE);
      htmlViewer = new WebView();
      htmlViewer.getEngine().loadContent("<html>Hello World :)</html>");
      htmlViewer.setPrefWidth( toolWindow.getComponent().getWidth() );
      root.getChildren().add(htmlViewer);
      fxPanel.setScene(scene);
    });
    return fxPanel;
    */

  }

  private static void showUiUpdate(Project project){
    final ToolWindowManager instance = ToolWindowManager.getInstance(project);
    if( instance.canShowNotification(HtmlToolWindowFactory.ID)) {
      instance.notifyByBalloon(HtmlToolWindowFactory.ID, MessageType.INFO, "Update");
    }
  }
}