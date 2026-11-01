package previewchunks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.dreeam.leaf.config.modules.opt.BiomeZoomShortcut;
import org.dreeam.leaf.config.modules.opt.CompiledDensity;
import org.dreeam.leaf.config.modules.opt.CompiledMaterialRules;
import org.dreeam.leaf.config.modules.opt.FastRNG;

/**
 * What a run measured: the server jar (path and SHA-256), the Leaf and Kindling switches that pick a worldgen code
 * path (the harness loads no config, so they keep their code defaults, which are also the config defaults), the JVM
 * and the options.
 */
final class Setup {
    private Setup() {
    }

    static Map<String, String> describe(Options options) {
        Map<String, String> setup = new LinkedHashMap<>();
        setup.put("command", options.command());
        setup.put("options", new TreeMap<>(options.values()).toString());
        setup.put("java", System.getProperty("java.version"));
        setup.put("cpus", Integer.toString(Runtime.getRuntime().availableProcessors()));
        String jar = System.getenv().getOrDefault("SERVER_JAR", "unknown");
        setup.put("serverJar", jar);
        setup.put("serverJarSha256", sha256(Path.of(jar)));
        setup.put("compiledDensity", Boolean.toString(CompiledDensity.enabled));
        setup.put("compiledMaterialRules", Boolean.toString(CompiledMaterialRules.enabled));
        setup.put("biomeZoomShortcut", Boolean.toString(BiomeZoomShortcut.enabled));
        setup.put("fastRngWorldgen", Boolean.toString(FastRNG.worldgenEnabled()));
        return setup;
    }

    static void print(Options options) {
        describe(options).forEach((key, value) -> System.out.println("setup " + key + ": " + value));
    }

    private static String sha256(Path file) {
        if (!Files.isRegularFile(file)) {
            return "unknown";
        }
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            for (int read = in.read(buffer); read > 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return "unreadable: " + e.getMessage();
        }
    }
}
