package mpo.dayon.assisted;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import mpo.dayon.assisted.gui.Assisted;
import mpo.dayon.common.Runner;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.log.Log;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static mpo.dayon.common.utils.SystemUtilities.getJarDir;

import static com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE;

class AssistedRunner implements Runner {
    public static void main(String[] args) {
        try {
            Runner.setDebug(args);
            Map<String, String> programArgs = Runner.extractProgramArgs(args);
            Runner.overrideLocale(programArgs.get("lang"));
            Runner.disableDynamicScale();
            Runner.getOrCreateAppHomeDir();
            Runner.logAppInfo("dayon_assisted");
            fixUacBehaviour();
            SwingUtilities.invokeLater(() -> launchAssisted(programArgs.get("ah"), programArgs.get("ap")));
        } catch (Exception ex) {
            FatalErrorHandler.bye("The assisted is dead!", ex);
        }
    }

    private static void launchAssisted(String assistantHost, String assistantPort) {
        final Assisted assisted = new Assisted();
        assisted.configure();
        // cli args have precedence
        if (assistantHost == null || assistantPort == null) {
            final Map<String, String> config = readPresetFile();
            assisted.start(config.get("host"), config.get("port"), isAutoConnect(config));
        } else {
            assisted.start(assistantHost, assistantPort, true);
        }
    }

    private static Map<String, String> readPresetFile() {
        final List<String> paths = Arrays.asList(System.getProperty("dayon.home"), System.getProperty("user.home"), getJarDir());
        final String fileName = "assisted.yaml";
        for (String path : paths) {
            final File presetFile = new File(path, fileName);
            if (presetFile.exists() && presetFile.isFile() && presetFile.canRead()) {
                final Map<String, String> content = parseFileContent(presetFile);
                if (content != null) {
                    return content;
                }
            }
        }
        return Collections.EMPTY_MAP;
    }

    private static Map<String, String> parseFileContent(File presetFile) {
        try (Stream<String> lines = Files.lines(presetFile.toPath())) {
            final Map<String, String> content = lines.map(line -> line.split(":")).filter(s -> s.length > 1).collect(Collectors.toMap(s -> s[0].trim(), s -> s[1].trim()));
            if (content.containsKey("host") && content.containsKey("port")) {
                Log.info(format("Using connection settings from %s", presetFile.getPath()));
                return content;
            }
        } catch (IOException e) {
            Log.warn(e.getMessage());
        }
        return null;
    }

    private static boolean isAutoConnect(Map<String, String> config) {
        return !config.containsKey("autoConnect") || !config.get("autoConnect").equalsIgnoreCase("false");
    }

    private static void fixUacBehaviour() {
        if (File.separatorChar == '/') {
            return;
        }
        final int off = 0x00000000;
        final int on = 0x00000001;
        final int secureDesktop = Advapi32Util.registryGetIntValue
                (HKEY_LOCAL_MACHINE,
                        "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System",
                        "PromptOnSecureDesktop");
        if (off != secureDesktop) {
            try {
                Advapi32Util.registrySetIntValue
                        (HKEY_LOCAL_MACHINE,
                                "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System", "PromptOnSecureDesktop", off);
                Advapi32Util.registrySetIntValue
                        (HKEY_LOCAL_MACHINE,
                                "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System", "EnableLUA", on);
            } catch(Win32Exception e) {
                Log.warn("Could not fix UAC behaviour, UAC dialogs will not be visible");
                Log.warn("Rerun the assisted with admin rights to fix this");
            }
        }
    }
}
