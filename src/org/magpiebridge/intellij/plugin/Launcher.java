package org.magpiebridge.intellij.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.magpiebridge.intellij.client.LanguageClient;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Launcher {

    private static final Logger LOGGER = Logger.getLogger(Launcher.class.getName());

    private static Map<Project, Process> processes = new ConcurrentHashMap<>();
    private static Map<Project, Service> services = new ConcurrentHashMap<>();


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
            if (cpPath != null) {
                args.add("-cp");
                args.add(cpPath);
            }
            if (mainClass != null) {
                args.add(mainClass);
            }
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

        services.put(p, new Service(p, lspServer, client));
        processes.put(p, server);
    }

    public static void reload() {
        CountDownLatch l = new CountDownLatch(processes.size());
        List<Project> activeProjects =new ArrayList<>(processes.keySet());
        //shutdown all active servers
        for (Project activeProject : activeProjects) {
            shutDown(activeProject, l::countDown);
        }
        try {
            //wait for all servers to be shut down
            l.await();
        } catch (InterruptedException e) {
            LOGGER.warning(e.getMessage());
        }
        // relaunch servers with new settings
        activeProjects.forEach(p -> {
            try {
                launch(p);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public static void shutDown(Project p) {
        shutDown(p, () -> {
        });
    }

    public static void shutDown(Project p, Runnable onFinish) {
        Service service = services.get(p);
        if (service == null){
            onFinish.run();
            return;
        }
        service.shutDown(() -> {
            try {
                Process server = processes.get(p);
                if (server != null) {
                    server.destroy();
                }
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "There was an error while trying to shutdown the language server for Project " + p.getName(), t);
            }
            onFinish.run();
        });
    }
}
