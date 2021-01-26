package magpiebridge.intellij.plugin;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.google.gson.JsonObject;
import com.intellij.AppTopics;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightweightHint;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import magpiebridge.intellij.client.MagpieLanguageClient;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeActionCapabilities;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKindCapabilities;
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandCapabilities;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.HoverCapabilities;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsCapabilities;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Service {

  private final int flags =
      HintManager.HIDE_BY_ANY_KEY
          | HintManager.HIDE_BY_TEXT_CHANGE
          | HintManager.HIDE_BY_OTHER_HINT
          | HintManager.HIDE_BY_SCROLLING;

  private final class GutterActions implements EditorGutterAction {
    public GutterActions(List<? extends CodeLens> lenses) {
      this.lenses = new LinkedHashMap<>();
      lenses.forEach(cl -> this.lenses.put(cl.getRange().getStart().getLine(), cl));
    }

    private final Map<Integer, CodeLens> lenses;

    @Override
    public void doAction(int i) {
      Command c = lenses.get(i).getCommand();
      ExecuteCommandParams params = new ExecuteCommandParams();
      params.setCommand(c.getCommand());
      params.setArguments(c.getArguments());
      server.getWorkspaceService().executeCommand(params);
    }

    @Override
    public Cursor getCursor(int i) {
      return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }
  }

  public Service(Project project, LanguageServer server, LanguageClient lc) {
    this.project = project;
    this.server = server;
    this.codeActions = project.getComponent(QuickFixes.class);

    if (server instanceof LanguageClientAware) {
      ((LanguageClientAware) server).connect(lc);
    }

    String rootPath = project.getBasePath();
    InitializeParams init = createInitializeParams(rootPath);
    server
        .initialize(init)
        .thenAccept(
            ir -> {
              assert ir.getCapabilities().getCodeActionProvider().getLeft();

              InitializedParams ip = new InitializedParams();
              server.initialized(ip);

              MessageBus bus = project.getMessageBus();
              MessageBusConnection busStop = bus.connect();

              busStop.subscribe(
                  AppTopics.FILE_DOCUMENT_SYNC,
                  new FileDocumentManagerListener() {
                    @Override
                    public void beforeDocumentSaving(@NotNull Document document) {
                      VirtualFile file = FileDocumentManager.getInstance().getFile(document);
                      DidSaveTextDocumentParams params = new DidSaveTextDocumentParams();
                      TextDocumentIdentifier doc = new TextDocumentIdentifier();
                      doc.setUri(file.getUrl());
                      params.setText(document.getText());
                      params.setTextDocument(doc);
                      server.getTextDocumentService().didSave(params);
                    }

                    @Override
                    public void fileContentReloaded(
                        @NotNull VirtualFile file, @NotNull Document document) {}

                    @Override
                    public void fileContentLoaded(
                        @NotNull VirtualFile file, @NotNull Document document) {}
                  });

              busStop.subscribe(
                  FileEditorManagerListener.FILE_EDITOR_MANAGER,
                  new FileEditorManagerListener() {

                    private void open(@NotNull VirtualFile file) {
                      Document intelliJDoc = FileDocumentManager.getInstance().getDocument(file);
                      assert intelliJDoc != null;

                      DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
                      TextDocumentItem doc = new TextDocumentItem();

                      doc.setUri(Util.fixUrl(file.getUrl()));

                      doc.setLanguageId(file.getExtension());
                      doc.setText(intelliJDoc.getText());
                      params.setTextDocument(doc);

                      server.getTextDocumentService().didOpen(params);

                     /* CodeLensParams clp = new CodeLensParams();
                      TextDocumentIdentifier tdi = new TextDocumentIdentifier();
                      tdi.setUri(Util.fixUrl(file.getUrl()));
                      clp.setTextDocument(tdi);
                      server
                          .getTextDocumentService()
                          .codeLens(clp)
                          .thenAccept(
                              cls -> {
                                ApplicationManager.getApplication()
                                    .runReadAction(
                                        () -> {
                                          GutterAnnotations gutter = new GutterAnnotations(cls);
                                          GutterActions actions = new GutterActions(cls);
                                          for (Editor e :
                                              EditorFactory.getInstance()
                                                  .getEditors(intelliJDoc, project)) {
                                            e.getGutter().registerTextAnnotation(gutter, actions);
                                          }
                                        });
                              });
                        */

                        CodeLensParams clp = new CodeLensParams();
                        TextDocumentIdentifier tdi = new TextDocumentIdentifier();
                        tdi.setUri(Util.fixUrl(file.getUrl()));
                        clp.setTextDocument(tdi);
                        server.getTextDocumentService().codeLens(clp).thenAccept(cls -> {
                            cls.forEach(cl -> {
                                Range clr = cl.getRange();
                                Position clpos = clr.getEnd();
                                for (Editor e : EditorFactory.getInstance().getEditors(intelliJDoc, project)) {
                                    e.getInlayModel().addInlineElement(
                                        intelliJDoc.getLineStartOffset(clpos.getLine()) + clpos.getCharacter(),
                                        new EditorCustomElementRenderer() {
                                            @Override
                                            public int calcWidthInPixels(@NotNull Inlay inlay) {
                                                  return 35;
                                              }

                                              @Override
                                              public int calcHeightInPixels(@NotNull Inlay inlay) {
                                                  return 20;
                                              }

                                              @Override
                                              public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle targetRegion, @NotNull TextAttributes textAttributes) {
                                                  Editor editor = inlay.getEditor();
                                                  g.setColor(JBColor.GRAY);
                                                  g.drawString(cl.getCommand().getTitle(), targetRegion.x, targetRegion.y + targetRegion.height);
                                              }
                                          });
                              };
                           });
                      });


                      for (Editor e :
                          EditorFactory.getInstance().getEditors(intelliJDoc, project)) {
                        e.addEditorMouseMotionListener(
                            new EditorMouseMotionListener() {
                              @Override
                              public void mouseMoved(@NotNull EditorMouseEvent ev) {
                                if (ev.getArea().equals(EditorMouseEventArea.EDITING_AREA)) {
                                  TextDocumentPositionParams pos = new TextDocumentPositionParams();
                                  Position mp = new Position();
                                  int offset =
                                      e.logicalPositionToOffset(
                                          e.xyToLogicalPosition(ev.getMouseEvent().getPoint()));
                                  LogicalPosition logicalPos = e.offsetToLogicalPosition(offset);
                                  int line = intelliJDoc.getLineNumber(offset);
                                  int col = offset - intelliJDoc.getLineStartOffset(line);
                                  mp.setLine(line);
                                  mp.setCharacter(col);
                                  pos.setPosition(mp);
                                  TextDocumentIdentifier id = new TextDocumentIdentifier();
                                  String uri = Util.fixUrl(file.getUrl());
                                  id.setUri(uri);
                                  pos.setTextDocument(id);
                                  CodeActionParams codeActionParams = new CodeActionParams();
                                  Range range = new Range(mp, mp);
                                  codeActionParams.setRange(range);
                                  codeActionParams.setTextDocument(id);
                                  codeActionParams.setContext(
                                      new CodeActionContext(new ArrayList<Diagnostic>()));
                                  server
                                      .getTextDocumentService()
                                      .codeAction(codeActionParams)
                                      .thenAccept(
                                          actions -> {
                                            if (actions.size() >= 0) {
                                              codeActions.addCodeActions(
                                                  Util.getDocument(file), range, server, actions);
                                            }
                                          });

                                  HoverParams hov = new HoverParams(id, mp);
                                  server
                                      .getTextDocumentService()
                                      .hover(hov)
                                      .thenAccept(
                                          h -> {
                                            if (h != null) {
                                              String text = "";
                                              if (h.getContents().isLeft()) {
                                                for (Either<String, MarkedString> str :
                                                    h.getContents().getLeft()) {
                                                  if (str.isRight()) {
                                                    MarkedString ms = str.getRight();
                                                    text += ms;
                                                  } else {
                                                    text += str.getLeft();
                                                  }
                                                }
                                              } else {
                                                MarkupContent mc = h.getContents().getRight();
                                                text += mc.getValue();
                                              }
                                              LightweightHint hint =
                                                  new LightweightHint(new JLabel(text));
                                              Point p =
                                                  HintManagerImpl.getHintPosition(
                                                      hint, e, logicalPos, HintManager.ABOVE);
                                              HintManagerImpl.getInstanceImpl()
                                                  .showEditorHint(
                                                      hint,
                                                      e,
                                                      p,
                                                      flags,
                                                      -1,
                                                      true,
                                                      HintManagerImpl.createHintHint(
                                                              e, p, hint, HintManager.ABOVE)
                                                          .setContentActive(true));
                                            }
                                          });
                                }
                              }
                            });
                      }

                      if (lc instanceof MagpieLanguageClient) {
                        ((MagpieLanguageClient) lc).showDiagnostics(file);
                      }
                    }

                    @Override
                    public void fileOpened(
                        @NotNull FileEditorManager source, @NotNull VirtualFile file) {
                      open(file);
                    }

                    @Override
                    public void fileClosed(
                        @NotNull FileEditorManager source, @NotNull VirtualFile file) {
                      DidCloseTextDocumentParams params = new DidCloseTextDocumentParams();
                      TextDocumentIdentifier doc = new TextDocumentIdentifier();
                      doc.setUri(Util.fixUrl(file.getUrl()));
                      params.setTextDocument(doc);
                      server.getTextDocumentService().didClose(params);
                    }
                  });
            });
  }

    private final LanguageServer server;

  private final Project project;
  private QuickFixes codeActions;

  private InitializeParams createInitializeParams(String rootPath) {
      // TODO. add other capabilities
      InitializeParams init = new InitializeParams();
      init.setRootUri(
          Util.fixUrl(rootPath.startsWith("/") ? "file:" + rootPath : "file:///" + rootPath));
      init.setTrace("verbose");
      ClientCapabilities clientCapabilities = new ClientCapabilities();
      JsonObject publishDiagnostics = new JsonObject();
      publishDiagnostics.addProperty("relatedInformation", true);
      TextDocumentClientCapabilities textCap = new TextDocumentClientCapabilities();
      PublishDiagnosticsCapabilities pubCap = new PublishDiagnosticsCapabilities();
      pubCap.setRelatedInformation(true);
      textCap.setPublishDiagnostics(pubCap);
      HoverCapabilities hover =  new HoverCapabilities();
      hover.setContentFormat(Collections.singletonList(MarkupKind.MARKDOWN));
      textCap.setHover(hover);
      CodeActionCapabilities caCap = new CodeActionCapabilities();
      CodeActionLiteralSupportCapabilities support = new CodeActionLiteralSupportCapabilities();
      CodeActionKindCapabilities kind = new CodeActionKindCapabilities();
      List<String> kinds = new ArrayList<>();
      kinds.add("");
      kinds.add("quickfix");
      kinds.add("source");
      kind.setValueSet(kinds);
      support.setCodeActionKind(kind);
      caCap.setCodeActionLiteralSupport(support);
      textCap.setCodeAction(caCap);
      clientCapabilities.setTextDocument(textCap);
      WorkspaceClientCapabilities workCap = new WorkspaceClientCapabilities();
      ExecuteCommandCapabilities exeCap = new ExecuteCommandCapabilities();
      exeCap.setDynamicRegistration(true);
      workCap.setExecuteCommand(exeCap);
      clientCapabilities.setWorkspace(workCap);
      JsonObject experimental = new JsonObject();
      experimental.addProperty("supportsShowHTML", true);
      experimental.addProperty("supportsShowInputBox", true);
      clientCapabilities.setExperimental(experimental);
      init.setCapabilities(clientCapabilities);
      return init;
  }

  private final class GutterAnnotations implements TextAnnotationGutterProvider {

    public GutterAnnotations(List<? extends CodeLens> lenses) {
      this.lenses = new LinkedHashMap<>();
      lenses.forEach(cl -> this.lenses.put(cl.getRange().getStart().getLine(), cl));
    }

    private final Map<Integer, CodeLens> lenses;

    @Nullable
    @Override
    public String getToolTip(int i, Editor editor) {
      if (lenses.containsKey(i)) {
        StringBuffer msg = new StringBuffer(lenses.get(i).getCommand().getCommand());
        msg.append("(");
        lenses
            .get(i)
            .getCommand()
            .getArguments()
            .forEach(
                s -> {
                  msg.append(s.toString()).append(" ");
                });
        msg.append(")");
        return msg.toString();
      } else {
        return null;
      }
    }

    @Nullable
    @Override
    public String getLineText(int i, Editor editor) {
        String lineText = lenses.containsKey(i) ? lenses.get(i).getCommand().getCommand() : null;
        return lineText;
    }

    @Override
    public EditorFontType getStyle(int i, Editor editor) {
      return EditorFontType.BOLD;
    }

    @Nullable
    @Override
    public ColorKey getColor(int i, Editor editor) {
      return ColorKey.createColorKey("LSP", Color.BLUE);
    }

    @Nullable
    @Override
    public Color getBgColor(int i, Editor editor) {
      return editor.getColorsScheme().getDefaultBackground();
    }

    @Override
    public List<AnAction> getPopupActions(int i, Editor editor) {
      return Collections.singletonList(
          new AnAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
              Command c = lenses.get(i).getCommand();
              ExecuteCommandParams params = new ExecuteCommandParams();
              params.setCommand(c.getCommand());
              params.setArguments(c.getArguments());
              server.getWorkspaceService().executeCommand(params);
            }
          });
    }

    @Override
    public void gutterClosed() {}
  }

  public void shutDown(Runnable andThen) {
    Timer timer = new Timer();
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            andThen.run();
          }
        },
        3000); // run andThen after timeout of 3 seconds, if server does not respond to shutdown
    server
        .shutdown()
        .thenRunAsync(
            () -> {
              timer.cancel();
              andThen.run();
            });
  }

  public void exit() {
    server.exit();
  }
}
