package magpiebridge.intellij.test;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EchoServer implements LanguageServer, LanguageClientAware {
    private LanguageClient client;

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams initializeParams) {
        InitializeResult ir = new InitializeResult();
        ServerCapabilities sc = new ServerCapabilities();
        sc.setCodeActionProvider(true);
        ir.setCapabilities(sc);

        return CompletableFuture.completedFuture(ir);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return null;
    }

    @Override
    public void exit() {

    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return new TextDocumentService() {
            @Override
            public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
                if (position.getPosition().getLine() > 15 && position.getPosition().getCharacter()> 10) {
                    Hover h = new Hover();
                    Range r = new Range();
                    r.setStart(position.getPosition());
                    r.setEnd(position.getPosition());
                    h.setRange(r);
                    MarkupContent m = new MarkupContent();
                    m.setKind("text/html");
                    m.setValue("<html><h4>some text</h4></html>");
                    h.setContents(m);
                    return CompletableFuture.completedFuture(h);
                } else {
                    return CompletableFuture.completedFuture(null);
                }
            }

            @Override
            public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
                CodeLens cl = new CodeLens();
                Command c = new Command();
                c.setTitle("Echo");
                c.setCommand("ECHO");
                c.setArguments(Collections.singletonList("arg"));
                cl.setCommand(c);
                cl.setData("data");
                Position s = new Position();
                s.setLine(6);
                s.setCharacter(2);
                Position x = new Position();
                x.setLine(7);
                x.setCharacter(10);
                Range r = new Range();
                r.setStart(s);
                r.setEnd(x);
                cl.setRange(r);
                return CompletableFuture.completedFuture(Collections.singletonList(cl));
            }

            @Override
            public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
                CodeAction action = new CodeAction();
                action.setDiagnostics(params.getContext().getDiagnostics());
                action.setTitle("fix");
                action.setKind("repair");

                WorkspaceEdit we = new WorkspaceEdit();
                TextEdit te = new TextEdit();
                te.setRange(params.getContext().getDiagnostics().get(0).getRange());
                te.setNewText("XXXX");
                we.setChanges(Collections.singletonMap(params.getTextDocument().getUri(), Collections.singletonList(te)));
                action.setEdit(we);

                Command fix = new Command();
                fix.setCommand("fix");
                fix.setTitle("replace with 'XXXX'");
                fix.setArguments(Collections.singletonList(params.getContext().getDiagnostics().get(0)));
                action.setCommand(fix);
                return CompletableFuture.completedFuture(Collections.singletonList(Either.forRight(action)));
            }

            private final Pattern word = Pattern.compile("\\b[a-z]+\\b");

            private void fakeAnalysis(String uri, String text) {
                String[] lines = text.split("\n");

                int issue = 0;
                List<Diagnostic> diags = new ArrayList<>();
                for(int i = 0; i < lines.length; i += 10) {
                    Matcher m = word.matcher(lines[i]);
                    if (m.find()) {
                        Position start = new Position();
                        start.setLine(i);
                        start.setCharacter(m.start());
                        Position end = new Position();
                        end.setLine(i);
                        end.setCharacter(m.end());
                        Range dr = new Range();
                        dr.setStart(start);
                        dr.setEnd(end);

                        Diagnostic d = new Diagnostic();
                        d.setRange(dr);
                        d.setMessage("issue " + (issue++));
                        d.setSeverity(DiagnosticSeverity.Information);
                        d.setSource("EchoServer");

                        diags.add(d);
                    }
                }

                PublishDiagnosticsParams pdp = new PublishDiagnosticsParams();
                pdp.setUri(uri);
                pdp.setDiagnostics(diags);

                client.publishDiagnostics(pdp);
            }

            @Override
            public void didOpen(DidOpenTextDocumentParams didOpenTextDocumentParams) {
                MessageParams params = new MessageParams();
                params.setType(MessageType.Info);
                params.setMessage(didOpenTextDocumentParams.getTextDocument().getUri());
                client.showMessage(params);
                fakeAnalysis(didOpenTextDocumentParams.getTextDocument().getUri(), didOpenTextDocumentParams.getTextDocument().getText());
            }

            @Override
            public void didChange(DidChangeTextDocumentParams didChangeTextDocumentParams) {

            }

            @Override
            public void didClose(DidCloseTextDocumentParams didCloseTextDocumentParams) {

            }

            @Override
            public void didSave(DidSaveTextDocumentParams didSaveTextDocumentParams) {

            }
        };
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return new WorkspaceService() {
            @Override
            public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
                if (params.getCommand().equals("fix")) {
                    Diagnostic d = (Diagnostic) params.getArguments().get(0);
                    MessageParams x = new MessageParams();
                    x.setMessage(d.toString());
                    x.setType(MessageType.Info);
                    client.showMessage(x);
                }
                return null;
            }

            @Override
            public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
                return null;
            }

            @Override
            public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {

            }

            @Override
            public void didChangeConfiguration(DidChangeConfigurationParams didChangeConfigurationParams) {

            }

            @Override
            public void didChangeWatchedFiles(DidChangeWatchedFilesParams didChangeWatchedFilesParams) {

            }
        };
    }

    @Override
    public void connect(LanguageClient languageClient) {
        this.client = languageClient;
    }

    public static void main(String[] args) {
        EchoServer es = new EchoServer();
        Launcher<LanguageClient> clientLauncher =
                LSPLauncher.createServerLauncher(es, System.in, System.out);
        es.connect(clientLauncher.getRemoteProxy());
        clientLauncher.startListening();

    }
}
