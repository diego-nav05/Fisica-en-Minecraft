package io.github.diegonav05.tiroparabolico.fisica;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** Comprueba la regla del proyecto: el paquete de física no depende de Minecraft ni de Fabric. */
class ArquitecturaTest {
	private static final Path PAQUETE = Path.of("src/main/java/io/github/diegonav05/tiroparabolico/fisica");
	private static final List<String> PROHIBIDO = List.of("net.minecraft", "net.fabricmc", "com.mojang");

	@Test
	void elPaqueteDeFisicaNoUsaClasesDeMinecraft() throws IOException {
		assertTrue(Files.isDirectory(PAQUETE), "No se encuentra " + PAQUETE.toAbsolutePath());
		try (Stream<Path> archivos = Files.walk(PAQUETE)) {
			List<Path> fuentes = archivos.filter(p -> p.toString().endsWith(".java")).toList();
			assertFalse(fuentes.isEmpty());
			for (Path fuente : fuentes) {
				String codigo = Files.readString(fuente);
				for (String prohibido : PROHIBIDO) {
					assertFalse(codigo.contains(prohibido), fuente.getFileName() + " hace referencia a " + prohibido);
				}
			}
		}
	}
}
