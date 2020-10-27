package lsp4intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
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

  public static void updateHtmlContent(@Nonnull Project project, @Nonnull String content) {

    final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(HtmlToolWindowFactory.ID);

    // not initialized/opened yet or hidden -> lazyload: update HTML when user opens the panel
    ApplicationUtils.invokeLater(() -> {
      if (browser == null || (toolWindow != null && toolWindow.isActive() && !toolWindow.isVisible())) {
        htmlcontent = content;
        showUiUpdate(project);
      } else {
        browser.loadHTML(content);
      }
    });
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


      project.getMessageBus().connect(toolWindow.getDisposable()).subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
        @Override
        public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
          final ToolWindow htmlToolWindow = toolWindowManager.getToolWindow(ID);
          if( htmlToolWindow != null && !htmlToolWindow.isVisible() ){
          //  browser.getComponent().setVisible(false);
          }
        }

        @Override
        public void toolWindowShown(@NotNull String id, @NotNull ToolWindow toolWindow) {
          if (htmlcontent != null) {
            // load cached html when users opens the tool window -> lazyload
            ApplicationUtils.invokeLater(() -> {
              browser.loadHTML(htmlcontent);
              htmlcontent = null;
         //     browser.getComponent().setVisible(true);
            });
          }

        }
      });

  }

    static JComponent initBrowser(@NotNull Project project) {
      browser = new JBCefBrowser();
      browser.loadHTML(htmlcontent != null ? htmlcontent : "<html> There is nothing to show at the moment. </html>");
      return browser.getComponent();
  }

  private static void showUiUpdate(Project project){
    final ToolWindowManager instance = ToolWindowManager.getInstance(project);
    if( instance.canShowNotification(HtmlToolWindowFactory.ID)) {
      instance.notifyByBalloon(HtmlToolWindowFactory.ID, MessageType.INFO, "Update");
    }
  }
}