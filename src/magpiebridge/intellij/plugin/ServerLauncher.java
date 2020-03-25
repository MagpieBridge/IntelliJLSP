package magpiebridge.intellij.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import magpiebridge.intellij.client.LanguageClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerLauncher {

    private static final Logger LOGGER = Logger.getLogger(ServerLauncher.class.getName());

    private static Map<Project, Process> processes = new ConcurrentHashMap<>();
    private static Map<Project, Service> services = new ConcurrentHashMap<>();

    public static void launch(Project project) throws IOException {
        PropertiesComponent pc = PropertiesComponent.getInstance();
        String jarPath = pc.getValue(Configuration.JAR);
        String cpPath = pc.getValue(Configuration.CP);
        String mainClass = pc.getValue(Configuration.MAIN);
        String extraArgs = pc.getValue(Configuration.ARGS);
        String dir = pc.getValue(Configuration.DIR);
        String jvm = pc.getValue(Configuration.JVM);
        String channel=pc.getValue(Configuration.CHANNEL);
        String host= pc.getValue(Configuration.HOST);
        String port= pc.getValue(Configuration.PORT);
        ProcessBuilder proc = new ProcessBuilder();
        if (dir != null) {
            File df = new File(dir);
            if (df.exists() && df.isDirectory()) {
                proc.directory(df);
            }
        }
        else
            dir = project.getBasePath();
        LanguageClient client = new LanguageClient(project);
        org.eclipse.lsp4j.jsonrpc.Launcher<LanguageServer> serverLauncher=null;

        if(channel.equals(Channel.STDIO)) {
            List<String> args = new ArrayList<>();
            args.add(jvm);
            if (jarPath != null && !jarPath.isEmpty()) {
                args.add("-jar");
                args.add(jarPath);
            } else {
                if (cpPath != null&&!cpPath.isEmpty()) {
                    args.add("-cp");
                    args.add(cpPath);
                }
                if (mainClass != null&&!mainClass.isEmpty()) {
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
            proc.redirectError(new File(dir, "MagpieBridgeLSPSupportError.txt"));
            Process process = proc.start();
            serverLauncher =
                    LSPLauncher.createClientLauncher(client,
                            logStream(process.getInputStream(), "lsp.in.txt"),
                            logStream(process.getOutputStream(), "lsp.out.txt"));
            processes.put(project, process);
        }
        else if(channel.equals(Channel.SOCKET))
        {
            Socket socket=new Socket(host, Integer.parseInt(port));
            serverLauncher=LSPLauncher.createClientLauncher(client,logStream(socket.getInputStream(), "lsp.in.txt"),
                    logStream(socket.getOutputStream(), "lsp.out.txt"));
        }
        else
        {
            throw new RuntimeException("Channel "+channel +" not supported");
        }
        LanguageServer lspServer = serverLauncher.getRemoteProxy();
        client.connect(lspServer);
        serverLauncher.startListening();
        services.put(project, new Service(project, lspServer, client));
    }

    public static void reload() {
        new Thread(()-> {
            CountDownLatch l = new CountDownLatch(services.size());
            List<Project> activeProjects = new ArrayList<>(services.keySet());
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
        }).start();
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


    private static InputStream logStream(InputStream is, String logFileName) {
        File log;
        try {
            log = File.createTempFile(logFileName, ".txt");
            return new TeeInputStream(is, new FileOutputStream(log));
        } catch (IOException e) {
            return is;
        }
    }

    private static OutputStream logStream(OutputStream os, String logFileName) {
        File log;
        try {
            log = File.createTempFile(logFileName, ".txt");
            return new TeeOutputStream(os, new FileOutputStream(log));
        } catch (IOException e) {
            return os;
        }
    }

}
