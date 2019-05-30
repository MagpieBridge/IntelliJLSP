package org.magpiebridge.intellij.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.magpiebridge.intellij.client.LanguageClient;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

public class Launcher {

    static InputStream logStream(InputStream is, String logFileName) {
        File log;
        try {
            log = File.createTempFile(logFileName, ".txt");
            return new TeeInputStream(is, new FileOutputStream(log));
        } catch (IOException e) {
            return is;
        }
    }

    static OutputStream logStream(OutputStream os, String logFileName) {
        File log;
        try {
            log = File.createTempFile(logFileName, ".txt");
            return new TeeOutputStream(os, new FileOutputStream(log));
        } catch (IOException e) {
            return os;
        }
    }

    public static void launch(Project p) throws IOException {
        PropertiesComponent pc = PropertiesComponent.getInstance();
        String jarPath = pc.getValue(Configuration.JAR);
        String cpPath = pc.getValue(Configuration.CP);
        String mainClass = pc.getValue(Configuration.MAIN);
        String extraArgs = pc.getValue(Configuration.ARGS);
        String dir = pc.getValue(Configuration.DIR);
        String jvm = pc.getValue(Configuration.JVM);

        ProcessBuilder proc = new ProcessBuilder();

        if (dir != null) {
            File df = new File(dir);
            if (df.exists() && df.isDirectory()) {
                proc.directory(df);
            }
        }

        List<String> args = new ArrayList<>();
        args.add(jvm);
        if (jarPath != null && !"".equals(jarPath)) {
            args.add("-jar");
            args.add(jarPath);
        } else {
            args.add("-cp");
            args.add(cpPath);
            args.add(mainClass);
        }
        if (extraArgs != null) {
            StringTokenizer toks = new StringTokenizer(extraArgs);
            while (toks.hasMoreTokens()) {
                args.add(toks.nextToken());
            }
        }
        proc.command(args);

        proc.redirectError(new File(dir, "out.txt"));

        Process server = proc.start();
        
        LanguageClient client = new LanguageClient(p);
        org.eclipse.lsp4j.jsonrpc.Launcher<LanguageServer> serverLauncher =
                LSPLauncher.createClientLauncher(client,
                        logStream(server.getInputStream(), "lsp.in.txt"),
                        logStream(server.getOutputStream(), "lsp.out.txt"));
        serverLauncher.startListening();

        LanguageServer lspServer = serverLauncher.getRemoteProxy();
        client.connect(lspServer);

        new Service(p, lspServer, client);
    }
}
