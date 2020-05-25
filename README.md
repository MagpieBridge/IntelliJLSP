# IntelliJLSP

This is a IntelliJ Plugin which supports the language server protocol. It is made for the [MagpieBridge](https://github.com/MagpieBridge/MagpieBridge) framework.

## How to install it?
- Download [MagpieBridgeLSPSupport.zip](https://github.com/MagpieBridge/IntelliJLSP/releases/download/v1.0/MagpieBridgeLSPSupport.zip) from the release page.
- Choose `IntelliJ >> Settings >> Plugins >> SettingsIcon >> Install Plugin from Disk...` and Select `MagpieBridgeLSPSupport.zip`.
- Restart IntelliJ.
## How to configure a language server?
- Choose `IntelliJ >> Settings >> Other Settings >> MagpieBridge LSP Configuration. 
- Currenlty it only supports to use Java to run a language server. The following screenshot shows the configuration to run the server `inferIDE-0.0.1.jar` with Java 8.
![](https://github.com/MagpieBridge/MagpieBridge/blob/develop/doc/intellij.PNG)
