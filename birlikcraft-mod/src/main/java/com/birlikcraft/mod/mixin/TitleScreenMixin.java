package com.birlikcraft.mod.mixin;

import com.birlikcraft.mod.BirlikState;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BirlikCraft has no Minecraft title screen: once the session has been on the server, reaching
 * the title screen (leaving the disconnect screen, pause menu "Disconnect") closes the game and
 * the launcher opens again.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {
	@Inject(method = "init", at = @At("HEAD"))
	private void birlikcraft$onInit(CallbackInfo ci) {
		if (BirlikState.connectAttempted) {
			BirlikState.closeGame("title screen");
		}
	}
}
