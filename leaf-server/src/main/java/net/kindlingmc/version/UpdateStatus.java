package net.kindlingmc.version;

import java.util.Collections;
import java.util.List;

/** Where the running build stands against the published Kindling builds. */
sealed interface UpdateStatus {

    record Latest() implements UpdateStatus {}

    record Behind(int latestBuild, int buildsBehind) implements UpdateStatus {}

    record DevelopmentBuild() implements UpdateStatus {}

    record Unavailable() implements UpdateStatus {}

    static UpdateStatus compare(final int runningBuild, final List<Integer> publishedBuilds) {
        final int newerBuilds = (int) publishedBuilds.stream().filter(build -> build > runningBuild).count();
        if (newerBuilds == 0) {
            return new Latest();
        }
        return new Behind(Collections.max(publishedBuilds), newerBuilds);
    }
}
