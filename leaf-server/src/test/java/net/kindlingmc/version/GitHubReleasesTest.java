package net.kindlingmc.version;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
