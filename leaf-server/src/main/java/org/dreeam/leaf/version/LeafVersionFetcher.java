package org.dreeam.leaf.version;

import org.galemc.gale.version.AbstractPaperVersionFetcher;

// Kindling - fork name: compare the running build with the Kindling repository instead of Leaf's download API
public class LeafVersionFetcher extends AbstractPaperVersionFetcher {

    public static final String DOWNLOAD_PAGE = "https://github.com/jonax1337/KindlingMC/releases";

    public LeafVersionFetcher() {
        super(
            DOWNLOAD_PAGE,
            "KindlingMC",
            "Kindling",
            "jonax1337",
            "KindlingMC",
            ApiType.GITHUB
        );
    }
}
