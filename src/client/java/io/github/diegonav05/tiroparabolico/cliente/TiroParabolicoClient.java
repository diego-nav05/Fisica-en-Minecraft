package io.github.diegonav05.tiroparabolico.cliente;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

import io.github.diegonav05.tiroparabolico.registro.ModContenido;

/** Punto de entrada del cliente: cómo se dibuja el proyectil. */
public class TiroParabolicoClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// El proyectil se ve como el objeto «bala» volando, igual que una bola de nieve.
		EntityRendererRegistry.register(ModContenido.PROYECTIL, FlyingItemEntityRenderer::new);
	}
}
