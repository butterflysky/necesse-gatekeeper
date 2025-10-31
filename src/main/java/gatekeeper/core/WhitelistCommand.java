package gatekeeper.core;

/**
 * GateKeeper server command entry point.
 * <p>
 * Provides administrative subcommands to manage the per-world whitelist:
 * enable/disable/status, lockdown, list, online, recent, approve-last, export,
 * add/remove (with approve/deny aliases), and helpers to approve recent attempts.
 */

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import necesse.engine.commands.CmdParameter;
import necesse.engine.commands.CommandLog;
import necesse.engine.commands.ModularChatCommand;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.commands.parameterHandlers.RestStringParameterHandler;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.network.packet.PacketDisconnect;

public class WhitelistCommand extends ModularChatCommand {
    private final WhitelistManager manager;

    /**
     * Constructs a whitelist command bound to the given manager.
     * @param manager per-world whitelist manager
     */
    public WhitelistCommand(WhitelistManager manager) {
        super(
                "whitelist",
                "Manage the GateKeeper whitelist",
                PermissionLevel.ADMIN,
                false,
                new CmdParameter("args", new RestStringParameterHandler())
        );
        this.manager = manager;
    }

    @Override
    /**
     * Parses and executes a whitelist subcommand.
     */
    public void runModular(Client client, Server server, ServerClient serverClient, Object[] args, String[] errors, CommandLog logs) {
        String rest = ((String) args[0]).trim();
        if (rest.isEmpty()) {
            printHelp(logs);
            return;
        }
        String[] parts = rest.split("\\s+");
        String sub = parts[0].toLowerCase(Locale.ENGLISH);
        switch (sub) {
            case "help":
                printHelp(logs);
                break;
            case "enable":
                manager.setEnabled(server, true);
                logs.add("GateKeeper whitelist enabled");
                break;
            case "disable":
                manager.setEnabled(server, false);
                logs.add("GateKeeper whitelist disabled");
                break;
            case "status":
                logs.add("Whitelist is " + (manager.isEnabled() ? "ENABLED" : "DISABLED"));
                logs.add("Auth IDs: " + manager.listAuths(server).size());
                break;
            case "reload":
                StringBuilder sb = new StringBuilder();
                boolean ok = manager.reload(server, sb);
                logs.add((ok ? "OK: " : "ERROR: ") + sb.toString());
                break;
            case "list":
                List<Long> auths = manager.listAuths(server);
                Collections.sort(auths);
                logs.add("Auth IDs (" + auths.size() + "):");
                for (Long a : auths) {
                    String nm = manager.getNameByAuth(server, a);
                    logs.add(" - " + a + (nm != null ? (" => " + nm) : ""));
                }
                break;
            case "lockdown":
                if (parts.length == 1 || parts[1].equalsIgnoreCase("status")) {
                    logs.add("Lockdown is " + (manager.isLockdown() ? "ON" : "OFF"));
                } else if (parts[1].equalsIgnoreCase("on")) {
                    manager.setLockdown(server, true);
                    logs.add("Lockdown enabled: only whitelisted players can join; notifications suppressed.");
                } else if (parts[1].equalsIgnoreCase("off")) {
                    manager.setLockdown(server, false);
                    logs.add("Lockdown disabled.");
                } else {
                    logs.add("Usage: /whitelist lockdown [on|off|status]");
                }
                break;
            case "online":
                for (int i = 0; i < server.getSlots(); i++) {
                    ServerClient c = server.getClient(i);
                    if (c != null) {
                        logs.add("#" + (i + 1) + ": " + c.getName() + " (" + c.authentication + ") perm=" + c.getPermissionLevel());
                    }
                }
                break;
            case "recent":
                if (parts.length >= 3 && parts[1].equalsIgnoreCase("approve")) {
                    try {
                        int idx = Integer.parseInt(parts[2]);
                        java.util.List<gatekeeper.core.WhitelistManager.Attempt> list = manager.getRecentAttempts();
                        if (idx < 1 || idx > list.size()) { logs.add("Index out of range"); break; }
                        long authIdx = list.get(idx - 1).auth;
                        boolean addedIdx = manager.addAuth(server, authIdx);
                        logs.add((addedIdx ? "Approved" : "Already whitelisted") + " auth: " + authIdx);
                        break;
                    } catch (NumberFormatException ex) {
                        logs.add("Usage: /whitelist recent approve <index>");
                        break;
                    }
                }
                java.util.List<gatekeeper.core.WhitelistManager.Attempt> list = manager.getRecentAttempts();
                if (list.isEmpty()) { logs.add("No recent denied attempts."); break; }
                int shown = 0;
                int start = Math.max(0, list.size() - 10);
                for (int i = start; i < list.size(); i++) {
                    gatekeeper.core.WhitelistManager.Attempt a = list.get(i);
                    long ageSec = (System.currentTimeMillis() - a.timeMs) / 1000;
                    logs.add((i + 1) + ". " + (a.name == null ? "<unknown>" : a.name) + " (" + a.auth + ") " + ageSec + "s ago " + (a.address == null ? "" : ("[" + a.address + "]")));
                    shown++;
                }
                logs.add("Shown " + shown + "/" + list.size() + ". Use '/whitelist recent approve <index>' to approve.");
                break;
            case "approve-last":
                Long last = manager.getLastDeniedAuth();
                if (last == null) { logs.add("No recent denied attempts."); break; }
                boolean addedLast = manager.addAuth(server, last);
                logs.add((addedLast ? "Approved" : "Already whitelisted") + " last auth: " + last);
                break;
            case "export":
                int count = manager.exportKnownPlayers(server);
                java.io.File dir = manager.getConfigDir(server);
                logs.add("Exported " + count + " known players to " + new java.io.File(dir, "known_players.txt").getPath());
                break;
            case "add":
            case "approve":
                if (parts.length < 2) { logs.add("Usage: /whitelist add <auth|name>"); break; }
                handleAdd(server, logs, parts[1]);
                break;
            case "remove":
            case "deny":
                if (parts.length < 2) { logs.add("Usage: /whitelist remove <auth|name>"); break; }
                handleRemove(server, logs, parts[1]);
                break;
            default:
                printHelp(logs);
        }
    }

    /** Handle adding by SteamID or resolving a known name to SteamID. */
    private void handleAdd(Server server, CommandLog logs, String token) {
        try {
            long auth = Long.parseLong(token);
            boolean added = manager.addAuth(server, auth);
            logs.add((added ? "Added" : "Already present") + " auth: " + auth);
        } catch (NumberFormatException nfe) {
            Long auth = manager.findAuthByName(server, token);
            if (auth != null) {
                boolean added = manager.addAuth(server, auth);
                logs.add((added ? "Added" : "Already present") + " auth from name \"" + token + "\": " + auth);
            } else {
                logs.add("Could not resolve name '" + token + "' to a SteamID. Ask them to connect once or provide their SteamID.");
            }
        }
    }

    /** Remove a SteamID (or resolve a name to SteamID) and kick connected matches. */
    private void handleRemove(Server server, CommandLog logs, String token) {
        Long authToRemove = null;
        try { authToRemove = Long.parseLong(token); } catch (NumberFormatException ignore) {
            authToRemove = manager.findAuthByName(server, token);
        }
        if (authToRemove == null) { logs.add("Could not resolve '" + token + "' to a SteamID"); return; }
        boolean removed = manager.removeAuth(server, authToRemove);
        logs.add((removed ? "Removed" : "Not present") + " auth: " + authToRemove);
        if (removed) {
            for (int i = 0; i < server.getSlots(); i++) {
                ServerClient c = server.getClient(i);
                if (c != null && c.authentication == authToRemove) {
                    String nm = c.getName();
                    server.disconnectClient(c, PacketDisconnect.kickPacket(c.slot, "Removed from whitelist"));
                    manager.logAdminAction(server, "kick_on_remove," + authToRemove + "," + (nm == null ? "" : nm));
                }
            }
        }
    }

    /** Print summarized command help to the server log. */
    private void printHelp(CommandLog logs) {
        logs.add("/whitelist enable|disable|status|reload|lockdown [on|off|status]");
        logs.add("/whitelist list|online|recent|approve-last|export");
        logs.add("/whitelist add <auth|name> (name resolves to auth if known)");
        logs.add("/whitelist remove <auth|name> (deny is alias; removing by name resolves to auth)");
        logs.add("/whitelist recent approve <index>");
    }
}
