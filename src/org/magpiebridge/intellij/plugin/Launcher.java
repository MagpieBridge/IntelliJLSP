package org.magpiebridge.intellij.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.apache.commons.io.IOUtils;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.magpiebridge.intellij.client.LanguageClient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

public class Launcher {

    public static void launch(Project p) throws IOException {
        PropertiesComponent pc = PropertiesComponent.getInstance();
        String jarPath = pc.getValue(Configuration.JAR);
        String cpPath = pc.getValue(Configuration.CP);
        String mainClass = pc.getValue(Configuration.MAIN);
        String extraArgs = pc.getValue(Configuration.ARGS);
        String dir = pc.getValue(Configuration.DIR);
        String jvm = pc.getValue(Configuration.JVM);

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

        String[] aa = args.toArray(new String[args.size()]);

        Process server;
        if (dir != null && !"".equals(dir)) {
            File pwd = new File(dir);
            if (pwd.exists() && pwd.isDirectory()) {
                server = Runtime.getRuntime().exec(aa, new String[0], pwd);
            } else {
                server = Runtime.getRuntime().exec(aa);
            }
        } else {
            server = Runtime.getRuntime().exec(aa);
        }

        new Thread(() -> {
            try {
                FileWriter fw = new FileWriter(File.createTempFile("out", ".txt"));
                fw.write(Arrays.toString(aa));
                fw.write("\n");
                fw.flush();
                IOUtils.copy(server.getErrorStream(), fw, System.getProperty("file.encoding"));
            } catch (IOException exp) {
                assert false : exp.getMessage();
            }
        }).start();

        LanguageClient client = new LanguageClient(p);
        org.eclipse.lsp4j.jsonrpc.Launcher<LanguageServer> serverLauncher =
                LSPLauncher.createClientLauncher(client, server.getInputStream(), server.getOutputStream());
        serverLauncher.startListening();

        LanguageServer lspServer = serverLauncher.getRemoteProxy();
        client.connect(lspServer);

        new Service(p, lspServer, client);

    }
}