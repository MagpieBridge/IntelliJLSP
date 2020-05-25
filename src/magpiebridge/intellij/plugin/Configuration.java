package magpiebridge.intellij.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.xmlbeans.impl.jam.JComment;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;

/**
 * Configuration for LSP Server.
 */
public class Configuration implements Configurable {

    protected static final String JAR = "lsp.jar";
    protected static final String CP = "java.cp";
    protected static final String MAIN = "main.class";
    protected static final String ARGS = "java.args";
    protected static final String DIR = "java.dir";
    protected static final String JVM = "java.jvm";
    protected static final String CHANNEL="lsp.channel";
    protected static final String HOST="lsp.host";
    protected static final String PORT="lsp.port";
    protected static final String PATH ="PATH";

    private JTextField jarField;
    private JTextField cpField;
    private JTextField mainField;
    private JTextField argsField;
    private JTextField dirField;
    private JTextField jvmField;
    private JTextField pathField;
    private JTextField hostField;
    private JTextField portField;
    private JRadioButton socketButton;

    private boolean isModified = false;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "MagpieBridge LSP Configuration";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        PropertiesComponent pc = PropertiesComponent.getInstance();
        String javaHome = System.getProperty("java.home");
        File javaPath = new File(javaHome, "bin/java");
        String jvm = pc.getValue(JVM, javaPath.getAbsolutePath());
        String path= pc. getValue(PATH, System.getenv("PATH"));
        String jarPath = pc.getValue(JAR, "");
        String cpPath = pc.getValue(CP, "");
        String mainClass = pc.getValue(MAIN, "");
        String args = pc.getValue(ARGS, "");
        String dir = pc.getValue(DIR, "");
        String host=pc.getValue(HOST, "");
        String port=pc.getValue(PORT,"");
        String channel = pc.getValue(CHANNEL, Channel.STDIO);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        FlowLayout flowLayout= new FlowLayout();
        flowLayout.setAlignment(FlowLayout.LEFT);

        DocumentListener dl = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {

                isModified = true;
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                isModified = true;
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                isModified = true;
            }
        };

        JPanel channelPanel = new JPanel();
        channelPanel.setLayout(flowLayout);
        JComponent channelLabel = new JLabel("LSP communication channel");
        JRadioButton stdioButton = new JRadioButton("Standard I/O");
        socketButton= new JRadioButton("Socket");

        JPanel socketPanel = new JPanel();
        socketPanel.setLayout(flowLayout);

        JPanel stdIoPanel = new JPanel();
        stdIoPanel.setLayout(new GridLayout(11, 1));

        JLabel hostLabel=new JLabel("Host");
        hostField =new JTextField(host, 20);
        hostField.setEnabled(false);
        hostField.getDocument().addDocumentListener(dl);

        JLabel portLabel=new JLabel("Port");
        portField=new JTextField(port, 10);
        portField.setEnabled(false);
        portField.getDocument().addDocumentListener(dl);

        stdioButton.setSelected(Channel.STDIO.equals(channel));
        stdIoPanel.setVisible(Channel.STDIO.equals(channel));
        socketButton.setSelected(Channel.SOCKET.equals(channel));
        socketPanel.setVisible(Channel.SOCKET.equals(channel));

        stdioButton.addChangeListener(a->{
            JRadioButton b=(JRadioButton)a.getSource();
            socketButton.setSelected(!b.isSelected());
            socketPanel.setVisible(!b.isSelected());
            stdIoPanel.setVisible(b.isSelected());
            hostField.setEnabled(!b.isSelected());
            portField.setEnabled(!b.isSelected());

            isModified = true;
        });

        socketButton.addChangeListener(a->{
            JRadioButton b=(JRadioButton)a.getSource();
            stdioButton.setSelected(!b.isSelected());
            stdIoPanel.setVisible(!b.isSelected());
            socketPanel.setVisible(b.isSelected());
            hostField.setEnabled(b.isSelected());
            portField.setEnabled(b.isSelected());
            isModified = true;
        });



        hostField.setEnabled(socketButton.isSelected());
        portField.setEnabled(socketButton.isSelected());





        channelPanel.add(channelLabel);
        channelPanel.add(stdioButton);
        channelPanel.add(socketButton);
        mainPanel.add(channelPanel);


        socketPanel.add(hostLabel);
        socketPanel.add(hostField);

        socketPanel.add(portLabel);
        socketPanel.add(portField);
        mainPanel.add(socketPanel);



        JPanel jvmPanel= new JPanel();
        jvmPanel.setLayout(flowLayout);

        JComponent jvmText = new JLabel("JVM path");
        jvmPanel.add(jvmText);
        jvmField = new JTextField(jvm, 50);
        jvmField.getDocument().addDocumentListener(dl);
        jvmPanel.add(jvmField);
        JButton selectButton = new JButton("Select");
        selectButton.addActionListener(click -> {
            File previous = new File(jvm);
            VirtualFile jvmFile = FileChooser.chooseFile(new FileChooserDescriptor(
                            true,
                            false,
                            false,
                            false,
                            false,
                            false).withTitle("Select JVM"),
                    stdIoPanel,
                    null,
                    null);
            if (jvmFile != null && jvmFile.exists()) {
                jvmField.setText(jvmFile.getPath());
            }
        });
        jvmPanel.add(selectButton);
        stdIoPanel.add(jvmPanel);

        JComponent pathPanel= new JPanel();
        pathPanel.setLayout(flowLayout);
        JComponent pathText=new JLabel("PATH Variable: ");
        pathPanel.add(pathText);
        pathField = new JTextField(path, 50);
        pathField.getDocument().addDocumentListener(dl);
        pathPanel.add(pathField);
        stdIoPanel.add(pathPanel);

        JComponent commandText1=new JLabel("Option 1: run java -jar [jar file path] [program arguments]");
        stdIoPanel.add(commandText1);


        JPanel jarPanel=new JPanel();
        jarPanel.setLayout(flowLayout);
        JComponent jarText = new JLabel("Jar file path");
        jarPanel.add(jarText);
        jarField = new JTextField(jarPath, 50);
        jarField.getDocument().addDocumentListener(dl);
        jarPanel.add(jarField);
        JButton selectJarButton = new JButton("Select");
        selectJarButton.addActionListener(click -> {
            File previous = new File(jarPath);
            VirtualFile jar = FileChooser.chooseFile(new FileChooserDescriptor(
                            true,
                            false,
                            true,
                            true,
                            false,
                            false).withTitle("Select a Jar file"),
                    stdIoPanel,
                    null,
                    null);
            if (jar != null && jar.exists()) {
                jarField.setText(jar.getPath());
            }
        });
        jarPanel.add(selectJarButton);
        stdIoPanel.add(jarPanel);

        JComponent commandText2=new JLabel("Option 2: run java -cp [class path] [main class] [program arguments]");
        stdIoPanel.add(commandText2);

        JPanel cpPanel=new JPanel();
        cpPanel.setLayout(flowLayout);
        JComponent cpText = new JLabel("Class path");
        cpPanel.add(cpText);
        cpField = new JTextField(cpPath, 50);
        cpField.getDocument().addDocumentListener(dl);
        cpPanel.add(cpField);
        stdIoPanel.add(cpPanel);


        JPanel mcPanel=new JPanel();
        mcPanel.setLayout(flowLayout);
        JComponent mainText = new JLabel("Main class");
        mcPanel.add(mainText);
        mainField = new JTextField(mainClass, 50);
        mainField.getDocument().addDocumentListener(dl);
        mcPanel.add(mainField);
        stdIoPanel.add(mcPanel);

        JComponent argsText = new JLabel("Program arguments");
        stdIoPanel.add(argsText);

        argsField = new JTextField(args, 50);
        argsField.getDocument().addDocumentListener(dl);
        stdIoPanel.add(argsField);

        JComponent dirText = new JLabel("Working directory (optional)");
        stdIoPanel.add(dirText);

        dirField = new JTextField(dir, 50);
        dirField.getDocument().addDocumentListener(dl);
        stdIoPanel.add(dirField);
        mainPanel.add(stdIoPanel);
        mainPanel.add(Box.createVerticalGlue());

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        return isModified;
    }

    @Override
    public void apply() {
        PropertiesComponent pc = PropertiesComponent.getInstance();
        pc.setValue(JVM, jvmField.getText());
        pc.setValue(JAR, jarField.getText());
        pc.setValue(CP, cpField.getText());
        pc.setValue(MAIN, mainField.getText());
        pc.setValue(ARGS, argsField.getText());
        pc.setValue(DIR, dirField.getText());
        pc.setValue(PATH, pathField.getText());
        if(socketButton.isSelected()) {
            pc.setValue(CHANNEL, Channel.SOCKET);
            pc.setValue(HOST, hostField.getText());
            pc.setValue(PORT, portField.getText());
        } else
            pc.setValue(CHANNEL, Channel.STDIO);
        isModified = false;
        ServerLauncher.reload();
    }
}
