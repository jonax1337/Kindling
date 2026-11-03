package net.kindlingmc.branding;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstStartDefaultsTest {

    @TempDir
    Path directory;

    @Test
    void freshServerGetsKindlingMotdAndBundledIcon() throws IOException {
        final Path propertiesFile = this.directory.resolve("server.properties");
        final Path iconFile = this.directory.resolve("server-icon.png");

        FirstStartDefaults.initialize(propertiesFile, iconFile);

        final Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        assertEquals(FirstStartDefaults.MOTD, properties.getProperty("motd"));
        try (var bundled = FirstStartDefaults.class.getResourceAsStream("/kindling/server-icon.png")) {
            assertArrayEquals(bundled.readAllBytes(), Files.readAllBytes(iconFile));
        }
        final BufferedImage icon = ImageIO.read(iconFile.toFile());
        assertEquals(64, icon.getWidth());
        assertEquals(64, icon.getHeight());
    }

    @Test
    void bundledIconKeepsItsBackgroundAndLeafVeinTransparent() throws IOException {
        final BufferedImage icon;
        try (var bundled = FirstStartDefaults.class.getResourceAsStream("/kindling/server-icon.png")) {
            icon = ImageIO.read(bundled);
        }

        assertTrue(icon.getColorModel().hasAlpha());
        assertEquals(0, icon.getRGB(0, 0) >>> 24);
        assertEquals(0, icon.getRGB(38, 34) >>> 24);
        assertEquals(255, icon.getRGB(52, 0) >>> 24);
    }

    @Test
    void existingServerKeepsItsPropertiesAndDoesNotGetAnIcon() throws IOException {
        final Path propertiesFile = this.directory.resolve("server.properties");
        final String original = "# existing Leaf server\nmotd=A Minecraft Server\nserver-port=25999\n";
        Files.writeString(propertiesFile, original);
        final Path iconFile = this.directory.resolve("server-icon.png");

        FirstStartDefaults.initialize(propertiesFile, iconFile);

        assertEquals(original, Files.readString(propertiesFile));
        assertFalse(Files.exists(iconFile));
    }

    @Test
    void existingPropertiesWithoutMotdStayUntouched() throws IOException {
        final Path propertiesFile = this.directory.resolve("server.properties");
        Files.createFile(propertiesFile);
        final Path iconFile = this.directory.resolve("server-icon.png");

        FirstStartDefaults.initialize(propertiesFile, iconFile);

        assertEquals(0, Files.size(propertiesFile));
        assertFalse(Files.exists(iconFile));
    }

    @Test
    void freshServerKeepsAnExistingCustomIcon() throws IOException {
        final Path propertiesFile = this.directory.resolve("server.properties");
        final Path iconFile = this.directory.resolve("server-icon.png");
        final byte[] original = {1, 2, 3};
        Files.write(iconFile, original);

        FirstStartDefaults.initialize(propertiesFile, iconFile);

        assertTrue(Files.exists(propertiesFile));
        assertArrayEquals(original, Files.readAllBytes(iconFile));
    }

    @Test
    void removedIconIsNotRecreatedOnRestart() throws IOException {
        final Path propertiesFile = this.directory.resolve("server.properties");
        final Path iconFile = this.directory.resolve("server-icon.png");
        FirstStartDefaults.initialize(propertiesFile, iconFile);
        Files.delete(iconFile);
        Files.writeString(propertiesFile, "motd=My own server\n");

        FirstStartDefaults.initialize(propertiesFile, iconFile);

        assertEquals("motd=My own server\n", Files.readString(propertiesFile));
        assertFalse(Files.exists(iconFile));
    }

    @Test
    void missingPropertiesDirectoryDoesNotInstallAnIconOrAbortStartup() {
        final Path propertiesFile = this.directory.resolve("missing/server.properties");
        final Path iconFile = this.directory.resolve("server-icon.png");

        FirstStartDefaults.initialize(propertiesFile, iconFile);

        assertFalse(Files.exists(propertiesFile));
        assertFalse(Files.exists(iconFile));
    }

    @Test
    void failedIconCopyKeepsTheNewPropertiesAndDoesNotAbortStartup() {
        final Path propertiesFile = this.directory.resolve("server.properties");
        final Path iconFile = this.directory.resolve("missing/server-icon.png");

        FirstStartDefaults.initialize(propertiesFile, iconFile);

        assertTrue(Files.exists(propertiesFile));
        assertFalse(Files.exists(iconFile));
    }

    @Test
    void customPropertiesPathDoesNotMoveTheServerIcon() throws IOException {
        final Path configDirectory = Files.createDirectory(this.directory.resolve("config"));
        final Path propertiesFile = configDirectory.resolve("custom.properties");
        final Path iconFile = this.directory.resolve("server-icon.png");

        FirstStartDefaults.initialize(propertiesFile, iconFile);

        assertTrue(Files.exists(propertiesFile));
        assertTrue(Files.exists(iconFile));
        assertFalse(Files.exists(configDirectory.resolve("server-icon.png")));
    }
}
