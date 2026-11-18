package com.birlikcraft.mod.mixin;

import com.birlikcraft.mod.BirlikState;
import net.minecraft.client.gui.screens.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remember that the game started connecting to a server. */
@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
	@Inject(method = "startConnecting", at = @At("HEAD"))
	private static void birlikcraft$onStartConnecting(CallbackInfo ci) {
		BirlikState.connectAttempted = true;
		BirlikState.joinedWorld = false;
	}
}
