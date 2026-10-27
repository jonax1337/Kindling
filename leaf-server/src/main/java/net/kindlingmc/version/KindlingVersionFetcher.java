package net.kindlingmc.version;

import com.destroystokyo.paper.VersionHistoryManager;
import com.destroystokyo.paper.util.VersionFetcher;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import io.papermc.paper.ServerBuildInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.slf4j.Logger;

/** Writes the whole {@code /version} message and compares the running build with the Kindling releases on GitHub. */
public final class KindlingVersionFetcher implements VersionFetcher {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final long CACHE_MILLIS = TimeUnit.MINUTES.toMillis(12);

    @Override
    public long getCacheTime() {
        return CACHE_MILLIS;
    }

    @Override
    public Component getVersionMessage() {
        final ServerBuildInfo build = ServerBuildInfo.buildInfo();
        final List<Component> lines = new ArrayList<>(List.of(
            VersionMessage.title(build),
            VersionMessage.platform(build, Bukkit.getBukkitVersion()),
            VersionMessage.status(updateStatus(build))
        ));
        previousVersion().map(VersionMessage::previousVersion).ifPresent(lines::add);
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private static UpdateStatus updateStatus(final ServerBuildInfo build) {
        final OptionalInt runningBuild = build.buildNumber();
        if (runningBuild.isEmpty()) {
            return new UpdateStatus.DevelopmentBuild();
        }
        try {
            return UpdateStatus.compare(runningBuild.getAsInt(), GitHubReleases.buildNumbers(build.minecraftVersionId()));
        } catch (final IOException | JsonParseException e) {
            LOGGER.warn("Could not read the Kindling releases on GitHub: {}", e.toString());
            return new UpdateStatus.Unavailable("the releases on GitHub could not be read");
        }
    }

    private static Optional<String> previousVersion() {
        final VersionHistoryManager.VersionData history = VersionHistoryManager.INSTANCE.getVersionData();
        return history == null ? Optional.empty() : Optional.ofNullable(history.getOldVersion());
    }
}
