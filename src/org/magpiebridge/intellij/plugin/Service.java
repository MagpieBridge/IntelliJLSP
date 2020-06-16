package org.magpiebridge.intellij.plugin;

import com.google.gson.JsonObject;
import com.intellij.AppTopics;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import magpiebridge.command.CodeActionCommand;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.magpiebridge.intellij.client.LanguageClient;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Service {

    Balloon currentHint;
    String currentHintText;

    private final int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_OTHER_HINT | HintManager.HIDE_BY_SCROLLING;

    final LanguageServer server;
    final LanguageClient client;

    private final Project project;
    private QuickFixes codeActions;
    private Inlays codeLenses;

    public Service(Project project, LanguageServer server, LanguageClient lc) {
        this.project = project;
        this.server = server;
        this.client = lc;
        this.codeActions = project.getComponent(QuickFixes.class);
        this.codeLenses = project.getComponent(Inlays.class);

        for(String s : new String[]{"JAVA", "Python"}) {
            Language lang = Language.findLanguageByID(s);
            if (lang != null) {
                DocumentationProvider current = LanguageDocumentation.INSTANCE.forLanguage(lang);
                DocumentationProvider dp = project.getComponent(DiagnosticProvider.class).setServer(this).setBase(current);
                DocumentationProvider hp = new HoverProvider().setServer(this).setBase(dp);

                LanguageDocumentation.INSTANCE.addExplicitExtension(lang, hp);
            }
        }

        if (server instanceof LanguageClientAware) {
            ((LanguageClientAware) server).connect(lc);
        }

        String rootPath = project.getBasePath();
        InitializeParams init = new InitializeParams();
        init.setRootUri(Util.fixUrl(rootPath.startsWith("/") ? "file:" + rootPath : "file:///" + rootPath));
        init.setTrace("verbose");

        ClientCapabilities cap = init.getCapabilities();
        if (cap == null) {
            cap = new ClientCapabilities();
            init.setCapabilities(cap);
        }

        JsonObject exp = new JsonObject();
        exp.addProperty("supportsShowHTML", true);
        cap.setExperimental(exp);

        TextDocumentClientCapabilities txt = cap.getTextDocument();
        if (txt == null) {
            txt = new TextDocumentClientCapabilities();
            cap.setTextDocument(txt);
        }

        HoverCapabilities hover = new HoverCapabilities();
        hover.setContentFormat(Arrays.asList(MarkupKind.MARKDOWN));
        txt.setHover(hover);

        server.initialize(init).thenAccept(ir -> {
                    MessageBus bus = project.getMessageBus();
                    MessageBusConnection busStop = bus.connect();

                    busStop.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener() {
                        private int version = 0;

                        @Override
                        public void beforeDocumentSaving(@NotNull Document document) {
                            VirtualFile file = FileDocumentManager.getInstance().getFile(document);

                            VersionedTextDocumentIdentifier vdoc = new VersionedTextDocumentIdentifier();
                            vdoc.setUri(file.getUrl());
                            vdoc.setVersion(++version);
                            DidChangeTextDocumentParams changeParams = new DidChangeTextDocumentParams();
                            changeParams.setTextDocument(vdoc);
                            TextDocumentContentChangeEvent c = new TextDocumentContentChangeEvent();
                            c.setRange(null);
                            c.setText(document.getText());
                            changeParams.setContentChanges(Collections.singletonList(c));
                            server.getTextDocumentService().didChange(changeParams);

                            TextDocumentIdentifier doc = new TextDocumentIdentifier();
                            doc.setUri(file.getUrl());
                            DidSaveTextDocumentParams saveParams = new DidSaveTextDocumentParams();
                            saveParams.setText(document.getText());
                            saveParams.setTextDocument(doc);
                            server.getTextDocumentService().didSave(saveParams);
                        }

                        @Override
                        public void fileContentReloaded(@NotNull VirtualFile file, @NotNull Document document) {

                        }

                        @Override
                        public void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) {

                        }
                    });

                    busStop.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {

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

                            for (Editor e : EditorFactory.getInstance().getEditors(intelliJDoc, project)) {
                                e.addEditorMouseMotionListener(new EditorMouseMotionListener() {
                                    @Override
                                    public void mouseMoved(@NotNull EditorMouseEvent e) {
                                        handleEvent(e);
                                    }

                                    @Override
                                    public void mouseDragged(@NotNull EditorMouseEvent e) {
                                        handleEvent(e);
                                    }

                                    private void handleEvent(@NotNull EditorMouseEvent ev) {
                                        if (ev.getArea().equals(EditorMouseEventArea.EDITING_AREA)) {
                                            TextDocumentPositionParams pos = new TextDocumentPositionParams();
                                            Position mp = new Position();
                                            int offset = e.logicalPositionToOffset(e.xyToLogicalPosition(ev.getMouseEvent().getPoint()));
                                            int line = intelliJDoc.getLineNumber(offset);
                                            int col = offset - intelliJDoc.getLineStartOffset(line);
                                            mp.setLine(line);
                                            mp.setCharacter(col);
                                            pos.setPosition(mp);
                                            TextDocumentIdentifier id = new TextDocumentIdentifier();
                                            String uri = Util.fixUrl(file.getUrl());
                                            id.setUri(uri);
                                            pos.setTextDocument(id);

                                            try {
                                                Hover h = server.getTextDocumentService().hover(pos).get(5000, TimeUnit.MILLISECONDS);
                                                String text = "";
                                                if (h != null) {
                                                    if (h.getContents().isLeft()) {
                                                        for (Either<String, MarkedString> str : h.getContents().getLeft()) {
                                                            if (str.isRight()) {
                                                                MarkedString ms = str.getRight();
                                                                text += ms.getValue();
                                                            } else {
                                                                text += str.getLeft();
                                                            }
                                                        }
                                                    } else {
                                                        MarkupContent mc = h.getContents().getRight();
                                                        text += mc.getValue();
                                                    }

                                                    Parser parser = Parser.builder().build();
                                                    Node document = parser.parse(text);
                                                    HtmlRenderer renderer = HtmlRenderer.builder().build();
                                                    String html = renderer.render(document);

                                                    JEditorPane render = new JEditorPane();
                                                    render.setContentType("text/html");
                                                    render.setText(html);

                                                    if (!html.equals(currentHintText)) {
                                                        currentHintText = html;

                                                        if (currentHint != null) {
                                                            currentHint.dispose();
                                                        }

                                                        currentHint = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(html, MessageType.INFO, new HyperlinkListener() {
                                                            @Override
                                                            public void hyperlinkUpdate(HyperlinkEvent e) {
                                                                URL url = e.getURL();
                                                                if (e.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
                                                                    ExecuteCommandParams cmd = new ExecuteCommandParams();
                                                                    cmd.setCommand(CodeActionCommand.openURL.name());
                                                                    cmd.setArguments(Arrays.asList(url.toExternalForm()));
                                                                    server.getWorkspaceService().executeCommand(cmd);
                                                                }
                                                            }
                                                        }).createBalloon();

                                                        currentHint.show(new RelativePoint(ev.getMouseEvent()), Balloon.Position.above);
                                                        currentHint.addListener(new JBPopupListener() {
                                                            @Override
                                                            public void onClosed(@NotNull LightweightWindowEvent event) {
                                                                if (currentHint != null) {
                                                                    currentHint.dispose();
                                                                    currentHint = null;
                                                                    currentHintText = null;
                                                                }
                                                            }
                                                        });
                                                    }
                                                }
                                            } catch (InterruptedException | ExecutionException | TimeoutException e) {

                                            }
                                        }
                                    }
                                });
                            }

                            if (lc instanceof org.magpiebridge.intellij.client.LanguageClient) {
                                ((org.magpiebridge.intellij.client.LanguageClient) lc).showDiagnostics(file);
                            }
                        }

                        @Override
                        public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                            open(file);
                        }

                        @Override
                        public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {

                        }
                    });
                }

        ).join();

        InitializedParams ip = new InitializedParams();
        server.initialized(ip);
    }

    public void shutDown(Runnable andThen) {
        server.shutdown().thenRunAsync(andThen);
    }

    public static Service getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, Service.class);
    }
}
