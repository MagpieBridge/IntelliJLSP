package org.magpiebridge.intellij.test;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.magpiebridge.intellij.client.LanguageClient;
import org.magpiebridge.intellij.plugin.Configuration;
import org.magpiebridge.intellij.plugin.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

public class TestConfiguredServer extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        try {
            Project p = e.getProject();

            PropertiesComponent pc = PropertiesComponent.getInstance();
            String jarPath = pc.getValue(Configuration.JAR);
            String cpPath = pc.getValue(Configuration.CP);
            String mainClass = pc.getValue(Configuration.MAIN);
            String extraArgs = pc.getValue(Configuration.ARGS);
            String dir = pc.getValue(Configuration.DIR);

            String javaHome = System.getProperty("java.home");
            File javaPath = new File(javaHome, "bin/java");

            List<String> args = new ArrayList<>();
            args.add(javaPath.getCanonicalPath());
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

            try (FileWriter fw = new FileWriter(File.createTempFile("out"  , ".txt"))) {
                fw.write(Arrays.toString(aa));
            }

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

            LanguageClient client = new LanguageClient(p);
            Launcher<LanguageServer> serverLauncher =
                    LSPLauncher.createClientLauncher(client, server.getInputStream(), server.getOutputStream());
            serverLauncher.startListening();

            LanguageServer lspServer = serverLauncher.getRemoteProxy();
            client.connect(lspServer);

            new Service(p, lspServer, client);

        } catch (IOException exc) {
            assert false : exc.getMessage();
        }
    }
}
