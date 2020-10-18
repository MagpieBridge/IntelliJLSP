package lsp4intellij;

import javax.annotation.Nonnull;

public class HtmlToolWindow {


  // for the future: https://jetbrains.org/intellij/sdk/docs/reference_guide/jcef.html
  public static void init() {
    /* FIXME: add javafx to path
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).registerToolWindow("MagpieBridge Control Panel", false, ToolWindowAnchor.BOTTOM);
    JFXPanel fxPanel = new JFXPanel();
    JComponent component = toolWindow.getComponent();
    Platform.setImplicitExit(false);
    Platform.runLater(() -> {
      Group root  =  new Group();
      Scene scene  =  new  Scene(root, javafx.scene.paint.Color.WHITE);
      htmlViewer = new WebView();
      htmlViewer.getEngine().loadContent("<html>Hello World :)</html>");
      htmlViewer.setPrefWidth(1200);
      root.getChildren().add(htmlViewer);
      fxPanel.setScene(scene);
    });
    component.getParent().add(fxPanel);
    */
  }

  public static void show(@Nonnull String content) {
    /*Platform.runLater(()->{
      htmlViewer.getEngine().setJavaScriptEnabled(true);
      htmlViewer.getEngine().loadContent(content);
    })*/
    ;
  }

}
