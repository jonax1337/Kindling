package net.kindlingmc.stress;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;

/**
 * The fake players of a stress test. Each tick it moves their number one step toward the target, so a hundred
 * players join over a few seconds instead of in one tick, and lets them play as their motion says. While they are
 * online the server takes as many more players, so real players still find a free slot; the limit goes back when they
 * leave.
 */
final class FakePlayerLoad {

    private static final int JOINS_PER_TICK = 2;

    private final ServerLevel level;
    private final List<FakePlayer> players = new ArrayList<>();
    private int slotsAdded;
    private int target;
    private PlayerMotion motion = PlayerMotion.STANDING;

    FakePlayerLoad(final ServerLevel level) {
        this.level = level;
    }

    void aimAt(final int count, final PlayerMotion motion) {
        this.target = count;
        this.motion = motion;
    }

    void tick() {
        this.players.removeIf(FakePlayer::hasLeft);
        for (int joined = 0; joined < JOINS_PER_TICK && this.players.size() < this.target; joined++) {
            this.players.add(FakePlayer.join(this.level, this.freeLine(), freeName()));
        }
        while (this.players.size() > this.target) {
            this.players.removeLast().leave();
        }
        for (final FakePlayer player : this.players) {
            this.keepInWorld(player);
            player.tick(this.motion);
        }
        this.keepSlotsForRealPlayers();
    }

    /** The first line of the layout no fake player flies, so two never share one after a player left. */
    private int freeLine() {
        int line = 0;
        while (this.isFlown(line)) {
            line++;
        }
        return line;
    }

    private boolean isFlown(final int line) {
        for (final FakePlayer player : this.players) {
            if (player.flies(line)) {
                return true;
            }
        }
        return false;
    }

    /** The first fake name nobody online has, so the names (and the entries the server caches) stay few. */
    private static String freeName() {
        int serial = 0;
        while (!FakePlayer.isFree(FakePlayer.nameOf(serial))) {
            serial++;
        }
        return FakePlayer.nameOf(serial);
    }

    /** A plugin that moves joining players to its spawn would put the load into a real world; such a player leaves. */
    private void keepInWorld(final FakePlayer player) {
        if (!player.hasLeft() && !player.isIn(this.level)) {
            player.leave();
        }
    }

    /** Raises the limit by the number of fake players; a change someone else makes to the limit meanwhile stays. */
    private void keepSlotsForRealPlayers() {
        final int change = this.players.size() - this.slotsAdded;
        if (change != 0) {
            Bukkit.setMaxPlayers(Bukkit.getMaxPlayers() + change);
            this.slotsAdded += change;
        }
    }

    void removeAll() {
        this.target = 0;
        try {
            for (final FakePlayer player : this.players) {
                player.leave();
            }
            this.players.clear();
        } finally {
            Bukkit.setMaxPlayers(Math.max(0, Bukkit.getMaxPlayers() - this.slotsAdded));
            this.slotsAdded = 0;
        }
    }

    int count() {
        return this.players.size();
    }

    int target() {
        return this.target;
    }

    PlayerMotion motion() {
        return this.motion;
    }
}
