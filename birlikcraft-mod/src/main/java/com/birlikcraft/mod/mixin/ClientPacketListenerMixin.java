package com.birlikcraft.mod.mixin;

import com.birlikcraft.mod.BirlikState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The login packet means the player joined the server's world. */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleLogin", at = @At("HEAD"))
	private void birlikcraft$onLogin(CallbackInfo ci) {
		BirlikState.joinedWorld = true;
	}
}
