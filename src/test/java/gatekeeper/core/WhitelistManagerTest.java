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
    void saveLoad_roundTrip_authOnlyFormat() throws Exception {
        Server server = mockServerForWorldPath(tempDir);
        WhitelistManager mgr = new WhitelistManager();
        mgr.setEnabled(server, true);
        mgr.addAuth(server, 100L);
        mgr.addAuth(server, 200L);
        // Read file
        File file = new File(new File(tempDir, "GateKeeper"), "whitelist.txt");
        assertTrue(file.exists());
        String text = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("enabled=true"));
        assertTrue(text.contains("auth:100"));
        assertTrue(text.contains("auth:200"));
        assertFalse(text.contains("name:"));

        // New manager should load same
        WhitelistManager mgr2 = new WhitelistManager();
        assertTrue(mgr2.isWhitelisted(server, 100L, null));
        assertTrue(mgr2.isWhitelisted(server, 200L, null));
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
