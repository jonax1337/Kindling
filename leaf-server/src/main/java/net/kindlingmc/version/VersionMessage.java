package net.kindlingmc.version;

import io.papermc.paper.ServerBuildInfo;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import static net.kyori.adventure.text.Component.text;

/** The lines of {@code /version}. */
final class VersionMessage {

    private static final String SEPARATOR = " · ";

    private VersionMessage() {
    }

    /** {@code Kindling 26.3 build 3 (a32fb99, 2026-10-26)} */
    static Component title(final ServerBuildInfo build) {
        final List<String> details = new ArrayList<>(2);
        build.gitCommit().ifPresent(details::add);
        details.add(LocalDate.ofInstant(build.buildTime(), ZoneOffset.UTC).toString());
        return Component.textOfChildren(
            text(build.brandName(), NamedTextColor.GOLD),
            text(" " + build.minecraftVersionName() + " " + buildName(build)),
            text(" (" + String.join(", ", details) + ")", NamedTextColor.GRAY)
        );
    }

    private static String buildName(final ServerBuildInfo build) {
        return build.buildNumber().isPresent() ? "build " + build.buildNumber().getAsInt() : "development build";
    }

    /** {@code Minecraft 26.3 · Paper API 26.3-R0.1-SNAPSHOT · based on Leaf} */
    static Component platform(final ServerBuildInfo build, final String apiVersion) {
        final String minecraft = "Minecraft " + build.minecraftVersionName();
        return text(String.join(SEPARATOR, minecraft, "Paper API " + apiVersion, "based on Leaf"), NamedTextColor.GRAY);
    }

    static Component status(final UpdateStatus status) {
        return switch (status) {
            case UpdateStatus.Latest _ -> text("You are running the latest build.", NamedTextColor.GREEN);
            case UpdateStatus.Behind behind -> behind(behind);
            case UpdateStatus.DevelopmentBuild _ -> text("Development builds are not checked for updates.", NamedTextColor.GRAY);
            case UpdateStatus.Unavailable unavailable -> text("Could not check for updates: " + unavailable.reason() + ".", NamedTextColor.RED);
        };
    }

    private static Component behind(final UpdateStatus.Behind behind) {
        final String distance = "Build " + behind.latestBuild() + " is available, this server is " + builds(behind.buildsBehind()) + " behind. ";
        return text(distance, NamedTextColor.YELLOW).append(downloadLink());
    }

    private static String builds(final int count) {
        return count == 1 ? "1 build" : count + " builds";
    }

    private static Component downloadLink() {
        return text("[Download]", NamedTextColor.GOLD)
            .hoverEvent(text(GitHubReleases.DOWNLOAD_PAGE, NamedTextColor.WHITE))
            .clickEvent(ClickEvent.openUrl(GitHubReleases.DOWNLOAD_PAGE));
    }

    static Component previousVersion(final String version) {
        return text("Previous version: " + version, NamedTextColor.DARK_GRAY, TextDecoration.ITALIC);
    }
}
