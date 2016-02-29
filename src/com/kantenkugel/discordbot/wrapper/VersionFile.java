package com.kantenkugel.discordbot.wrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

public class VersionFile {
    private static final String VERSION_FILE_URL = "https://dl.dropboxusercontent.com/u/33175902/kbVersion2.txt";

    public static VersionFile get() {
        return get(false);
    }

    public static VersionFile get(boolean reFetch) {
        if(instance == null || reFetch) {
            VersionFile fetched = fetch();
            if(fetched != null) {
                instance = fetched;
            }
        }
        return instance;
    }

    private static VersionFile instance;

    public final int version;
    public final String downloadUrl;
    public final List<VersionEntry> versionEntries;
    public final long created = System.currentTimeMillis();

    private VersionFile(String downloadUrl, List<VersionEntry> entries) {
        this.downloadUrl = downloadUrl;
        this.versionEntries = entries;
        this.version = entries.size() == 0 ? 0 : entries.get(0).version;
    }

    private static VersionFile fetch() {
        try {
            URL url = new URL(VERSION_FILE_URL);
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            String dl = reader.readLine(); //first line is download-link
            List<VersionEntry> versions = new LinkedList<>();
            String changelog = null;
            String read;
            int version = 0;
            while((read = reader.readLine()) != null) {
                if(read.charAt(0) == '#') {
                    //new version
                    if(version > 0) {
                        versions.add(new VersionEntry(version, changelog));
                        changelog = null;
                    }
                    version = Integer.parseInt(read.substring(1));
                } else {
                    changelog = changelog == null ? read : changelog + "\n" + read;
                }
            }
            if(version > 0) {
                versions.add(new VersionEntry(version, changelog));
            }
            reader.close();
            return new VersionFile(dl, versions);
        } catch(IOException ignored) {
        }
        return null;
    }

    public static class VersionEntry {
        public final int version;
        public final String changelog;

        private VersionEntry(int version, String changelog) {
            this.version = version;
            this.changelog = changelog;
        }
    }
}
