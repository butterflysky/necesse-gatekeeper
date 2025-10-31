package gatekeeper.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import necesse.engine.network.server.Server;
import necesse.engine.world.World;

public class WhitelistManager {
    // Per-world computed config path
    private File configDir;
    private File configFile;
    private long currentWorldId = Long.MIN_VALUE;

    private final Set<Long> authIds = new HashSet<>();
    private final Set<String> namesLower = new HashSet<>(); // legacy entries; not used for allow
    private boolean enabled = true;
    private boolean lockdown = false;

    // in-memory rate limit for notifications (auth -> lastMillis)
    private final Map<Long, Long> lastNotify = new HashMap<>();
    private long lastGlobalNotify = 0L;
    private static final long NOTIFY_COOLDOWN_PER_AUTH_MS = 60_000L;
    private static final long NOTIFY_GLOBAL_MIN_INTERVAL_MS = 3_000L;

    // recent denied attempts (most recent last)
    public static class Attempt {
        public final long timeMs;
        public final long auth;
        public final String name;
        public final String address;
        public Attempt(long timeMs, long auth, String name, String address) {
            this.timeMs = timeMs; this.auth = auth; this.name = name; this.address = address;
        }
    }
    private final LinkedList<Attempt> recent = new LinkedList<>();
    private static final int RECENT_MAX = 50;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(Server server, boolean value) { ensureWorld(server); enabled = value; saveInternal(); }
    private void ensureWorld(Server server) {
        if (server == null || server.world == null) return;
        long wid = server.world.getUniqueID();
        if (wid == currentWorldId && configFile != null) return;
        File worldPath = server.world.filePath;
        if (World.isWorldADirectory(worldPath)) {
            configDir = new File(worldPath, "GateKeeper");
        } else {
            String baseName = World.getWorldDisplayName(worldPath.getName());
            configDir = new File(worldPath.getParentFile(), baseName + ".GateKeeper");
        }
        configFile = new File(configDir, "whitelist.txt");
        currentWorldId = wid;
        loadInternal();
    }

    private synchronized void loadInternal() {
        authIds.clear();
        namesLower.clear();
        enabled = true;
        if (configDir == null || configFile == null) return;
        if (!configDir.exists()) configDir.mkdirs();
        if (!configFile.exists()) {
            saveInternal();
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.equalsIgnoreCase("enabled=true")) { enabled = true; continue; }
                if (line.equalsIgnoreCase("enabled=false")) { enabled = false; continue; }
                if (line.equalsIgnoreCase("lockdown=true")) { lockdown = true; continue; }
                if (line.equalsIgnoreCase("lockdown=false")) { lockdown = false; continue; }
                int idx = line.indexOf(":");
                if (idx > 0) {
                    String key = line.substring(0, idx).trim().toLowerCase(Locale.ENGLISH);
                    String val = line.substring(idx + 1).trim();
                    if (key.equals("auth")) {
                        try { authIds.add(Long.parseLong(val)); } catch (Exception ignore) {}
                    } else if (key.equals("name")) { // legacy; retained for readability
                        namesLower.add(val.toLowerCase(Locale.ENGLISH));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void saveInternal() {
        if (configDir == null || configFile == null) return;
        if (!configDir.exists()) configDir.mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(configFile))) {
            bw.write("# GateKeeper whitelist\n");
            bw.write("enabled=" + enabled + "\n");
            bw.write("lockdown=" + lockdown + "\n");
            for (Long a : authIds) {
                bw.write("auth:" + a + "\n");
            }
            // legacy names retained for human readability; not used for allow decision
            for (String n : namesLower) { bw.write("name:" + n + "\n"); }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized boolean isWhitelisted(Server server, long auth, String name) {
        ensureWorld(server);
        if (!enabled) return true; // disabled means allow all
        return authIds.contains(auth);
    }

    public synchronized boolean isLockdown() { return lockdown; }
    public synchronized void setLockdown(Server server, boolean on) { ensureWorld(server); lockdown = on; saveInternal(); }

    public synchronized boolean addAuth(Server server, long auth) { ensureWorld(server); boolean added = authIds.add(auth); if (added) saveInternal(); return added; }
    public synchronized boolean removeAuth(Server server, long auth) { ensureWorld(server); boolean rem = authIds.remove(auth); if (rem) saveInternal(); return rem; }
    public synchronized boolean addName(Server server, String name) {
        ensureWorld(server);
        if (name == null) return false;
        boolean added = namesLower.add(name.toLowerCase(Locale.ENGLISH));
        if (added) saveInternal();
        return added;
    }
    public synchronized boolean removeName(Server server, String name) {
        ensureWorld(server);
        if (name == null) return false;
        boolean rem = namesLower.remove(name.toLowerCase(Locale.ENGLISH));
        if (rem) saveInternal();
        return rem;
    }

    public synchronized List<Long> listAuths(Server server) { ensureWorld(server); return new ArrayList<>(authIds); }
    public synchronized List<String> listNames(Server server) { ensureWorld(server); return new ArrayList<>(namesLower); }

    public synchronized Long findAuthByName(Server server, String name) {
        if (server == null || name == null) return null;
        String nlow = name.toLowerCase(Locale.ENGLISH);
        // online clients
        for (int i = 0; i < server.getSlots(); i++) {
            necesse.engine.network.server.ServerClient c = server.getClient(i);
            if (c != null && c.getName() != null && c.getName().equalsIgnoreCase(name)) {
                return c.authentication;
            }
        }
        // known names from saves
        Map<Long, String> used = server.world.getUsedPlayerNames();
        for (Map.Entry<Long, String> e : used.entrySet()) {
            if (e.getValue() != null && e.getValue().toLowerCase(Locale.ENGLISH).equals(nlow)) {
                return e.getKey();
            }
        }
        return null;
    }

    public synchronized String getNameByAuth(Server server, long auth) {
        if (server == null) return null;
        for (int i = 0; i < server.getSlots(); i++) {
            necesse.engine.network.server.ServerClient c = server.getClient(i);
            if (c != null && c.authentication == auth) {
                return c.getName();
            }
        }
        Map<Long, String> used = server.world.getUsedPlayerNames();
        return used.get(auth);
    }

    public synchronized void rememberNotify(long auth) {
        lastNotify.put(auth, System.currentTimeMillis());
        lastGlobalNotify = System.currentTimeMillis();
    }

    public synchronized boolean shouldNotify(long auth, long cooldownMs) {
        long now = System.currentTimeMillis();
        if (now - lastGlobalNotify < NOTIFY_GLOBAL_MIN_INTERVAL_MS) return false;
        Long last = lastNotify.get(auth);
        if (last == null) return true;
        return now - last > cooldownMs;
    }

    public synchronized void recordDeniedAttempt(Server server, long auth, String name, String address) {
        ensureWorld(server);
        Attempt a = new Attempt(System.currentTimeMillis(), auth, name, address);
        recent.addLast(a);
        while (recent.size() > RECENT_MAX) recent.removeFirst();
        // Append to log file
        if (configDir != null) {
            File log = new File(configDir, "denied_log.txt");
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(log, true))) {
                String n = name == null ? "" : name;
                String addr = address == null ? "" : address;
                bw.write(a.timeMs + "," + auth + "," + n + "," + addr + "\n");
            } catch (IOException ignore) {}
        }
    }

    public synchronized List<Attempt> getRecentAttempts() { return new ArrayList<>(recent); }
    public synchronized Long getLastDeniedAuth() { return recent.isEmpty() ? null : recent.getLast().auth; }

    public synchronized File getConfigDir(Server server) {
        ensureWorld(server);
        return configDir;
    }

    public synchronized int exportKnownPlayers(Server server) {
        ensureWorld(server);
        if (configDir == null) return 0;
        if (!configDir.exists()) configDir.mkdirs();
        File out = new File(configDir, "known_players.txt");
        Map<Long, String> used = server.world.getUsedPlayerNames();
        int count = 0;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(out))) {
            bw.write("# auth,name\n");
            for (Map.Entry<Long, String> e : used.entrySet()) {
                if (e.getValue() != null) {
                    bw.write(e.getKey() + "," + e.getValue() + "\n");
                    count++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return count;
    }

    public synchronized void logAdminAction(Server server, String line) {
        ensureWorld(server);
        if (configDir == null) return;
        if (!configDir.exists()) configDir.mkdirs();
        File out = new File(configDir, "admin_log.txt");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(out, true))) {
            bw.write(System.currentTimeMillis() + "," + line + "\n");
        } catch (IOException ignore) {}
    }
}
