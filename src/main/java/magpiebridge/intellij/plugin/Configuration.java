package magpiebridge.intellij.plugin;

import com.intellij.ide.HelpTooltip;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for LSP Server.
 */
public class Configuration implements Configurable {

  List<String> configKeys = new ArrayList<>();
  List<String> configValues = new ArrayList<>();
  List<Integer> modifiedIndices = new ArrayList<>();

  protected static final String FILEPATTERN = "lsp.filepattern";
  protected static final String JAR = "lsp.jar";
  protected static final String CP = "java.cp";
  protected static final String MAIN = "main.class";
  protected static final String ARGS = "java.args";
  protected static final String DIR = "java.dir";
  protected static final String JVM = "java.jvm";
  protected static final String CHANNEL = "lsp.channel";
  protected static final String HOST = "lsp.host";
  protected static final String PORT = "lsp.port";
    protected static final String PATH = "PATH";
    protected static final String COMMANDOPTION = "lsp.commandoption";

  private JTextField filepatternField;
  private JTextField jarField;
    private JTextField cpField;
    private JTextField mcField;
    private JTextField argsField;

    private JTextField dirField;
    private JTextField jvmField;
    private JTextField pathField;
    private JTextField hostField;
    private JTextField portField;
    private JRadioButton socketButton;
    private JRadioButton commandOneRadio;

  private boolean isModified = false;
  private String selectedConfigLineKey = null;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "MagpieBridge LSP Configuration";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
      PropertiesComponent pc = PropertiesComponent.getInstance();
      String filepattern = pc.getValue(FILEPATTERN, ".*");
      String javaHome = System.getProperty("java.home");
      File javaPath = new File(javaHome, "bin/java");
      String jvm = pc.getValue(JVM, javaPath.getAbsolutePath());
      String path = pc.getValue(PATH, "");
      String jarPath = pc.getValue(JAR, "");
      String cpPath = pc.getValue(CP, "");
      String mainClass = pc.getValue(MAIN, "");
      String args = pc.getValue(ARGS, "");
      String dir = pc.getValue(DIR, "");
      String host = pc.getValue(HOST, "127.0.0.1");
      String port = pc.getValue(PORT, "2403");
      boolean channel = pc.getBoolean(CHANNEL, true);

      final @Nullable String[] customConfigKeys = pc.getValues("CUSTOM_CONFIG_KEYS");
      if (customConfigKeys != null) {
        Collections.addAll(configKeys, customConfigKeys);
      }

      final @Nullable String[] customConfigValues = pc.getValues("CUSTOM_CONFIG_VALUES");
      if (customConfigValues != null) {
        Collections.addAll(configValues, customConfigValues);
      }


      JPanel mainPanel = new JPanel();
      mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

      FlowLayout flowLayout = new FlowLayout();
      flowLayout.setAlignment(FlowLayout.LEFT);

      DocumentListener dl =
              new DocumentListener() {
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

      JPanel filepatternPanel = new JPanel(flowLayout);
      filepatternPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
      filepatternPanel.add(new JLabel("File pattern (extension or regex):"));
      filepatternField = new JTextField(filepattern, 30);
      filepatternPanel.add(filepatternField);
      mainPanel.add(filepatternPanel);
      new HelpTooltip().setDescription("To match every file use: .*").installOn(filepatternField);

      JPanel channelPanel = new JPanel(flowLayout);
      JComponent channelLabel = new JLabel("LSP communication channel");
      JRadioButton stdioButton = new JRadioButton("Standard I/O");
      socketButton = new JRadioButton("Socket");

      JPanel socketPanel = new JPanel(flowLayout);

      JPanel stdIoPanel = new JPanel(new GridLayout(11, 1));

      JLabel hostLabel = new JLabel("Host");
      hostField = new JTextField(host, 20);
      hostField.setEnabled(false);
      hostField.getDocument().addDocumentListener(dl);

      JLabel portLabel = new JLabel("Port");
      portField = new JTextField(port, 10);
      portField.setEnabled(false);
      portField.getDocument().addDocumentListener(dl);

      stdioButton.setSelected(channel);
      stdIoPanel.setVisible(channel);
      socketButton.setSelected(!channel);
      socketPanel.setVisible(!channel);

      commandOneRadio =
              new JRadioButton("Option 1: run java -jar [jar file path] [program arguments]");
      JRadioButton commandTwoRadio =
              new JRadioButton("Option 2: run java -cp [class path] [main class] [program arguments]");

      final boolean isCommandOne = pc.getBoolean(COMMANDOPTION, true);
      commandOneRadio.setSelected(isCommandOne);
      commandTwoRadio.setSelected(!isCommandOne);

      stdioButton.addChangeListener(
              a -> {
                JRadioButton b = (JRadioButton) a.getSource();
                socketButton.setSelected(!b.isSelected());
                socketPanel.setVisible(!b.isSelected());
                stdIoPanel.setVisible(b.isSelected());
                hostField.setEnabled(!b.isSelected());
                portField.setEnabled(!b.isSelected());
                isModified = true;
              });

      socketButton.addChangeListener(
              a -> {
                JRadioButton b = (JRadioButton) a.getSource();
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

      JPanel jvmPanel = new JPanel(flowLayout);

      JComponent jvmText = new JLabel("JVM path");
      jvmPanel.add(jvmText);
      jvmField = new JTextField(jvm, 50);
      jvmField.getDocument().addDocumentListener(dl);
      jvmPanel.add(jvmField);
      JButton selectButton = new JButton("Select");
      selectButton.addActionListener(
              click -> {
                File previous = new File(jvm);
                VirtualFile jvmFile =
                        FileChooser.chooseFile(
                                new FileChooserDescriptor(true, false, false, false, false, false)
                                        .withTitle("Select JVM"),
                                stdIoPanel,
                                null,
                                null);
                if (jvmFile != null && jvmFile.exists()) {
                  jvmField.setText(jvmFile.getPath());
                }
              });
      jvmPanel.add(selectButton);
      stdIoPanel.add(jvmPanel);

      stdIoPanel.add(commandOneRadio);

      JPanel jarPanel = new JPanel(flowLayout);
      jarPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
      JComponent jarText = new JLabel("Jar file path");
      jarPanel.add(jarText);
      jarField = new JTextField(jarPath, 50);
      jarField.getDocument().addDocumentListener(dl);
      jarPanel.add(jarField);
      JButton selectJarButton = new JButton("Select");
      selectJarButton.addActionListener(
              click -> {
                File previous = new File(jarPath);
                VirtualFile jar =
                        FileChooser.chooseFile(
                                new FileChooserDescriptor(true, false, true, true, false, false)
                                        .withTitle("Select the Jar File of the Lsp Server"),
                                stdIoPanel,
                                null,
                                null);
                if (jar != null && jar.exists()) {
                  jarField.setText(jar.getPath());
                }
              });
      jarPanel.add(selectJarButton);
      stdIoPanel.add(jarPanel);
      stdIoPanel.add(commandTwoRadio);

      JPanel cpPanel = new JPanel(flowLayout);
      cpPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
      JComponent cpText = new JLabel("Class path");
      cpPanel.add(cpText);
      cpField = new JTextField(cpPath, 50);
      cpField.getDocument().addDocumentListener(dl);
      cpPanel.add(cpField);
      stdIoPanel.add(cpPanel);

      JPanel mcPanel = new JPanel(flowLayout);
      mcPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
      JComponent mainText = new JLabel("Main class");
      mcPanel.add(mainText);
      mcField = new JTextField(mainClass, 50);
      mcField.getDocument().addDocumentListener(dl);
      mcPanel.add(mcField);
      stdIoPanel.add(mcPanel);


      JPanel dividerPanel = new JPanel(flowLayout);
      dividerPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
      dividerPanel.add(new JLabel("Optional"));
      stdIoPanel.add(dividerPanel);


      JPanel argsTextPanel = new JPanel(flowLayout);
      argsTextPanel.add(new JLabel("Program arguments"));
      argsField = new JTextField(args, 50);
      argsField.getDocument().addDocumentListener(dl);
      argsTextPanel.add(argsField);
      stdIoPanel.add(argsTextPanel);


      JComponent dirTextPanel = new JPanel(flowLayout);
      dirTextPanel.add(new JLabel("Working directory"));
      dirField = new JTextField(dir, 50);
      dirField.getDocument().addDocumentListener(dl);
      dirTextPanel.add(dirField);
      stdIoPanel.add(dirTextPanel);

      JComponent pathPanel = new JPanel(flowLayout);
      JComponent pathText = new JLabel("Additions to PATH Variable");
      pathPanel.add(pathText);
      pathField = new JTextField(path, 50);
      pathField.getDocument().addDocumentListener(dl);
      pathPanel.add(pathField);
      stdIoPanel.add(pathPanel);

      mainPanel.add(stdIoPanel);
      mainPanel.add(Box.createVerticalGlue());

      final ChangeListener commandOptionChangeListener =
              click -> {
                boolean isRadioOne = click.getSource() == commandOneRadio;
                boolean selected = ((JRadioButton) click.getSource()).isSelected();
                if (isRadioOne) {
                  commandTwoRadio.setSelected(!selected);
                } else {
                  commandOneRadio.setSelected(!selected);
                  selected = !selected;
                }
                selectJarButton.setEnabled(selected);
                jarField.setEnabled(selected);
                cpField.setEnabled(!selected);
                mcField.setEnabled(!selected);
              };
      commandOneRadio.addChangeListener(commandOptionChangeListener);
      commandTwoRadio.addChangeListener(commandOptionChangeListener);


      JPanel configPanel = new JPanel();
      configPanel.setLayout(new BoxLayout(configPanel, BoxLayout.X_AXIS));
      configPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
      configPanel.setBackground(JBColor.WHITE);

      JPanel configKeyPanel = new JPanel();
      configKeyPanel.setLayout(new BoxLayout(configKeyPanel, BoxLayout.Y_AXIS));
      configPanel.add(configKeyPanel);

      JPanel configValuePanel = new JPanel();
      configValuePanel.setLayout(new BoxLayout(configValuePanel, BoxLayout.Y_AXIS));
      configValuePanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
      configPanel.add(configValuePanel);

      renderConfigTable(configKeyPanel, configValuePanel);

      configKeys.add("test");
      configValues.add("testval");
      renderConfigTable(configKeyPanel, configValuePanel);


      JPanel configButtonPanel = new JPanel();
      configButtonPanel.setLayout(new BoxLayout(configButtonPanel, BoxLayout.Y_AXIS));
      configButtonPanel.setBackground(JBColor.LIGHT_GRAY);

      final JButton addConfigButton = new JButton("+");
      addConfigButton.addActionListener(actionEvent -> {
        if (configKeys.contains("new.key")) {
          return;
        }

        configKeys.add("new.key");
        configValues.add("Value");
        modifiedIndices.add(configKeys.size());

        renderConfigTable(configKeyPanel, configValuePanel);
      });

      configButtonPanel.add(addConfigButton);
      final JButton delConfigButton = new JButton("-");
      delConfigButton.addActionListener((event) -> {
        if (selectedConfigLineKey != null) {
          final int idx = configKeys.indexOf(selectedConfigLineKey);
          if (idx > -1) {
            modifiedIndices.add(idx);
            configKeys.remove(idx);
            configValues.remove(idx);
          }
        }
      });
      configButtonPanel.add(delConfigButton);
      configPanel.add(configButtonPanel);

//        mainPanel.add(configPanel);

      return mainPanel;
    }

  public void renderConfigTable(JPanel configKeyPanel, JPanel configValuePanel) {
    configKeyPanel.removeAll();
    configValuePanel.removeAll();

    if (configKeys.isEmpty()) {
      configKeyPanel.add(new JLabel("Empty"));
      configKeyPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    } else {
      for (String configKey : configKeys) {
        final JButton component = new JButton(configKey);

        component.addActionListener((event) -> {
          selectedConfigLineKey = ((JButton) event.getSource()).getText();
          renderConfigTable(configKeyPanel, configValuePanel);
        });

        if (selectedConfigLineKey != null && selectedConfigLineKey.equals(configKey)) {
          component.setBackground(JBColor.BLUE);
        }

        configKeyPanel.add(component);
      }
      for (String configValue : configValues) {
        configValuePanel.add(new JLabel(configValue));
      }
    }
  }

  @Override
  public boolean isModified() {
    return isModified;
  }

  @Override
  public void apply() {
    PropertiesComponent pc = PropertiesComponent.getInstance();
    pc.setValue(FILEPATTERN, filepatternField.getText());
    pc.setValue(JVM, jvmField.getText());
    pc.setValue(COMMANDOPTION, commandOneRadio.isSelected());
    pc.setValue(JAR, jarField.getText());
    pc.setValue(CP, cpField.getText());
    pc.setValue(MAIN, mcField.getText());
    pc.setValue(ARGS, argsField.getText());
    pc.setValue(DIR, dirField.getText());
    pc.setValue(PATH, pathField.getText());
    pc.setValue(HOST, hostField.getText());
    pc.setValue(PORT, portField.getText());
    pc.setValue(CHANNEL, !socketButton.isSelected(), true);

    /* handle custom config: see https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_didChangeConfiguration
    final Project project = ServiceManager.getService(ProjectService.class).getProject();
    Map<String, String> settings = new HashMap<>();
    final @Nullable String[] custom_config_keys = pc.getValues("CUSTOM_CONFIG_KEYS");

    // TODO: diff what changed; rethink: marking modified items by index will have problems with remove();add(); ;)
    for (int i = 0; i < modifiedIndices.size(); i++) {
        settings.put( configKeys.get(i), configValues.get(i) );
    }
    ServiceManager.getService(IntellijLanguageClient.class).didChangeConfiguration( new DidChangeConfigurationParams( settings ), project);

    pc.setValues("CUSTOM_CONFIG_KEYS", configKeys.toArray(new String[0]));
    pc.setValues("CUSTOM_CONFIG_VALUES", configValues.toArray(new String[0]));
    */

    isModified = false;
    ServiceManager.getService(ProjectService.class).restartServerConnection();

  }
}


