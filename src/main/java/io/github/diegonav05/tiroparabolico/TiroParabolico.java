package io.github.diegonav05.tiroparabolico;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.diegonav05.tiroparabolico.comando.ComandoCanon;
import io.github.diegonav05.tiroparabolico.registro.ModContenido;

/** Punto de entrada común (cliente y servidor) del mod. */
public class TiroParabolico implements ModInitializer {
	public static final String MOD_ID = "tiroparabolico";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String ruta) {
		return Identifier.of(MOD_ID, ruta);
	}

	@Override
	public void onInitialize() {
		ModContenido.registrar();
		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> ComandoCanon.registrar(dispatcher));
		LOGGER.info("Tiro Parabólico cargado: 1 bloque = 1 m, 1 tick = 0,05 s");
	}
}
