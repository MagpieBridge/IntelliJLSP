package org.magpiebridge.intellij.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class Configuration implements Configurable {

    public static final String JAR = "lsp.jar";
    public static final String CP = "java.cp";
    public static final String MAIN = "main.class";
    public static final String ARGS = "java.args";
    public static final String DIR = "java.dir";

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
        JPanel p = new JPanel();
        p.setLayout(new GridLayout(12, 1));

        JComponent jarText = new JLabel("JAR file path");
        p.add(jarText);

        JTextField jarField = new JTextField(jarPath, 50);
        jarField.addActionListener(e -> pc.setValue(JAR, jarField.getText()));
        p.add(jarField);

        p.add(new JLabel("-- OR --"));

        JComponent cpText = new JLabel("Java class path");
        p.add(cpText);

        JTextField cpField = new JTextField(cpPath, 50);
        cpField.addActionListener(e -> pc.setValue(CP, cpField.getText()));
        p.add(cpField);

        JComponent mainText = new JLabel("Main class name");
        p.add(mainText);

        JTextField mainField = new JTextField(mainClass, 50);
        mainField.addActionListener(e -> pc.setValue(MAIN, mainField.getText()));
        p.add(mainField);

        p.add(new JLabel("--------------"));

        JComponent argsText = new JLabel("Java arguments");
        p.add(argsText);

        JTextField argsField = new JTextField(args, 50);
        argsField.addActionListener(e -> pc.setValue(ARGS, argsField.getText()));
        p.add(argsField);

        JComponent dirText = new JLabel("Java working directory");
        p.add(dirText);

        JTextField dirField = new JTextField(dir, 50);
        dirField.addActionListener(e -> pc.setValue(DIR, dirField.getText()));
        p.add(dirField);

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
