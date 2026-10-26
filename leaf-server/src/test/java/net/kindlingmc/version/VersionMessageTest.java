package net.kindlingmc.version;

import io.papermc.paper.ServerBuildInfo;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VersionMessageTest {

    @Test
    void titleOfAReleaseBuild() {
        final ServerBuildInfo build = build(OptionalInt.of(3), Optional.of("a32fb99"));

        assertEquals("Kindling 26.3 build 3 (a32fb99, 2026-10-26)", plain(VersionMessage.title(build)));
    }

    @Test
    void titleOfADevelopmentBuildWithoutGit() {
        final ServerBuildInfo build = build(OptionalInt.empty(), Optional.empty());

        assertEquals("Kindling 26.3 development build (2026-10-26)", plain(VersionMessage.title(build)));
    }

    @Test
    void platformNamesMinecraftTheApiAndLeaf() {
        final ServerBuildInfo build = build(OptionalInt.of(3), Optional.of("a32fb99"));

        assertEquals("Minecraft 26.3 · Paper API 26.3-R0.1-SNAPSHOT · based on Leaf", plain(VersionMessage.platform(build, "26.3-R0.1-SNAPSHOT")));
    }

    @Test
    void anOutdatedBuildNamesTheNewestBuildAndTheDistance() {
        assertEquals("Build 5 is available, this server is 2 builds behind. [Download]", plain(VersionMessage.status(new UpdateStatus.Behind(5, 2))));
        assertEquals("Build 3 is available, this server is 1 build behind. [Download]", plain(VersionMessage.status(new UpdateStatus.Behind(3, 1))));
    }

    @Test
    void theDownloadLinkOpensTheReleasePage() {
        final Component link = VersionMessage.status(new UpdateStatus.Behind(5, 2)).children().getFirst();

        assertEquals(ClickEvent.openUrl(GitHubReleases.DOWNLOAD_PAGE), link.clickEvent());
    }

    private static ServerBuildInfo build(final OptionalInt buildNumber, final Optional<String> gitCommit) {
        final ServerBuildInfo build = mock(ServerBuildInfo.class);
        when(build.brandName()).thenReturn("Kindling");
        when(build.minecraftVersionName()).thenReturn("26.3");
        when(build.buildNumber()).thenReturn(buildNumber);
        when(build.gitCommit()).thenReturn(gitCommit);
        when(build.buildTime()).thenReturn(Instant.parse("2026-10-26T14:54:27Z"));
        return build;
    }

    private static String plain(final Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
