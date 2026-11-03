package net.kindlingmc.branding;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FirstStartDefaults {

    static final String MOTD = "§6Kindling§r §8|§r §aA Minecraft Server";
    private static final Logger LOGGER = LoggerFactory.getLogger(FirstStartDefaults.class);

    private FirstStartDefaults() {
    }

    public static void initialize(final Path propertiesFile, final Path iconFile) {
        // CREATE_NEW also preserves empty files and dangling symbolic links.
        try (Writer writer = Files.newBufferedWriter(propertiesFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final Properties defaults = new Properties();
            defaults.setProperty("motd", MOTD);
            defaults.store(writer, "Kindling server properties");
        } catch (FileAlreadyExistsException existing) {
            return;
        } catch (IOException failure) {
            // Cosmetic defaults must not prevent the normal settings loader from running.
            LOGGER.warn("Could not initialize Kindling defaults in {}", propertiesFile, failure);
            return;
        }

        copyIcon(iconFile);
    }

    private static void copyIcon(final Path iconFile) {
        try (InputStream icon = FirstStartDefaults.class.getResourceAsStream("/kindling/server-icon.png")) {
            if (icon == null) {
                throw new IOException("Bundled Kindling server icon is missing");
            }
            Files.copy(icon, iconFile);
        } catch (FileAlreadyExistsException existing) {
            // A supplied icon always wins, including one created during initialization.
        } catch (IOException failure) {
            // The server can still start without a server-list icon.
            LOGGER.warn("Could not install the default Kindling server icon at {}", iconFile, failure);
        }
    }
}
