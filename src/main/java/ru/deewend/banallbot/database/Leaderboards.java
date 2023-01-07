package ru.deewend.banallbot.database;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import ru.deewend.banallbot.Helper;
import ru.deewend.banallbot.Main;

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
            String leaderboardName, int offset, int maxResults, User requester
    ) {
        LeaderboardContainer container;
        synchronized (this) {
            if (!leaderboards.containsKey(leaderboardName)) {
                return new Object[] {
                        "Error: such leaderboard does not exist.", System.currentTimeMillis(), 0
                };
            }

            container = leaderboards.get(leaderboardName);
        }

        long requesterID = requester.getIdLong();
        boolean foundUserRequested = false;
        StringBuilder sb = new StringBuilder();
        synchronized (container) {
            List<Pair<Long, Integer>> leaderboard = container.leaderboard;

            if (System.currentTimeMillis() - container.lastQueried < 180000) {
                return new Object[] { "Error: this leaderboard is being requested too often! " +
                        "Please allow up to 3 minutes.", container.lastModified, leaderboard.size()
                };
            }

            int boundI = Math.min(leaderboard.size(), offset + maxResults);
            if (boundI - offset <= 0) {
                return new Object[] { "Error: offset is too high (or it's guaranteed " +
                        "that there won't be any results shown).",
                        container.lastModified, leaderboard.size()
                };
            }

            Object[] results = new Object[boundI - offset];

            for (int i = offset; i < boundI; i++) {
                int thisI = i;
                Pair<Long, Integer> entry = leaderboard.get(i);
                long discordID = entry.getLeft();
                int result = entry.getRight();

                if (discordID == requesterID) {
                    results[thisI] = Pair.of(requester.getAsTag(), result);
                } else {
                    Main.getJDA().retrieveUserById(discordID).queue(
                            user -> results[thisI] = Pair.of(user.getAsTag(), result),
                            throwable -> results[thisI] = throwable
                    );
                }

                if (!foundUserRequested) {
                    if (discordID == requesterID) foundUserRequested = true;
                }
            }

            try {
                Helper.waitUntilAllElementsBecomeNonNull(results);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return new Object[] { "Error: received a Thread interruption " +
                        "request while waiting for responses from Discord.",
                        container.lastModified, leaderboard.size()
                };
            }

            for (int i = 0; i < results.length; i++) {
                String discordTag;
                int result;
                Object current = results[i];
                if (current instanceof Throwable) {
                    discordTag = "<failed to retrieve their Discord tag>";
                    result = -1;
                } else {
                    //noinspection unchecked
                    Pair<String, Integer> entry = (Pair<String, Integer>) current;
                    discordTag = entry.getLeft();
                    result = entry.getRight();
                }
                int place = offset + i + 1;

                sb.append(renderEntry(place, discordTag, (result != -1), result))
                        .append('\n');
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
                    String discordTag = requester.getAsTag();

                    String fullEntry = "**... " + renderEntry(positionInRating + 1,
                            discordTag, true, userResult) + "**";
                    if (drawOnTop) {
                        sb.insert(0, fullEntry + "\n\n");
                    } else {
                        sb.append("\n").append(fullEntry);
                    }
                }
            }

            return new Object[] { sb.toString(), container.lastModified, leaderboard.size() };
        }
    }

    private static String renderEntry(
            int place, String discordTag, boolean shouldSanitize, int result
    ) {
        boolean wasSanitized = false;
        if (shouldSanitize) {
            String sanitized = Helper.sanitizeNickname(discordTag);
            if (!sanitized.equals(discordTag)) {
                discordTag = sanitized;
                wasSanitized = true;
            }
        }

        return place +
                Helper.getPlacePostfix(place) +
                discordTag +
                (wasSanitized ? "_[sanitized]_" : "") +
                " (" + result + ")";
    }
}
