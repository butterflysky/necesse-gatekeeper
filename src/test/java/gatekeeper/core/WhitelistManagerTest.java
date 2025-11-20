package gatekeeper.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import necesse.engine.network.server.Server;
import necesse.engine.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhitelistManagerTest {
    private File tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("gk-world-").toFile();
    }

    @AfterEach
    void tearDown() {
        if (tempDir != null && tempDir.exists()) {
            deleteRec(tempDir);
        }
    }

    private static void deleteRec(File f) {
        if (f.isDirectory()) for (File c : f.listFiles()) deleteRec(c);
        f.delete();
    }

    private Server mockServerForWorldPath(File worldPath) throws Exception {
        World world = mock(World.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS));
        java.lang.reflect.Field fp = World.class.getDeclaredField("filePath");
        fp.setAccessible(true);
        fp.set(world, worldPath);
        when(world.getUniqueID()).thenReturn(123456789L);
        when(world.getUsedPlayerNames()).thenReturn(new HashMap<>());
        Server server = mock(Server.class, RETURNS_DEEP_STUBS);
        server.world = world;
        return server;
    }

    @Test
    void defaultsDisabled_newWorld_allowsAllWhenDisabled() throws Exception {
        Server server = mockServerForWorldPath(tempDir);
        WhitelistManager mgr = new WhitelistManager();
        // Disabled by default; should allow all
        assertFalse(mgr.isEnabled());
        assertFalse(mgr.isLockdown());
        assertTrue(mgr.isWhitelisted(server, 111L, "any"));
    }

    @Test
    void enable_thenAuthOnlyEnforced() throws Exception {
        Server server = mockServerForWorldPath(tempDir);
        WhitelistManager mgr = new WhitelistManager();
        mgr.setEnabled(server, true);
        mgr.addAuth(server, 42L);
        assertTrue(mgr.isWhitelisted(server, 42L, "ignored"));
        assertFalse(mgr.isWhitelisted(server, 7L, "ignored"));
    }

    @Test
    void saveLoad_roundTrip_jsonFormat() throws Exception {
        Server server = mockServerForWorldPath(tempDir);
        WhitelistManager mgr = new WhitelistManager();
        mgr.setEnabled(server, true);
        mgr.setLockdown(server, true);
        mgr.addAuth(server, 100L);
        mgr.addAuth(server, 200L);
        // New manager should load same (round-trip)
        WhitelistManager mgr2 = new WhitelistManager();
        // Touch world-bound API to trigger load
        assertTrue(mgr2.isWhitelisted(server, 100L, null));
        assertTrue(mgr2.isWhitelisted(server, 200L, null));
        assertFalse(mgr2.isWhitelisted(server, 300L, null));
        assertTrue(mgr2.isEnabled());
        assertTrue(mgr2.isLockdown());

        // Compare sets ignoring order
        java.util.List<Long> ids = mgr2.listAuths(server);
        java.util.Set<Long> set = new java.util.HashSet<>(ids);
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(100L, 200L)), set);
    }

    @Test
    void malformedJson_renamed_defaultsRemain_and_saveRewrites() throws Exception {
        // Prepare malformed JSON file
        File gk = new File(tempDir, "GateKeeper");
        gk.mkdirs();
        File cfg = new File(gk, "whitelist.json");
        java.nio.file.Files.write(cfg.toPath(), "{ not: json,".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Server server = mockServerForWorldPath(tempDir);
        WhitelistManager mgr = new WhitelistManager();
        // Touch to trigger load
        assertTrue(mgr.isWhitelisted(server, 1L, null)); // defaults allow
        assertFalse(mgr.isEnabled());
        assertFalse(mgr.isLockdown());

        // Broken file should have been renamed
        String[] names = gk.list();
        assertNotNull(names);
        boolean renamedFound = java.util.Arrays.stream(names).anyMatch(n -> n.startsWith("whitelist.json.broken-"));
        assertTrue(renamedFound);

        // Saving should write a fresh valid JSON file
        mgr.setEnabled(server, true);
        File newCfg = new File(gk, "whitelist.json");
        assertTrue(newCfg.exists());
        String body = new String(java.nio.file.Files.readAllBytes(newCfg.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("\"enabled\": true"));
    }

    @Test
    void reload_withMalformed_doesNotChangeState_and_renames() throws Exception {
        // Write a valid JSON first
        Server server = mockServerForWorldPath(tempDir);
        WhitelistManager mgr = new WhitelistManager();
        mgr.setEnabled(server, true);
        mgr.addAuth(server, 999L);

        // Overwrite on disk with garbage
        File gk = new File(tempDir, "GateKeeper");
        File cfg = new File(gk, "whitelist.json");
        java.nio.file.Files.write(cfg.toPath(), "garbage".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        StringBuilder msg = new StringBuilder();
        boolean ok = mgr.reload(server, msg);
        assertFalse(ok);
        // State should remain unchanged in memory
        assertTrue(mgr.isEnabled());
        assertTrue(mgr.isWhitelisted(server, 999L, null));

        // File should have been renamed
        String[] names = gk.list();
        assertNotNull(names);
        boolean renamedFound = java.util.Arrays.stream(names).anyMatch(n -> n.startsWith("whitelist.json.broken-"));
        assertTrue(renamedFound);
    }

    @Test
    void recordDeniedAttempt_appendsAndKeepsRecent() throws Exception {
        Server server = mockServerForWorldPath(tempDir);
        WhitelistManager mgr = new WhitelistManager();
        mgr.recordDeniedAttempt(server, 555L, "Test", "127.0.0.1");
        assertFalse(mgr.getRecentAttempts().isEmpty());
        File log = new File(new File(tempDir, "GateKeeper"), "denied_log.txt");
        assertTrue(log.exists());
        String body = new String(Files.readAllBytes(log.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains(",555,Test,127.0.0.1"));
    }

    @Test
    void nameCache_persistsAndResolvesBothWays() throws Exception {
        Server server = mockServerForWorldPath(tempDir);
        WhitelistManager mgr = new WhitelistManager();
        // Simulate a denied attempt which should also update the cache
        mgr.recordDeniedAttempt(server, 76561198056903463L, "butterflysky", "1.2.3.4");

        // New manager instance should load cache
        WhitelistManager mgr2 = new WhitelistManager();
        // Resolve by name (case-insensitive)
        Long id = mgr2.findAuthByName(server, "butterflysky");
        assertNotNull(id);
        assertEquals(76561198056903463L, id.longValue());
        // Resolve by auth to name
        String name = mgr2.getNameByAuth(server, 76561198056903463L);
        assertEquals("butterflysky", name);
    }

    @Test
    void rateLimit_perAuthAndGlobal() throws Exception {
        WhitelistManager mgr = new WhitelistManager();
        long auth1 = 1L;
        // First time should notify
        assertTrue(mgr.shouldNotify(auth1, 60_000L));
        mgr.rememberNotify(auth1);
        // Immediately again should be blocked (global + per-auth)
        assertFalse(mgr.shouldNotify(auth1, 60_000L));
        // Different auth shortly after also blocked by global
        assertFalse(mgr.shouldNotify(2L, 60_000L));
        // Wait a bit beyond global interval
        Thread.sleep(3100);
        assertTrue(mgr.shouldNotify(2L, 60_000L));
    }

    // Online resolution relies on reading final fields in game classes.
    // Covered implicitly via integration, omitted from unit tests.
}
