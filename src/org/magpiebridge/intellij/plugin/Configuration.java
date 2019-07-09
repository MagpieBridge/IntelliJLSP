package org.magpiebridge.intellij.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;

public class Configuration implements Configurable {

    public static final String JAR = "lsp.jar";
    public static final String CP = "java.cp";
    public static final String MAIN = "main.class";
    public static final String ARGS = "java.args";
    public static final String DIR = "java.dir";
    public static final String JVM = "java.jvm";
    private JTextField jarField;
    private JTextField cpField;
    private JTextField mainField;
    private JTextField argsField;
    private JTextField dirField;
    private JTextField jvmField;

    private boolean isModified = false;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "LSP Configuration";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        PropertiesComponent pc = PropertiesComponent.getInstance();
        String cpPath = pc.getValue(CP, "");
        String jarPath = pc.getValue(JAR, "");
        String mainClass = pc.getValue(MAIN, "");
        String args = pc.getValue(ARGS, "");
        String dir = pc.getValue(DIR, "");

        String javaHome = System.getProperty("java.home");
        File javaPath = new File(javaHome, "bin/java");
        String jvm = pc.getValue(JVM, javaPath.getAbsolutePath());

        JPanel p = new JPanel();
        p.setLayout(new GridLayout(14, 1));

        JComponent jarText = new JLabel("JAR file path");
        p.add(jarText);

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

        jarField = new JTextField(jarPath, 50);
        jarField.getDocument().addDocumentListener(dl);
        p.add(jarField);

        JButton selectJar = new JButton("Search");
        selectJar.addActionListener(click -> {
            File previous = new File(jarPath);
            VirtualFile jar = FileChooser.chooseFile(new FileChooserDescriptor(
                            true,
                            false,
                            true,
                            true,
                            false,
                            false).withTitle("Select LSP Jar"),
                    p,
                    null,
                    null);
            if (jar != null && jar.exists()) {
                jarField.setText(jar.getPath());
            }
        });
        p.add(selectJar);

        JComponent cpText = new JLabel("Java class path");
        p.add(cpText);

        cpField = new JTextField(cpPath, 50);
        cpField.getDocument().addDocumentListener(dl);
        p.add(cpField);

        JComponent mainText = new JLabel("Main class name");
        p.add(mainText);

        mainField = new JTextField(mainClass, 50);
        mainField.getDocument().addDocumentListener(dl);
        p.add(mainField);

        JComponent argsText = new JLabel("Java arguments");
        p.add(argsText);

        argsField = new JTextField(args, 50);
        argsField.getDocument().addDocumentListener(dl);
        p.add(argsField);

        JComponent dirText = new JLabel("Java working directory");
        p.add(dirText);

        dirField = new JTextField(dir, 50);
        dirField.getDocument().addDocumentListener(dl);
        p.add(dirField);

        JComponent jvmText = new JLabel("JVM absolute path");
        p.add(jvmText);

        jvmField = new JTextField(jvm, 50);
        jvmField.getDocument().addDocumentListener(dl);
        p.add(jvmField);

        return p;
    }

    @Override
    public boolean isModified() {
        return isModified;
    }

    @Override
    public void apply() {
        PropertiesComponent pc = PropertiesComponent.getInstance();
        pc.setValue(JAR, jarField.getText());
        pc.setValue(CP, cpField.getText());
        pc.setValue(MAIN, mainField.getText());
        pc.setValue(ARGS, argsField.getText());
        pc.setValue(DIR, dirField.getText());
        pc.setValue(JVM, jvmField.getText());
        isModified = false;
        Launcher.reload();
    }
}
