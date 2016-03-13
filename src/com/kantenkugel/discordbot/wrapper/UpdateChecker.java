package com.kantenkugel.discordbot.wrapper;

public class UpdateChecker extends Thread {
    private static final long CHECK_INTERVAL = 5*60*1000; //5min

    public UpdateChecker() {
        setPriority(Thread.NORM_PRIORITY + 1);
        setDaemon(true);
        start();
    }

    @Override
    public void run() {
        while(true) {
            try {
                Thread.sleep(CHECK_INTERVAL);
            } catch(InterruptedException e) {
                System.out.println("Exiting update-check routine.");
                break;
            }
            VersionFile versionFile = VersionFile.get(true);
            if(versionFile != null && (Main.versions.size() == 0 || versionFile.version > Main.versions.get(0).version)) {
                System.out.println("Detected newer version... initializing update!");
                Main.doUpdate();
            }
        }
    }
}
