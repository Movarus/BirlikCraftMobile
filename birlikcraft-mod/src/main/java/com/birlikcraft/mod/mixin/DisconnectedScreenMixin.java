package com.birlikcraft.mod.mixin;

import com.birlikcraft.mod.BirlikState;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Joining the server failed (never reached the world): close the game. */
@Mixin(DisconnectedScreen.class)
public class DisconnectedScreenMixin {
	@Inject(method = "init", at = @At("TAIL"))
	private void birlikcraft$onInit(CallbackInfo ci) {
		if (BirlikState.connectAttempted && !BirlikState.joinedWorld) {
			BirlikState.closeGame("could not join the server");
		}
	}
}
