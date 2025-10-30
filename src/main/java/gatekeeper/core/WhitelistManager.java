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
    private final Set<String> namesLower = new HashSet<>();
    private boolean enabled = true;

    // in-memory rate limit for notifications (auth -> lastMillis)
    private final Map<Long, Long> lastNotify = new HashMap<>();

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
                int idx = line.indexOf(":");
                if (idx > 0) {
                    String key = line.substring(0, idx).trim().toLowerCase(Locale.ENGLISH);
                    String val = line.substring(idx + 1).trim();
                    if (key.equals("auth")) {
                        try { authIds.add(Long.parseLong(val)); } catch (Exception ignore) {}
                    } else if (key.equals("name")) {
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
            for (Long a : authIds) {
                bw.write("auth:" + a + "\n");
            }
            for (String n : namesLower) {
                bw.write("name:" + n + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized boolean isWhitelisted(Server server, long auth, String name) {
        ensureWorld(server);
        if (!enabled) return true; // disabled means allow all
        if (authIds.contains(auth)) return true;
        if (name != null && namesLower.contains(name.toLowerCase(Locale.ENGLISH))) return true;
        return false;
    }

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

    public synchronized void rememberNotify(long auth) {
        lastNotify.put(auth, System.currentTimeMillis());
    }

    public synchronized boolean shouldNotify(long auth, long cooldownMs) {
        Long last = lastNotify.get(auth);
        if (last == null) return true;
        return System.currentTimeMillis() - last > cooldownMs;
    }
}
