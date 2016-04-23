package com.kantenkugel.discordbot.wrapper;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Main {
    private static final int UPDATE_EXIT_CODE = 20;
    private static final int NORMAL_EXIT_CODE = 21;
    private static final int RESTART_CODE = 22;
    private static final int REVERT_CODE = 23;

    private static final Path botFile = Paths.get("DiscordBotJDA.jar");
    private static final Path backupFile = Paths.get("DiscordBotJDA_backup.jar");
    private static final Path current = Paths.get("");

    private static final int CONFIG_VERSION = 2;

    /*
    Command-string for bot:
    String[0] -> error loading login-informations from config-file
    Otherwise: String[8+]:
        "java",
        "-jar",
        "botFile.jar",
        0  email,
        1  password,
        2  system-time of wrapper-start (for uptime),
        3  success-indicator (true/false/"-")
        4  version-number
        5+ MULTIPLE/NONE strings describing the changelog of this version
     */
    private static final String[] START_BOT_COMMAND;
    private static Thread updateChecker = null;

    public static List<VersionFile.VersionEntry> versions = new ArrayList<>(0);
    private static BufferedWriter botWriter;

    public static void main(String[] args) {
        if(START_BOT_COMMAND.length == 0) {
            return;
        }
        System.out.println("Downloading current version of the Bot...");
        update();
        System.out.println("Starting update-checker...");
        updateChecker = new UpdateChecker();


        System.out.println("Starting the Bootstrap Launch loop");
        Status updateStatus = Status.NONE;
        try {
            while(true) {
                ProcessBuilder builder = new ProcessBuilder();
                builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                builder.command(START_BOT_COMMAND);
                builder.command().add(updateStatus == Status.NONE ? "-" : Boolean.toString(updateStatus.isSuccess()));
                VersionFile.VersionEntry entry = (versions.isEmpty() || (updateStatus == Status.FAILED && versions.size() == 1)) ?
                        null : versions.get(updateStatus == Status.FAILED ? 1 : 0);
                builder.command().add(entry == null ? "0" : Integer.toString(entry.version));
                if(entry != null) {
                    String changelog = entry.changelog;
                    if(changelog != null) {
                        for(String s : changelog.split("\n")) {
                            builder.command().add(s);
                        }
                    }
                }
                if(updateStatus != Status.NONE) {
                    updateStatus = Status.NONE;
                }

                Process botProcess = builder.start();
                botWriter = new BufferedWriter(new OutputStreamWriter(botProcess.getOutputStream()));
                botProcess.waitFor();
                switch(botProcess.exitValue()) {
                    case NORMAL_EXIT_CODE:
                        System.out.println("The Bot requested to shutdown and not relaunch.\nShutting down...");
                        System.exit(0);
                        break;
                    case UPDATE_EXIT_CODE:
                        System.out.println("Uptating bot...");
                        update();
                        updateStatus = Status.SUCCESS;
                        break;
                    case RESTART_CODE:
                        System.out.println("Restarting");
                        break;
                    case REVERT_CODE:
                        System.out.println("Bot exited with Revert-code... reverting");
                        revert();
                        updateStatus = Status.FAILED;
                        break;
                    default:
                        System.out.println("The Bot's Exit code was unrecognized. ExitCode: " + botProcess.exitValue());
                        System.out.println("Stopping");
                        System.exit(0);
                }
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void doUpdate() {
        sendCommand(UPDATE_EXIT_CODE);
    }

    private enum Status {
        SUCCESS(true), FAILED(false), NONE(false);
        private final boolean success;

        Status(boolean success) {
            this.success = success;
        }

        public boolean isSuccess() {
            return success;
        }
    }

    private static void sendCommand(int code) {
        if(botWriter != null) {
            try {
                botWriter.write(code + "\n");
                botWriter.flush();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void update() {
        try {
            if(Files.exists(current.resolve(botFile))) {
                Files.move(current.resolve(botFile), current.resolve(backupFile), StandardCopyOption.REPLACE_EXISTING);
            }
            VersionFile versionFile = VersionFile.get(true);
            try {
                URL url = new URL(versionFile.downloadUrl);
                Files.copy(url.openStream(), current.resolve(botFile), StandardCopyOption.REPLACE_EXISTING);
                versions = versionFile.versionEntries;
            } catch(IOException ignored) {

            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private static void revert() {
        try {
            Files.copy(current.resolve(backupFile), current.resolve(botFile), StandardCopyOption.REPLACE_EXISTING);
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    static {
        Path cfgFile = Paths.get("loginConfig.json");
        JSONObject config = null;
        if(Files.exists(cfgFile)) {
            try {
                Optional<String> join = Files.readAllLines(cfgFile, StandardCharsets.UTF_8).stream().map(String::trim).reduce((s1, s2) -> s1 + s2);
                if(join.isPresent()) {
                    config = new JSONObject(join.get());
                }
            } catch(Exception ex) {
                ex.printStackTrace();
            }
        }
        if(config == null) {
            config = new JSONObject().put("email", "").put("password", "")
                    .put("isBot", true).put("botToken", "")
                    .put("version", CONFIG_VERSION);
            try {
                Files.write(cfgFile, Arrays.asList(config.toString(4).split("\n")), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            } catch(IOException e) {
                e.printStackTrace();
            }
            System.out.println("Created login-config file... plese populate it!");
            START_BOT_COMMAND = new String[0];
        } else if(!config.has("version") || config.getInt("version") != CONFIG_VERSION) {
            if(!config.has("version")) config.put("version", 1);
            switch(config.getInt("version")) {
                case 1:
                    config.put("isBot", false).put("botToken", "");
                    break;
            }
            config.put("version", CONFIG_VERSION);
            try {
                Files.write(cfgFile, Arrays.asList(config.toString(4).split("\n")), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            } catch(IOException e) {
                e.printStackTrace();
            }
            System.out.println("Updated login-config. Pleas check it before restarting!");
            START_BOT_COMMAND = new String[0];
        } else {
            if(config.getBoolean("isBot")) {
                String botToken = config.getString("botToken");
                if(botToken.isEmpty()) {
                    System.out.println("Please populate the config-file with your login-informations!");
                    START_BOT_COMMAND = new String[0];
                } else {
                    START_BOT_COMMAND = new String[]{
                            "java", "-jar", botFile.toString(), botToken, "-", Long.toString(System.currentTimeMillis())
                    };
                }
            } else {
                String email = config.getString("email").trim();
                String pass = config.getString("password");
                if(email.isEmpty() || pass.isEmpty()) {
                    System.out.println("Please populate the config-file with your login-informations!");
                    START_BOT_COMMAND = new String[0];
                } else {
                    START_BOT_COMMAND = new String[]{
                            "java", "-jar", botFile.toString(), email, pass, Long.toString(System.currentTimeMillis())
                    };
                }
            }
        }
    }
}
