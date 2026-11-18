package com.birlikcraft.mod;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** BirlikCraft Mobile client mod entry point. */
public class BirlikCraftClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("BirlikCraft");

	@Override
	public void onInitializeClient() {
		LOGGER.info("BirlikCraft mod loaded");
	}
}
