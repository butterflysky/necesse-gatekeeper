package gatekeeper.core;

/**
 * Manages GateKeeper whitelist state and persistence per world.
 * <p>
 * - Persists whitelist next to the world save.
 * - Enforces auth-only access (SteamID64).
 * - Tracks denied attempts and writes audit logs.
 * - Provides lookups between auth and last-known name.
 * <p>
 * Thread-safety: public methods synchronize on this instance.
 */

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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

public class WhitelistManager {
    // Per-world computed config path
    private File configDir;
    private File configFile;
    private File nameCacheFile;
    private long currentWorldId = Long.MIN_VALUE;

    private final Set<Long> authIds = new HashSet<>();
    private boolean enabled = false;
    private boolean lockdown = false;

    // in-memory rate limit for notifications (auth -> lastMillis)
    private final Map<Long, Long> lastNotify = new HashMap<>();
    private long lastGlobalNotify = 0L;
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

    // Cached bidirectional name/auth mapping (for ergonomics only)
    private final Map<Long, String> authToName = new HashMap<>();
    private final Map<String, Long> nameToAuth = new HashMap<>(); // lower-cased name -> auth

    /**
     * @return true if whitelist is enabled; when disabled all connects are allowed.
     */
    public boolean isEnabled() { return enabled; }
    /**
     * Enable/disable the whitelist for the current world.
     * @param server Server providing the active world
     * @param value new enabled state
     */
    public void setEnabled(Server server, boolean value) { ensureWorld(server); enabled = value; saveInternal(); }
    /** Ensure config paths for the given server world are initialized and loaded. */
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
        configFile = new File(configDir, "whitelist.json");
        nameCacheFile = new File(configDir, "name_cache.json");
        currentWorldId = wid;
        loadInternal();
    }

    /** Load whitelist.json from disk into memory (create if missing). */
    private synchronized void loadInternal() {
        if (configDir == null || configFile == null) return;
        if (!configDir.exists()) configDir.mkdirs();
        if (!configFile.exists()) {
            // No file present yet; keep defaults
            // Still try to load name cache if present
            loadNameCache();
            return;
        }
        try {
            WhitelistConfig cfg = readConfig(configFile);
            applyConfig(cfg);
            loadNameCache();
        } catch (IOException | JsonSyntaxException e) {
            // Malformed or unreadable: keep defaults and rename broken file
            String renamed = renameBrokenConfig();
            System.err.println("GateKeeper: Failed to parse whitelist.json; kept defaults. Renamed broken file to: " + renamed);
        }
    }

    /** Persist current whitelist state to whitelist.json. */
    private synchronized void saveInternal() {
        if (configDir == null || configFile == null) return;
        if (!configDir.exists()) configDir.mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(configFile))) {
            List<Long> ids = new ArrayList<>(authIds);
            java.util.Collections.sort(ids);
            WhitelistConfig cfg = new WhitelistConfig();
            cfg.enabled = this.enabled;
            cfg.lockdown = this.lockdown;
            cfg.auth = ids;
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            bw.write(gson.toJson(cfg));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns whether the provided SteamID (auth) is allowed in the current world.
     * Name is ignored for access decisions and used only for logging.
     */
    public synchronized boolean isWhitelisted(Server server, long auth, String name) {
        ensureWorld(server);
        if (!enabled) return true; // disabled means allow all
        return authIds.contains(auth);
    }

    public synchronized boolean isLockdown() { return lockdown; }
    public synchronized void setLockdown(Server server, boolean on) { ensureWorld(server); lockdown = on; saveInternal(); }

    /** Add a SteamID to the whitelist. @return true if newly added. */
    public synchronized boolean addAuth(Server server, long auth) { ensureWorld(server); boolean added = authIds.add(auth); if (added) saveInternal(); return added; }
    /** Remove a SteamID from the whitelist. @return true if it was present. */
    public synchronized boolean removeAuth(Server server, long auth) { ensureWorld(server); boolean rem = authIds.remove(auth); if (rem) saveInternal(); return rem; }
    /** @return snapshot of all whitelisted SteamIDs for the current world. */
    public synchronized List<Long> listAuths(Server server) { ensureWorld(server); return new ArrayList<>(authIds); }

    /**
     * Resolve a player name to SteamID using online clients and saved players for this world.
     * @return SteamID or null if not found
     */
    public synchronized Long findAuthByName(Server server, String name) {
        if (server == null || name == null) return null;
        // Ensure world paths and name cache are initialized/loaded
        ensureWorld(server);
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
        // fallback to cached mapping
        Long cached = nameToAuth.get(nlow);
        return cached;
    }

    /** Resolve a SteamID to last-known player name (online preferred, else saved). */
    public synchronized String getNameByAuth(Server server, long auth) {
        if (server == null) return null;
        // Ensure world paths and name cache are initialized/loaded
        ensureWorld(server);
        for (int i = 0; i < server.getSlots(); i++) {
            necesse.engine.network.server.ServerClient c = server.getClient(i);
            if (c != null && c.authentication == auth) {
                return c.getName();
            }
        }
        // cached last-known name
        String cached = authToName.get(auth);
        if (cached != null) return cached;
        Map<Long, String> used = server.world.getUsedPlayerNames();
        return used.get(auth);
    }

    /**
     * Reloads configuration from disk in a non-destructive manner.
     * If parsing fails, in-memory state remains unchanged and the broken file is renamed.
     * @return true if reloaded successfully; false if parse error (state unchanged)
     */
    public synchronized boolean reload(Server server, StringBuilder messageOut) {
        ensureWorld(server);
        if (configFile == null) {
            if (messageOut != null) messageOut.append("No config file to reload.");
            return false;
        }
        try {
            WhitelistConfig cfg = readConfig(configFile);
            applyConfig(cfg);
            if (messageOut != null) messageOut.append("Reloaded whitelist from ").append(configFile.getName());
            return true;
        } catch (IOException | JsonSyntaxException e) {
            String renamed = renameBrokenConfig();
            if (messageOut != null) messageOut.append("Error parsing whitelist; kept existing config. Renamed broken file to ").append(renamed);
            return false;
        }
    }

    // --- Helpers ----------------------------------------------------------
    private WhitelistConfig readConfig(File file) throws IOException, JsonSyntaxException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            Gson gson = new Gson();
            WhitelistConfig cfg = gson.fromJson(br, WhitelistConfig.class);
            if (cfg == null) cfg = new WhitelistConfig();
            return cfg;
        }
    }

    private void applyConfig(WhitelistConfig cfg) {
        // Reset then apply
        this.enabled = cfg.enabled;
        this.lockdown = cfg.lockdown;
        this.authIds.clear();
        if (cfg.auth != null) this.authIds.addAll(cfg.auth);
    }

    private String renameBrokenConfig() {
        if (configFile == null) return null;
        String ts = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
        File renamed = new File(configDir, configFile.getName() + ".broken-" + ts);
        boolean ok = configFile.renameTo(renamed);
        return ok ? renamed.getName() : configFile.getName();
    }

    /** Mark that we notified admins for this auth (used for rate limiting). */
    public synchronized void rememberNotify(long auth) {
        lastNotify.put(auth, System.currentTimeMillis());
        lastGlobalNotify = System.currentTimeMillis();
    }

    /** Return true if we should send an admin notification for this auth now. */
    public synchronized boolean shouldNotify(long auth, long cooldownMs) {
        long now = System.currentTimeMillis();
        if (now - lastGlobalNotify < NOTIFY_GLOBAL_MIN_INTERVAL_MS) return false;
        Long last = lastNotify.get(auth);
        if (last == null) return true;
        return now - last > cooldownMs;
    }

    /** Record a denied connect attempt in memory and append to denied_log.txt. */
    public synchronized void recordDeniedAttempt(Server server, long auth, String name, String address) {
        ensureWorld(server);
        Attempt a = new Attempt(System.currentTimeMillis(), auth, name, address);
        recent.addLast(a);
        while (recent.size() > RECENT_MAX) recent.removeFirst();
        // Update name cache for ergonomics
        rememberName(auth, name);
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

    /** @return snapshot list of recent denied attempts (most recent last). */
    public synchronized List<Attempt> getRecentAttempts() { return new ArrayList<>(recent); }
    /** @return the SteamID from the most recent denied attempt, or null. */
    public synchronized Long getLastDeniedAuth() { return recent.isEmpty() ? null : recent.getLast().auth; }

    /** @return the GateKeeper directory for the current world (created on demand). */
    public synchronized File getConfigDir(Server server) {
        ensureWorld(server);
        return configDir;
    }

    /**
     * Export saved players (SteamID,name) to known_players.txt next to the world.
     * @return number of entries written
     */
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

    /** Append an admin/audit log line (timestamped) to admin_log.txt. */
    public synchronized void logAdminAction(Server server, String line) {
        ensureWorld(server);
        if (configDir == null) return;
        if (!configDir.exists()) configDir.mkdirs();
        File out = new File(configDir, "admin_log.txt");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(out, true))) {
            bw.write(System.currentTimeMillis() + "," + line + "\n");
        } catch (IOException ignore) {}
    }

    // --- Name cache -------------------------------------------------------
    /** Remember a last-known name for the given auth and persist cache. */
    public synchronized void rememberName(long auth, String name) {
        if (name == null || name.isEmpty()) return;
        authToName.put(auth, name);
        nameToAuth.put(name.toLowerCase(Locale.ENGLISH), auth);
        saveNameCache();
    }

    private void loadNameCache() {
        if (nameCacheFile == null) return;
        if (!nameCacheFile.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(nameCacheFile))) {
            Gson gson = new Gson();
            NameCache nc = gson.fromJson(br, NameCache.class);
            if (nc != null) {
                authToName.clear();
                nameToAuth.clear();
                if (nc.authNames != null) authToName.putAll(nc.authNames);
                if (nc.names != null) {
                    // Normalize to lowercase keys
                    for (Map.Entry<String, Long> e : nc.names.entrySet()) {
                        if (e.getKey() != null && e.getValue() != null) {
                            nameToAuth.put(e.getKey().toLowerCase(Locale.ENGLISH), e.getValue());
                        }
                    }
                }
            }
        } catch (IOException | JsonSyntaxException ignore) {
            // Ignore cache errors silently; cache is best-effort
        }
    }

    private void saveNameCache() {
        if (configDir == null || nameCacheFile == null) return;
        if (!configDir.exists()) configDir.mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(nameCacheFile))) {
            NameCache nc = new NameCache();
            nc.authNames = new HashMap<>(authToName);
            // Persist name->auth with lowercase keys
            Map<String, Long> namesOut = new HashMap<>();
            for (Map.Entry<String, Long> e : nameToAuth.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    namesOut.put(e.getKey().toLowerCase(Locale.ENGLISH), e.getValue());
                }
            }
            nc.names = namesOut;
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            bw.write(gson.toJson(nc));
        } catch (IOException ignore) {}
    }
}

/** Simple JSON structure for whitelist persistence. */
class WhitelistConfig {
    boolean enabled = false;
    boolean lockdown = false;
    java.util.List<Long> auth = new java.util.ArrayList<>();
}

/**
 * Simple JSON structure for name cache persistence (ergonomics only; not authoritative).
 */
class NameCache {
    java.util.Map<Long, String> authNames = new java.util.HashMap<>();
    java.util.Map<String, Long> names = new java.util.HashMap<>();
}
