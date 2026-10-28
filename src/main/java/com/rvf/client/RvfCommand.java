package com.rvf.client;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Registers the client-side command /rvf config.
 * The screen opens on the next tick when no screen is open, because the chat screen closes right after a command
 * and would otherwise close the new screen as well.
 */
public final class RvfCommand {

    private RvfCommand() {}

    private static boolean openRequested = false;

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommands.literal("rvf")
                .then(ClientCommands.literal("config")
                    .executes(context -> {
                        openRequested = true;
                        return 1;
                    }))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openRequested && client.gui.screen() == null) {
                openRequested = false;
                client.setScreenAndShow(new RvfConfigScreen(null));
            }
        });
    }

}
