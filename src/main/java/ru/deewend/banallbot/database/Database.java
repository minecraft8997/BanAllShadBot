package ru.deewend.banallbot.database;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.internal.utils.tuple.Pair;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Database {
    private static final Database INSTANCE = new Database();
    private static final File DB_FILE = new File("db.dat");

    private Map<Long, UserData> dataMap;
    private Thread savingThread;
    private volatile boolean unsavedChanges;

    private Database() {
    }

    public static Database getInstance() {
        return INSTANCE;
    }

    // should be called on startup when there is only one (main) thread active
    public /* synchronized */ void load() throws IOException {
        if (dataMap != null) return;

        if (!DB_FILE.exists()) {
            //noinspection ResultOfMethodCallIgnored
            DB_FILE.createNewFile();
            dataMap = new HashMap<>();

            return;
        }

        try (DataInputStream stream =
                     new DataInputStream(new FileInputStream(DB_FILE))
        ) {
            int numberOfEntries = stream.readInt();
            dataMap = new HashMap<>(numberOfEntries);

            for (int i = 0; i < numberOfEntries; i++) {
                long discordID = stream.readLong();
                UserData userData = UserData.deserialize(stream);

                dataMap.put(discordID, userData);
            }
        }

        reportChanges(true);
        reportChanges(false);
        unsavedChanges = false;
    }

    // should be called on startup when there is only one (main) thread active
    public void startSavingThread() {
        if (savingThread != null) throw new IllegalStateException();

        savingThread = new Thread(() -> {
            while (true) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(3 * 3600 * 1000); // 3 hours
                } catch (InterruptedException e) {
                    //Thread.currentThread().interrupt(); // no need to set the flag again

                    break;
                }

                try {
                    save();
                } catch (IOException e) {
                    System.err.println("Failed to save the database:");
                    e.printStackTrace();
                }
            }
        }, "Saving Thread");

        savingThread.setUncaughtExceptionHandler((t, e) -> {
            System.err.println("Saving Thread went off due to an unhandled issue:");
            e.printStackTrace();
            System.err.println("The bot will be terminated!");

            System.exit(-1);
        });
        savingThread.start();
    }

    // should be called only in a JVM exit hook
    public void interruptSavingThread() {
        if (savingThread == null) throw new IllegalStateException();

        savingThread.interrupt();
    }

    /*
     * Placing additional "synchronized" keyword here (in spite of
     * the "retrieveUserData" method is already synchronized) to make
     * sure all the operations on volatile fields are atomic (including
     * those which come from UserData objects).
     */
    public synchronized void incrementTimesBanned(User user) {
        retrieveUserData(user.getIdLong()).incrementTimesBanned();
        reportChanges(true);
    }

    public synchronized void incrementWordsSent(User user) {
        retrieveUserData(user.getIdLong()).incrementWordsSent();
        reportChanges(false);
    }

    // should be executed inside synchronized (this)
    private void reportChanges(boolean banall) {
        unsavedChanges = true;

        List<Pair<Long, Integer>> resultList = new ArrayList<>(dataMap.size());
        for (Map.Entry<Long, UserData> entry : dataMap.entrySet()) {
            long discordID = entry.getKey();
            UserData associatedData = entry.getValue();
            int result = (banall ? associatedData
                    .getTimesBanned() : associatedData.getWordsSent());

            resultList.add(Pair.of(discordID, result));
        }

        Leaderboards.getInstance().updateLeaderboard(
                (banall ? "BanAll" : "One Word Story"), resultList);
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
