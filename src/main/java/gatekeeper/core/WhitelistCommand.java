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
                manager.setEnabled(true);
                logs.add("GateKeeper whitelist enabled");
                break;
            case "disable":
                manager.setEnabled(false);
                logs.add("GateKeeper whitelist disabled");
                break;
            case "status":
                logs.add("Whitelist is " + (manager.isEnabled() ? "ENABLED" : "DISABLED"));
                logs.add("Auth IDs: " + manager.listAuths().size() + ", Names: " + manager.listNames().size());
                break;
            case "list":
                List<Long> auths = manager.listAuths();
                Collections.sort(auths);
                logs.add("Auth IDs (" + auths.size() + "): " + auths);
                List<String> names = manager.listNames();
                names.sort(Comparator.naturalOrder());
                logs.add("Names (" + names.size() + "): " + names);
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
            boolean added = manager.addAuth(auth);
            logs.add((added ? "Added" : "Already present") + " auth: " + auth);
        } catch (NumberFormatException nfe) {
            // treat as name
            Long auth = manager.findAuthByName(server, token);
            if (auth != null) {
                boolean added = manager.addAuth(auth);
                logs.add((added ? "Added" : "Already present") + " auth from name \"" + token + "\": " + auth);
            } else {
                boolean added = manager.addName(token);
                logs.add((added ? "Added" : "Already present") + " name: " + token + ". Will allow matching name on connect.");
            }
        }
    }

    private void handleRemove(Server server, CommandLog logs, String token) {
        try {
            long auth = Long.parseLong(token);
            boolean removed = manager.removeAuth(auth);
            logs.add((removed ? "Removed" : "Not present") + " auth: " + auth);
        } catch (NumberFormatException nfe) {
            boolean removed = manager.removeName(token);
            logs.add((removed ? "Removed" : "Not present") + " name: " + token);
        }
    }

    private void printHelp(CommandLog logs) {
        logs.add("/whitelist enable|disable|status");
        logs.add("/whitelist list");
        logs.add("/whitelist add <auth|name>");
        logs.add("/whitelist remove <auth|name>");
        logs.add("/whitelist approve <auth|name>");
        logs.add("/whitelist deny <auth|name>");
    }
}

