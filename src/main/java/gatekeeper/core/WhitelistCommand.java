package gatekeeper.core;

import java.util.Collections;
import java.util.Comparator;
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
    public void runModular(Client client, Server server, ServerClient serverClient, Object[] args, String[] errors, CommandLog logs) {
        String rest = ((String) args[0]).trim();
        if (rest.isEmpty()) {
            printHelp(logs);
            return;
        }
        String[] parts = rest.split("\\s+");
        String sub = parts[0].toLowerCase(Locale.ENGLISH);
        switch (sub) {
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
                logs.add("Auth IDs: " + manager.listAuths(server).size() + ", Names: " + manager.listNames(server).size());
                break;
            case "list":
                List<Long> auths = manager.listAuths(server);
                Collections.sort(auths);
                logs.add("Auth IDs (" + auths.size() + "): " + auths);
                List<String> names = manager.listNames(server);
                names.sort(Comparator.naturalOrder());
                logs.add("Names (" + names.size() + "): " + names);
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

    private void handleAdd(Server server, CommandLog logs, String token) {
        try {
            long auth = Long.parseLong(token);
            boolean added = manager.addAuth(server, auth);
            logs.add((added ? "Added" : "Already present") + " auth: " + auth);
        } catch (NumberFormatException nfe) {
            // treat as name
            Long auth = manager.findAuthByName(server, token);
            if (auth != null) {
                boolean added = manager.addAuth(server, auth);
                logs.add((added ? "Added" : "Already present") + " auth from name \"" + token + "\": " + auth);
            } else {
                boolean added = manager.addName(server, token);
                logs.add((added ? "Added" : "Already present") + " name: " + token + ". Will allow matching name on connect.");
            }
        }
    }

    private void handleRemove(Server server, CommandLog logs, String token) {
        try {
            long auth = Long.parseLong(token);
            boolean removed = manager.removeAuth(server, auth);
            logs.add((removed ? "Removed" : "Not present") + " auth: " + auth);
            if (removed) {
                // Kick connected clients with this auth
                for (int i = 0; i < server.getSlots(); i++) {
                    ServerClient c = server.getClient(i);
                    if (c != null && c.authentication == auth) {
                        server.disconnectClient(c, PacketDisconnect.kickPacket(c.slot, "Removed from whitelist"));
                    }
                }
            }
        } catch (NumberFormatException nfe) {
            boolean removed = manager.removeName(server, token);
            logs.add((removed ? "Removed" : "Not present") + " name: " + token);
            if (removed) {
                // Kick connected clients matching this name who are no longer whitelisted
                for (int i = 0; i < server.getSlots(); i++) {
                    ServerClient c = server.getClient(i);
                    if (c != null && c.getName() != null && c.getName().equalsIgnoreCase(token)) {
                        boolean stillAllowed = manager.isWhitelisted(server, c.authentication, c.getName());
                        if (!stillAllowed) {
                            server.disconnectClient(c, PacketDisconnect.kickPacket(c.slot, "Removed from whitelist"));
                        }
                    }
                }
            }
        }
    }

    private void printHelp(CommandLog logs) {
        logs.add("/whitelist enable|disable|status|lockdown [on|off|status]");
        logs.add("/whitelist list|online|recent|approve-last|export");
        logs.add("/whitelist add <auth|name> (approve is alias)");
        logs.add("/whitelist remove <auth|name> (deny is alias)");
        logs.add("/whitelist recent approve <index>");
    }
}
