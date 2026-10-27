package net.kindlingmc.version;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** Reads the build numbers of the Kindling releases on GitHub; a release is tagged {@code <minecraft version>-<build>}. */
final class GitHubReleases {

    static final String DOWNLOAD_PAGE = "https://github.com/jonax1337/Kindling/releases";
    private static final String RELEASES_API = "https://api.github.com/repos/jonax1337/Kindling/releases?per_page=100";
    private static final int TIMEOUT_MILLIS = 5000;
    private static final Gson GSON = new Gson();

    private GitHubReleases() {
    }

    static List<Integer> buildNumbers(final String minecraftVersion) throws IOException {
        return buildNumbersIn(fetchReleases(), minecraftVersion);
    }

    static List<Integer> buildNumbersIn(final JsonArray releases, final String minecraftVersion) {
        final String tagPrefix = minecraftVersion + "-";
        final List<Integer> builds = new ArrayList<>();
        for (final JsonElement release : releases) {
            if (release instanceof JsonObject object && object.get("tag_name") instanceof JsonPrimitive tag) {
                buildNumberOf(tag.getAsString(), tagPrefix).ifPresent(builds::add);
            }
        }
        return builds;
    }

    private static OptionalInt buildNumberOf(final String tag, final String tagPrefix) {
        if (!tag.startsWith(tagPrefix)) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(tag.substring(tagPrefix.length())));
        } catch (final NumberFormatException notABuild) {
            return OptionalInt.empty();
        }
    }

    private static JsonArray fetchReleases() throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) URI.create(RELEASES_API).toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Kindling");
        try (final Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonArray.class);
        }
    }
}
