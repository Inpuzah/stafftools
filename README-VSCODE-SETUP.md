# VS Code setup for Java + Paper/Velocity plugin development

Follow these steps to get VS Code ready for Java and Minecraft plugin development.

1) Install recommended extensions

- Use the Workspace Recommended extensions (open the Extensions pane -> "Recommended") or run these commands in PowerShell to install the core Java pack and helpers:

```
code --install-extension vscjava.vscode-java-pack
code --install-extension vscjava.vscode-maven
code --install-extension vscjava.vscode-java-dependency
code --install-extension vscjava.vscode-java-debug
code --install-extension vscjava.vscode-java-test
code --install-extension redhat.vscode-yaml
```

Note: there are community extensions specifically for Minecraft development (search the Marketplace for "Minecraft Development"). Install one you prefer — the workspace recommendations include core Java tooling only because marketplace IDs for Minecraft extensions vary.

2) Terminal

- The workspace defaults the integrated terminal to PowerShell on Windows. To open it, use `Terminal > New Terminal` or press ``Ctrl+` ``.

3) Java runtime

- The workspace `settings.json` includes a `java.configuration.runtimes` entry pointing at `C:\Program Files\Java\jdk-17` as a placeholder. Update that path to your installed JDK 17+ if needed (Minecraft server and modern plugin APIs require Java 17+). You can also leave it if your JDK is on PATH and the Java extensions detect it.

4) Running servers with debug enabled

- Create the directories and place the server jars:

  - `${workspaceFolder}/server/paper/paper.jar` for Paper
  - `${workspaceFolder}/server/velocity/velocity.jar` for Velocity

- Use `Terminal > Run Task...` and select `Run Paper (debug)` or `Run Velocity (debug)`. Each task launches the server with JDWP enabled on ports 5005 (Paper) and 5006 (Velocity) so you can attach the VS Code debugger.

5) Attach debugger

- Open the Run view (Run and Debug), choose `Attach to Paper (5005)` or `Attach to Velocity (5006)`, then click the green Start button. Your breakpoints in Java sources under `src/main/java` will be hit.

6) Notes & tips

- If you use Maven or Gradle, use the Java extensions' dependency viewers to add the Paper API / Velocity API artifacts to your `pom.xml` or `build.gradle`.
- If you use Lombok, install a Lombok-support extension and ensure annotation processing is enabled in your Java settings.
- If the `code` CLI is not available, open the Extensions view and install the recommended extensions using the UI.

If you want, I can also:
- Add a sample `server` folder and a small PowerShell script to download the latest Paper/Velocity jars where possible.
- Add a Maven profile or task to build and automatically copy the plugin jar into the `server` folder.

What's been added now in the workspace:

- `server/paper/download-jar.ps1` — helper to download a Paper jar into `server/paper/paper.jar` (pass `-Url`).
- `server/paper/start-debug.ps1` — starts Paper with JDWP enabled on port 5005 and creates `eula.txt` if missing.
- `server/paper/plugins/` — folder where plugin jars will be copied.
- `server/velocity/download-jar.ps1` — helper to download a Velocity jar into `server/velocity/velocity.jar` (pass `-Url`).
- `server/velocity/start-debug.ps1` — starts Velocity with JDWP enabled on port 5006.
- VS Code task `Maven: package & Deploy to Paper` — runs `mvn -DskipTests package` and copies the produced jar from `target/` into `server/paper/plugins`.

Quick usage examples (PowerShell):

Download a server jar manually (replace URL):

```powershell
.\server\paper\download-jar.ps1 -Url 'https://example.com/path/to/paper.jar'
.\server\velocity\download-jar.ps1 -Url 'https://example.com/path/to/velocity.jar'
```

Start servers (debug):

```powershell
.\server\paper\start-debug.ps1
.\server\velocity\start-debug.ps1
```

Build and deploy your plugin to the Paper server (from VS Code):

- Run the task: `Terminal > Run Task...` -> `Maven: package & Deploy to Paper`.

This task will create `target/*.jar`, then copy the latest jar found to `server/paper/plugins` so the running Paper server can pick it up (or accept on restart).
