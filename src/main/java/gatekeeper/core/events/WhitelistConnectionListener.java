package gatekeeper.core.events;

import gatekeeper.core.WhitelistManager;
import necesse.engine.GameEventInterface;
import necesse.engine.events.ServerClientConnectedEvent;
import necesse.engine.network.packet.PacketChatMessage;
import necesse.engine.network.packet.PacketDisconnect;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.commands.PermissionLevel;

/**
 * Enforces whitelist policy on new client connections.
 * <p>
 * If the client is not whitelisted (and the whitelist is enabled), a denied
 * attempt is recorded, admins are notified with rate-limiting (unless in
 * lockdown), and the client is disconnected with a friendly message.
 */
public class WhitelistConnectionListener implements GameEventInterface<ServerClientConnectedEvent> {
    private volatile boolean disposed = false;
    private final WhitelistManager manager;

    // Admin notify cooldown per auth (ms)
    private static final long NOTIFY_COOLDOWN_MS = 60_000L;

    /**
     * @param manager shared whitelist manager instance
     */
    public WhitelistConnectionListener(WhitelistManager manager) {
        this.manager = manager;
    }

    @Override
    /** No-op; listener lifecycle is controlled by the mod. */
    public void init(Runnable removeCallback) {
        // No-op; store no callback, rely on disposed flag
    }

    @Override
    /** Apply whitelist checks when a client finishes connecting. */
    public void onEvent(ServerClientConnectedEvent event) {
        if (disposed || event == null || event.client == null) return;
        ServerClient c = event.client;
        Server server = c.getServer();

        long auth = c.authentication;
        String name = c.getName();

        // Admins/Owners bypass whitelist and get auto-added for future access
        boolean isPrivileged = c.getPermissionLevel().getLevel() >= PermissionLevel.ADMIN.getLevel();
        if (isPrivileged) {
            if (!manager.isWhitelisted(server, auth, name)) {
                boolean added = manager.addAuth(server, auth);
                if (added) {
                    manager.logAdminAction(server, "auto_add_privileged_on_join," + auth + "," + (name == null ? "" : name));
                }
            }
            // Remember name for ergonomics
            manager.rememberName(auth, name);
            String status = manager.isEnabled() ? "ENABLED" : "DISABLED";
            c.sendPacket(new PacketChatMessage("[GateKeeper] Whitelist is " + status + ". Use /whitelist help"));
            return;
        }

        if (!manager.isEnabled()) return;
        if (manager.isWhitelisted(server, auth, name)) return;

        // Record attempt (for recent + log + name cache)
        String address = c.networkInfo == null ? null : c.networkInfo.getDisplayName();
        manager.recordDeniedAttempt(server, auth, name, address);

        // Notify admins/owners with rate limit unless in lockdown
        if (!manager.isLockdown() && manager.shouldNotify(auth, NOTIFY_COOLDOWN_MS)) {
            String who = (name == null || name.isEmpty()) ? "<unknown>" : name;
            String msg = "[GateKeeper] Connection blocked for non-whitelisted user: " + who +
                    " — approve with /whitelist approve " + who + " or /whitelist approve-last";
            for (int i = 0; i < server.getSlots(); i++) {
                ServerClient admin = server.getClient(i);
                if (admin != null && admin.getPermissionLevel().getLevel() >= necesse.engine.commands.PermissionLevel.ADMIN.getLevel()) {
                    admin.sendPacket(new PacketChatMessage(msg));
                }
            }
            manager.rememberNotify(auth);
        }

        // Disconnect with friendly message
        String who = (name == null || name.isEmpty()) ? "you" : name;
        String reason = manager.isLockdown()
                ? "Server is in lockdown. Please contact an admin."
                : ("Not whitelisted. Ask an admin to run /whitelist approve " + who);
        server.disconnectClient(c, PacketDisconnect.kickPacket(c.slot, reason));
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    public void dispose() {
        disposed = true;
    }
}
