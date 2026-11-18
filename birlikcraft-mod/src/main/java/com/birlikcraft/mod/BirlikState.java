package com.birlikcraft.mod;

import net.minecraft.client.Minecraft;

/**
 * Server-only flow of BirlikCraft Mobile. The launcher starts the game straight into the
 * BirlikCraft server (--quickPlayMultiplayer):
 * - if joining fails, the game closes and the launcher opens again;
 * - after a disconnect the disconnect screen stays visible, and leaving it (or the pause
 *   menu's disconnect button) would show Minecraft's title screen, which closes the game too.
 */
public final class BirlikState {
	/** A connection to a server was started in this session. */
	public static volatile boolean connectAttempted;
	/** The player actually joined the world after the last connection attempt. */
	public static volatile boolean joinedWorld;
	private static volatile boolean closing;

	private BirlikState() {}

	/** Set by the launcher's test mode (-Dbirlikcraft.testMode=true): never close the game. */
	public static final boolean TEST_MODE = Boolean.getBoolean("birlikcraft.testMode");

	public static void closeGame(String reason) {
		if (TEST_MODE) {
			BirlikCraftClient.LOGGER.info("BirlikCraft test mode: not closing the game ({})", reason);
			return;
		}
		if (closing) return;
		closing = true;
		BirlikCraftClient.LOGGER.info("BirlikCraft: closing the game ({})", reason);
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(minecraft::stop);
	}
}
