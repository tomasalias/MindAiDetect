package ru.Fronzter.MindAc.util;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.ToJson;
import java.util.UUID;

public class MoshiFactory {
    private static Moshi instance;

    public static Moshi getInstance() {
        if (instance == null) {
            instance = new Moshi.Builder()
                    .add(new UUIDAdapter())
                    .build();
        }
        return instance;
    }

    public static class UUIDAdapter {
        @ToJson
        public String toJson(UUID uuid) {
            return uuid.toString();
        }

        @FromJson
        public UUID fromJson(String json) {
            return UUID.fromString(json);
        }
    }
}