package magpiebridge.intellij.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import io.netty.util.concurrent.Promise;
import magpiebridge.intellij.client.LanguageClient;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.logging.Logger;

public class ServerLauncher {

    private static final Logger LOGGER = Logger.getLogger(ServerLauncher.class.getName());

    private static Set<Project> projects = new HashSet<>();
    private static Map<Project, Process> processes = new ConcurrentHashMap<>();
    private static Map<Project, Socket> sockets = new ConcurrentHashMap<>();
    private static Map<Project, Service> services = new ConcurrentHashMap<>();
    private static Map<Project, Future> listening = new ConcurrentHashMap<>();


        public static void launch(Project project) {

        projects.add(project);
        PropertiesComponent pc = PropertiesComponent.getInstance();
        String channel = pc.getValue(Configuration.CHANNEL);
        if (channel == null){
            return;
        }
        String path = pc.getValue(Configuration.PATH);
        String currentPath = System.getenv("PATH");
        String newPath=currentPath;
        if(path!=null&&!path.equals(currentPath))
          newPath=currentPath+File.pathSeparator+path;
        String jarPath = pc.getValue(Configuration.JAR);
        String cpPath = pc.getValue(Configuration.CP);
        String mainClass = pc.getValue(Configuration.MAIN);
        String extraArgs = pc.getValue(Configuration.ARGS);
        String dir = pc.getValue(Configuration.DIR);
        String jvm = pc.getValue(Configuration.JVM);
        String host = pc.getValue(Configuration.HOST);
        String port = pc.getValue(Configuration.PORT);
        boolean commandOptionOne = pc.getBoolean(Configuration.COMMANDOPTION);

        ProcessBuilder proc = new ProcessBuilder();
        proc.environment().put("PATH",newPath);
        if (dir != null && !dir.isEmpty()) {
            File df = new File(dir);
            if (df.exists() && df.isDirectory()) {
                proc.directory(df);
            } else {
                dir = project.getBasePath();
            }
        } else {
            dir = project.getBasePath();
        }
        LanguageClient client = new LanguageClient(project);
        org.eclipse.lsp4j.jsonrpc.Launcher<LanguageServer> serverLauncher = null;

        if (Channel.STDIO.equals(channel)) {
            List<String> args = new ArrayList<>();
            args.add(jvm);
            if ( commandOptionOne ) {
                if (jarPath != null && !jarPath.isEmpty()){
                    args.add("-jar");
                    args.add(jarPath);
                }
            } else {
                if (cpPath != null && !cpPath.isEmpty()) {
                    args.add("-cp");
                    args.add(cpPath);
                }
                if (mainClass != null && !mainClass.isEmpty()) {
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
            try {

                Process process = proc.start();
                serverLauncher =
                        LSPLauncher.createClientLauncher(client,
                                logStream(process.getInputStream(), "lsp.in.txt"),
                                logStream(process.getOutputStream(), "lsp.out.txt"));
                processes.put(project, process);
            } catch (Exception e) {
                Notifications.Bus.notify(new Notification("lsp", "Error", "Could not launch language server:\n" + e.getMessage(), NotificationType.ERROR), project);
                return;
            }
        } else if (Channel.SOCKET.equals(channel)) {
            try {

                Socket socket = new Socket(host, Integer.parseInt(port));
                serverLauncher = LSPLauncher.createClientLauncher(client, logStream(socket.getInputStream(), "lsp.in.txt"),
                        logStream(socket.getOutputStream(), "lsp.out.txt"));
                sockets.put(project, socket);
            } catch (Exception e) {
                Notifications.Bus.notify(new Notification("lsp", "Error", "Could not launch language server:\n" + e.getMessage(), NotificationType.ERROR), project);
                return;
            }
        } else {
            Notifications.Bus.notify(new Notification("lsp", "Error", "Could not launch language server:\nChannel " + channel + " not supported", NotificationType.ERROR), project);
            return;
        }
        LanguageServer lspServer = serverLauncher.getRemoteProxy();
        client.connect(lspServer);
        Future<Void> listener = serverLauncher.startListening();
        listening.put(project, listener);
        services.put(project, new Service(project, lspServer, client));
    }

    public static void reload() {
        new Thread(() -> {
            CountDownLatch l = new CountDownLatch(projects.size());
            //shutdown all active servers
            for (Project activeProject : projects) {
                shutDown(activeProject, l::countDown);
            }
            try {
                //wait for all servers to be shut down
                l.await();
            } catch (InterruptedException e) {
                LOGGER.warning(e.getMessage());
            }
            // relaunch servers with new settings
            projects.forEach(ServerLauncher::launch);
            projects.forEach(project -> Notifications.Bus.notify(new Notification("lsp", "Info", "LSP Connection reloaded", NotificationType.INFORMATION), project) );
        }).start();
    }

    public static void closeProject(Project project) {
        projects.remove(project);
        shutDown(project, () -> services.remove(project));
    }

    public static void shutDown(Project p, Runnable onFinish) {
        Service service = services.get(p);
        if (service == null) {
            onFinish.run();
            return;
        }
        service.shutDown(() -> {
            try {
                Process process = processes.remove(p);
                Socket socket = sockets.remove(p);
                Future listener = listening.remove(p);
                if (listener != null){
                    //cancel listening on messageprocessor to avoid exception
                    listener.cancel(true);
                }
                if ( process != null) {
                    // only call exit for processes
                    service.exit();
                    process.destroy();
                    services.remove(p);
                }
                if (socket != null){
                    socket.close();
                    services.remove(p);
                }
            } catch (Throwable t) {
                Notifications.Bus.notify(
                        new Notification("lsp",
                                NotificationType.WARNING.toString(),
                                "There was an error while trying to shutdown the language server for Project " + p.getName(),
                                NotificationType.WARNING),
                        p);
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
