package gatekeeper.core.events;

import gatekeeper.core.WhitelistManager;
import necesse.engine.GameEventInterface;
import necesse.engine.events.ServerClientConnectedEvent;
import necesse.engine.network.packet.PacketChatMessage;
import necesse.engine.network.packet.PacketDisconnect;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

public class WhitelistConnectionListener implements GameEventInterface<ServerClientConnectedEvent> {
    private volatile boolean disposed = false;
    private final WhitelistManager manager;

    // Admin notify cooldown per auth (ms)
    private static final long NOTIFY_COOLDOWN_MS = 60_000L;

    public WhitelistConnectionListener(WhitelistManager manager) {
        this.manager = manager;
    }

    @Override
    public void init(Runnable removeCallback) {
        // No-op; store no callback, rely on disposed flag
    }

    @Override
    public void onEvent(ServerClientConnectedEvent event) {
        if (disposed || event == null || event.client == null) return;
        ServerClient c = event.client;
        Server server = c.getServer();

        long auth = c.authentication;
        String name = c.getName();

        if (!manager.isEnabled()) return;
        if (manager.isWhitelisted(server, auth, name)) return;

        // Notify admins/owners with rate limit
        if (manager.shouldNotify(auth, NOTIFY_COOLDOWN_MS)) {
            String msg = "[GateKeeper] Non-whitelisted connect: " + name + " (" + auth + ")" +
                    " — approve with /whitelist approve " + auth + " or add by name.";
            for (int i = 0; i < server.getSlots(); i++) {
                ServerClient admin = server.getClient(i);
                if (admin != null && admin.getPermissionLevel().getLevel() >= necesse.engine.commands.PermissionLevel.ADMIN.getLevel()) {
                    admin.sendPacket(new PacketChatMessage(msg));
                }
            }
            manager.rememberNotify(auth);
        }

        // Disconnect with friendly message
        String reason = "Not whitelisted. Ask an admin to run /whitelist approve " + auth + ".";
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
