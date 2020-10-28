# IntelliJLSP

This is a IntelliJ Plugin which supports the language server protocol. It is made for the [MagpieBridge](https://github.com/MagpieBridge/MagpieBridge) framework.
## Supported Features: 
-  publishDiagnostics: 
    - Diagnositcs are listed in a seperate Tool Window at the bottom of the IDE. 
    - The issued code is underlined. 
    - Hovering over the issued code dispalys the warning message and related information. 
    - Related information with code locations can be directly navigated on clicking
-  Code Actions
-  Hover 
-  ShowMessage
-  ShowMessageRequest
-  ShowHTML (not in LSP): this feature display a HTML page from the language server in a Tool Window at the bottom of the IDE. 

## Supported Channel
- Standard I/O
- Sockets 

## How to install it?
- Download [MagpieBridgeLSPSupport-1.1.zip](https://github.com/MagpieBridge/IntelliJLSP/releases/download/v1.1/MagpieBridgeLSPSupport-1.1.zip) from the release page.
- Choose `IntelliJ >> Settings >> Plugins >> SettingsIcon >> Install Plugin from Disk...` and Select `MagpieBridgeLSPSupport.zip`.
- Restart IntelliJ.
## How to configure a language server?
- Choose `IntelliJ >> Settings >> Other Settings >> MagpieBridge LSP Configuration.` 
- Currenlty it only supports to use Java to run a language server. The following screenshot shows the configuration to run the server `inferIDE-0.0.1.jar` with Java 8.
![configuration](https://github.com/MagpieBridge/MagpieBridge/blob/develop/doc/intellij1.PNG)
## Configuration options explained
- `JVM path`: path to the Java virtual machine
- `PATH Variable`: your system environment variable `PATH`, it is important to set if the language server runs another program which ought to be found in `PATH`.
- `Jar file path`: path to your language server jar file
- `Program arguments`: arguements for your language server.
- `Working directory (optional)`: directory where the server should observe. If not set, the default is the root path of the current opening project in the IDE. 

## Build from Source
To build/run the Project from source, import it as a gradle Project into IntelliJ.

To run the plugin from Source, run `Gradle >> Tasks >> intelliJ >> runIde`.

To build the plugin artifact, run `Gradle >> Tasks >> intelliJ >> buildPlugin`.

## Contact 
&#x2709; linghui[at]outlook.de
