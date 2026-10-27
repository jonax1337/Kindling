package net.kindlingmc.version;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubReleasesTest {

    @Test
    void readsOnlyTheBuildsOfTheRunningMinecraftVersion() {
        final JsonArray releases = new Gson().fromJson("""
            [
              {"tag_name": "26.3-3"},
              {"tag_name": "26.4-1"},
              {"tag_name": "26.3.1-2"},
              {"tag_name": "26.3-rc"},
              {"name": "a release without a tag"},
              {"tag_name": "26.3-1"}
            ]
            """, JsonArray.class);

        assertEquals(List.of(3, 1), GitHubReleases.buildNumbersIn(releases, "26.3"));
    }

    @Test
    void anEmptyListHasNoBuilds() {
        assertEquals(List.of(), GitHubReleases.buildNumbersIn(new JsonArray(), "26.3"));
    }

    @Test
    void releasesOfOtherMinecraftVersionsGiveNoBuilds() {
        final JsonArray releases = new Gson().fromJson("[{\"tag_name\": \"26.4-1\"}, {\"tag_name\": \"26.3.1-2\"}]", JsonArray.class);

        assertEquals(List.of(), GitHubReleases.buildNumbersIn(releases, "26.3"));
    }

    @Test
    void aTagWithATextOrNegativeOrHugeBuildIsNotABuild() {
        final JsonArray releases = new Gson().fromJson("""
            [{"tag_name": "26.3-"}, {"tag_name": "26.3-x"}, {"tag_name": "26.3-99999999999"}, {"tag_name": "26.3-4"}]
            """, JsonArray.class);

        assertEquals(List.of(4), GitHubReleases.buildNumbersIn(releases, "26.3"));
    }

    @Test
    void aTagThatIsNotAStringIsIgnored() {
        final JsonArray releases = new Gson().fromJson("[{\"tag_name\": null}, {\"tag_name\": [1]}, {\"tag_name\": \"26.3-2\"}, 7, \"x\"]", JsonArray.class);

        assertEquals(List.of(2), GitHubReleases.buildNumbersIn(releases, "26.3"));
    }

    @Test
    void anEmptyResponseIsAnIoErrorAndNotANullPointer() {
        final IOException failure = assertThrows(IOException.class, () -> GitHubReleases.parseReleases(new StringReader("")));

        assertTrue(failure.getMessage().contains("empty"), failure.getMessage());
    }

    @Test
    void aJsonNullBodyIsAParseErrorTheFetcherCatches() {
        assertThrows(JsonParseException.class, () -> GitHubReleases.parseReleases(new StringReader("null")));
    }

    @Test
    void anObjectInsteadOfAListIsAParseError() {
        assertThrows(JsonParseException.class, () -> GitHubReleases.parseReleases(new StringReader("{\"message\": \"rate limit\"}")));
    }

    @Test
    void aListIsParsed() throws IOException {
        assertEquals(1, GitHubReleases.parseReleases(new StringReader("[{\"tag_name\": \"26.3-1\"}]")).size());
    }
}
