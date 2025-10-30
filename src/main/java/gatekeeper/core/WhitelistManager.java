package gatekeeper.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import necesse.engine.GlobalData;
import necesse.engine.network.server.Server;

public class WhitelistManager {
    private static final String CONFIG_DIR = GlobalData.appDataPath() + "mods/GateKeeper";
    private static final String CONFIG_FILE = CONFIG_DIR + "/whitelist.txt";

    private final Set<Long> authIds = new HashSet<>();
    private final Set<String> namesLower = new HashSet<>();
    private boolean enabled = true;

    // in-memory rate limit for notifications (auth -> lastMillis)
    private final Map<Long, Long> lastNotify = new HashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; save(); }

    public synchronized void load() {
        authIds.clear();
        namesLower.clear();
        enabled = true;
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) dir.mkdirs();
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            save();
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
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

    public synchronized void save() {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) dir.mkdirs();
        File file = new File(CONFIG_FILE);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
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

    public synchronized boolean isWhitelisted(long auth, String name) {
        if (!enabled) return true; // disabled means allow all
        if (authIds.contains(auth)) return true;
        if (name != null && namesLower.contains(name.toLowerCase(Locale.ENGLISH))) return true;
        return false;
    }

    public synchronized boolean addAuth(long auth) { boolean added = authIds.add(auth); if (added) save(); return added; }
    public synchronized boolean removeAuth(long auth) { boolean rem = authIds.remove(auth); if (rem) save(); return rem; }
    public synchronized boolean addName(String name) {
        if (name == null) return false;
        boolean added = namesLower.add(name.toLowerCase(Locale.ENGLISH));
        if (added) save();
        return added;
    }
    public synchronized boolean removeName(String name) {
        if (name == null) return false;
        boolean rem = namesLower.remove(name.toLowerCase(Locale.ENGLISH));
        if (rem) save();
        return rem;
    }

    public synchronized List<Long> listAuths() { return new ArrayList<>(authIds); }
    public synchronized List<String> listNames() { return new ArrayList<>(namesLower); }

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

