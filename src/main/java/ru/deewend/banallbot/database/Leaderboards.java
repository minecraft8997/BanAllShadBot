package ru.deewend.banallbot.database;

import net.dv8tion.jda.internal.utils.tuple.Pair;
import ru.deewend.banallbot.Helper;

import java.util.*;

@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
public class Leaderboards {
    private static class LeaderboardContainer {
        public volatile List<Pair<Long, Integer>> leaderboard;
        public volatile long lastModified;
        public volatile long lastQueried;
    }

    private static final Leaderboards INSTANCE = new Leaderboards();

    private final Map<String, LeaderboardContainer> leaderboards = new HashMap<>();

    private Leaderboards() {
    }

    public static Leaderboards getInstance() {
        return INSTANCE;
    }

    void updateLeaderboard(String name, List<Pair<Long, Integer>> leaderboard) {
        leaderboard.sort(Comparator.comparingInt(
                (Pair<Long, Integer> pair) -> pair.getRight()).reversed());

        LeaderboardContainer entry;
        synchronized (this) {
            entry = leaderboards.computeIfAbsent(name, key -> new LeaderboardContainer());
        }
        synchronized (entry) {
            entry.leaderboard = Collections.unmodifiableList(leaderboard);
            entry.lastModified = System.currentTimeMillis();
        }
    }

    /*
    long getLastModified(String leaderboardName) {
        LeaderboardContainer entry;
        synchronized (this) {
            if (!leaderboards.containsKey(leaderboardName)) return 0L;

            entry = leaderboards.get(leaderboardName);
        }
        synchronized (entry) {
            return entry.lastModified;
        }
    }
     */
    
    public Object[] renderResults(
            String leaderboardName, int offset, int maxResults, long requesterID
    ) {
        LeaderboardContainer container;
        synchronized (this) {
            if (!leaderboards.containsKey(leaderboardName)) {
                return new Object[] { "Error: such leaderboard does not exist.",
                        System.currentTimeMillis(), 0
                };
            }

            container = leaderboards.get(leaderboardName);
        }

        boolean foundUserRequested = false;
        StringBuilder sb = new StringBuilder();
        synchronized (container) {
            List<Pair<Long, Integer>> leaderboard = container.leaderboard;

            if (System.currentTimeMillis() - container.lastQueried < 180000) {
                return new Object[] { "Error: this leaderboard is being requested too " +
                        "often! Please allow up to 3 minutes.", container.lastModified,
                        leaderboard.size()
                };
            }

            int boundI = Math.min(leaderboard.size(), offset + maxResults);
            if (boundI - offset <= 0) {
                if (leaderboard.isEmpty()) {
                    return new Object[] { "This leaderboard is... quite a bit empty.",
                            container.lastModified, 0
                    };
                }

                return new Object[] { "Error: offset is too high (or it's guaranteed " +
                        "that there won't be any results shown).",
                        container.lastModified, leaderboard.size()
                };
            }

            for (int i = offset; i < boundI; i++) {
                Pair<Long, Integer> entry = leaderboard.get(i);
                long discordID = entry.getLeft();
                int result = entry.getRight();

                sb.append(renderEntry((i + 1), discordID, result)).append('\n');

                if (!foundUserRequested) {
                    if (discordID == requesterID) foundUserRequested = true;
                }
            }

            if (!foundUserRequested) {
                int positionInRating = -1;
                int userResult = 0;
                for (int i = 0; i < leaderboard.size(); i++) {
                    Pair<Long, Integer> entry = leaderboard.get(i);
                    long discordID = entry.getLeft();
                    int result = entry.getRight();

                    if (requesterID == discordID) {
                        positionInRating = i;
                        userResult = result;

                        break;
                    }
                }

                if (positionInRating != -1) {
                    boolean drawOnTop = (positionInRating < offset);

                    String fullEntry = "**... " +
                            renderEntry(
                                    (positionInRating + 1),
                                    requesterID,
                                    userResult
                            ) + "**";

                    if (drawOnTop) {
                        sb.insert(0, fullEntry + "\n\n");
                    } else {
                        sb.append("\n").append(fullEntry);
                    }
                }
            }

            Object[] banStats = Database.getInstance().getBanStats();
            sb.append("\n**Total #ban-all channel ban time:** ")
                    .append((String) banStats[0])
                    .append(" (").append(banStats[1]).append(" times)");

            return new Object[] { sb.toString(), container.lastModified, leaderboard.size() };
        }
    }

    private static String renderEntry(
            int place, long discordID, int result
    ) {
        return place + Helper.getPlacePostfix(place) + "<@" + discordID + "> (" + result + ")";
    }
}
