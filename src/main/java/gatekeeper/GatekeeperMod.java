package gatekeeper;

/**
 * GateKeeper mod entry point.
 * <p>
 * Registers the whitelist manager, server command, and connection listener
 * during init.
 */

import gatekeeper.core.WhitelistCommand;
import gatekeeper.core.WhitelistManager;
import gatekeeper.core.events.WhitelistConnectionListener;
import necesse.engine.GameEvents;
import necesse.engine.commands.CommandsManager;
import necesse.engine.modLoader.annotations.ModEntry;

@ModEntry
public class GatekeeperMod {

    public static final String MOD_ID = "gatekeeper";
    public static final String MOD_NAME = "GateKeeper";

    private static WhitelistManager whitelistManager;

    // Called first - register content and commands
    /** Register command and listener; load per-world config on first use. */
    public void init() {
        System.out.println(MOD_NAME + " is loading...");

        // Initialize whitelist manager (per-world config will load on first use)
        whitelistManager = new WhitelistManager();

        // Register server command: /whitelist
        CommandsManager.registerServerCommand(new WhitelistCommand(whitelistManager));

        // Register connection listener
        GameEvents.addListener(necesse.engine.events.ServerClientConnectedEvent.class,
                new WhitelistConnectionListener(whitelistManager));

        System.out.println(MOD_NAME + " loaded successfully!");
    }

    // Called second - load resources (images, sounds, etc...)
    /** No resources to load. Reserved for future textures/sounds. */
    public void initResources() {
    }

    // Called last - everything is loaded, safe to reference any content
    /** Post-initialization hook; not used currently. */
    public void postInit() {
    }

    public static WhitelistManager getWhitelistManager() {
        return whitelistManager;
    }
}
