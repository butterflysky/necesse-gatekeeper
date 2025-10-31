package gatekeeper.core.events;

import gatekeeper.core.WhitelistManager;
import necesse.engine.GameEventInterface;
import necesse.engine.events.ServerClientConnectedEvent;
import necesse.engine.network.packet.PacketChatMessage;
import necesse.engine.network.packet.PacketDisconnect;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

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

        if (!manager.isEnabled()) return;
        if (manager.isWhitelisted(server, auth, name)) return;

        // Record attempt (for recent + log)
        String address = c.networkInfo == null ? null : c.networkInfo.getDisplayName();
        manager.recordDeniedAttempt(server, auth, name, address);

        // Notify admins/owners with rate limit unless in lockdown
        if (!manager.isLockdown() && manager.shouldNotify(auth, NOTIFY_COOLDOWN_MS)) {
            String msg = "[GateKeeper] Non-whitelisted connect: " + name + " (" + auth + ")" +
                    " — approve with /whitelist approve " + auth + " or /whitelist approve-last";
            for (int i = 0; i < server.getSlots(); i++) {
                ServerClient admin = server.getClient(i);
                if (admin != null && admin.getPermissionLevel().getLevel() >= necesse.engine.commands.PermissionLevel.ADMIN.getLevel()) {
                    admin.sendPacket(new PacketChatMessage(msg));
                }
            }
            manager.rememberNotify(auth);
        }

        // Disconnect with friendly message
        String reason = manager.isLockdown()
                ? "Server is in lockdown. Please contact an admin."
                : ("Not whitelisted. Ask an admin to run /whitelist approve " + auth);
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
