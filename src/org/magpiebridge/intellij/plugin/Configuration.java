package org.magpiebridge.intellij.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBOptionButton;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class Configuration implements Configurable {

    public static final String JAR = "lsp.jar";
    public static final String CP = "java.cp";
    public static final String MAIN = "main.class";
    public static final String ARGS = "java.args";
    public static final String DIR = "java.dir";
    public static final String JVM = "java.jvm";

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

        JTextField jarField = new JTextField(jarPath, 50);
        p.add(jarField);

        JComponent cpText = new JLabel("Java class path");
        p.add(cpText);

        JTextField cpField = new JTextField(cpPath, 50);
        p.add(cpField);

        JComponent mainText = new JLabel("Main class name");
        p.add(mainText);

        JTextField mainField = new JTextField(mainClass, 50);
        p.add(mainField);

        JComponent argsText = new JLabel("Java arguments");
        p.add(argsText);

        JTextField argsField = new JTextField(args, 50);
        p.add(argsField);

        JComponent dirText = new JLabel("Java working directory");
        p.add(dirText);

        JTextField dirField = new JTextField(dir, 50);
        p.add(dirField);

        JComponent jvmText = new JLabel("JVM absolute path");
        p.add(jvmText);

        JTextField jvmField = new JTextField(jvm, 50);
        p.add(jvmField);

        JButton set = new JButton("set configuration");
        set.addActionListener(e -> {
            pc.setValue(JAR, jarField.getText());
            pc.setValue(CP, cpField.getText());
            pc.setValue(MAIN, mainField.getText());
            pc.setValue(ARGS, argsField.getText());
            pc.setValue(DIR, dirField.getText());
            pc.setValue(JVM, jvmField.getText());
        });
        p.add(set);

        return p;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public void apply() throws ConfigurationException {

    }
}
