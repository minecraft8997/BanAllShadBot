package ru.deewend.banallbot.database;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import ru.deewend.banallbot.Helper;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Database {
    private static final Database INSTANCE = new Database();
    private static final File DB_FILE = new File("db.dat");

    private Map<Long, UserData> dataMap;
    private Thread helperThread;
    private volatile boolean unsavedChanges;

    private Database() {
    }

    public static Database getInstance() {
        return INSTANCE;
    }

    // should be called on startup when there is only one (main) thread active
    public /* synchronized */ void load() throws IOException {
        if (dataMap != null) return;

        if (DB_FILE.createNewFile()) {
            try (OutputStream stream = new FileOutputStream(DB_FILE)){
                stream.write(new byte[] { 0, 0, 0, 0 }); // = 0 (32-bit integer)
            }
            dataMap = new HashMap<>();
        } else {
            try (DataInputStream stream =
                         new DataInputStream(new FileInputStream(DB_FILE))
            ) {
                int numberOfEntries = stream.readInt();
                dataMap = new HashMap<>(numberOfEntries + 32);

                for (int i = 0; i < numberOfEntries; i++) {
                    long discordID = stream.readLong();
                    UserData userData = UserData.deserialize(stream);

                    int timesBanned = userData.getTimesBanned();
                    int wordsSent = userData.getWordsSent();

                    if (timesBanned < 0 || wordsSent < 0) {
                        reportSkippedLoading(discordID, "found a negative value " +
                                "in their stats. Definitely corrupted");

                        continue;
                    }
                    if (timesBanned == 0 && wordsSent == 0) {
                        reportSkippedLoading(discordID, "both of their stats " +
                                "values are equal to zero. Might be a bug");

                        continue;
                    }

                    dataMap.put(discordID, userData);
                }
            }
        }

        refreshLeaderboards(true);
    }

    private static void reportSkippedLoading(long discordID, String reason) {
        System.err.println("Skipped loading " + discordID + "'s entry: " + reason);
    }

    // should be called on startup when there is only one (main) thread active
    public void startHelperThread() {
        if (helperThread != null) throw new IllegalStateException();

        helperThread = new Thread(() -> {
            global: while (true) {
                for (int i = 0; i < 6; i++) {
                    if (!Helper.sleep(10 * 1000)) break global; // 10 minutes

                    refreshLeaderboards(false);
                }

                try {
                    save(); // is called once an hour
                } catch (IOException e) {
                    System.err.println("Failed to save the database:");
                    e.printStackTrace();
                }
            }
        }, "Helper Thread (saving data & refreshing leaderboards)");

        helperThread.setUncaughtExceptionHandler((t, e) -> {
            System.err.println("Saving Thread went off due to an unhandled issue:");
            e.printStackTrace();
            System.err.println("The bot will be terminated!");

            System.exit(-1);
        });
        helperThread.start();
    }

    // should be called only in a JVM exit hook
    public void interruptHelperThread() {
        if (helperThread == null) throw new IllegalStateException();

        helperThread.interrupt();
    }

    /*
     * Placing additional "synchronized" keyword here (in spite of
     * the "retrieveUserData" method is already synchronized) to make
     * sure all the operations on volatile fields are atomic (including
     * those which come from UserData objects).
     */
    public synchronized void incrementTimesBanned(User user) {
        retrieveUserData(user.getIdLong()).incrementTimesBanned();
        unsavedChanges = true;
    }

    public synchronized void incrementWordsSent(User user) {
        retrieveUserData(user.getIdLong()).incrementWordsSent();
        unsavedChanges = true;
    }

    private void refreshLeaderboards(boolean ignoreUnsavedChangesIsFalse) {
        List<Pair<Long, Integer>> banAllResultList = new ArrayList<>();
        List<Pair<Long, Integer>> storyResultList = new ArrayList<>();
        synchronized (this) {
            if (!unsavedChanges && !ignoreUnsavedChangesIsFalse) return;

            for (Map.Entry<Long, UserData> entry : dataMap.entrySet()) {
                long discordID = entry.getKey();
                UserData associatedData = entry.getValue();

                int timesBanned = associatedData.getTimesBanned();
                int wordsSent = associatedData.getWordsSent();

                if (timesBanned > 0) {
                    banAllResultList.add(Pair.of(discordID, timesBanned));
                }
                if (wordsSent > 0) {
                    storyResultList.add(Pair.of(discordID, wordsSent));
                }
            }
        }

        Leaderboards.getInstance().updateLeaderboard("BanAll", banAllResultList);
        Leaderboards.getInstance().updateLeaderboard("One Word Story", storyResultList);
    }

    public synchronized void save() throws IOException {
        if (!unsavedChanges) return;

        if (!DB_FILE.exists()) {
            //noinspection ResultOfMethodCallIgnored
            DB_FILE.createNewFile();
        }

        try (DataOutputStream stream =
                     new DataOutputStream(new FileOutputStream(DB_FILE))
        ) {
            stream.writeInt(dataMap.size());
            for (Map.Entry<Long, UserData> entry : dataMap.entrySet()) {
                stream.writeLong(entry.getKey());
                entry.getValue().serialize(stream);
            }
        }

        unsavedChanges = false;
    }

    // should be executed inside synchronized (this)
    private UserData retrieveUserData(long discordID) {
        UserData userData;
        /*
        if (!dataMap.containsKey(discordID)) {
            dataMap.put(discordID, (userData = new UserData()));
        } else {
            userData = dataMap.get(discordID);
        }
         */
        userData = dataMap.computeIfAbsent(discordID, key -> new UserData());

        return userData;
    }
}
