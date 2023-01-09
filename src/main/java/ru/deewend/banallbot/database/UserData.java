package ru.deewend.banallbot.database;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class UserData {
    private volatile int timesBanned;
    private volatile int wordsSent;

    public UserData() {
    }

    private UserData(int timesBanned, int wordsSent) {
        this.timesBanned = timesBanned;
        this.wordsSent = wordsSent;
    }

    public static UserData deserialize(DataInputStream stream) throws IOException {
        return new UserData(stream.readInt(), stream.readInt());
    }

    public int getTimesBanned() {
        return timesBanned;
    }

    public int getWordsSent() {
        return wordsSent;
    }

    @SuppressWarnings("NonAtomicOperationOnVolatileField") // it will be atomic
    void incrementTimesBanned() {
        timesBanned++;
    }

    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    void incrementWordsSent() {
        wordsSent++;
    }

    void serialize(DataOutputStream stream) throws IOException {
        stream.writeInt(timesBanned);
        stream.writeInt(wordsSent);
    }
}
