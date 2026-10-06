package travelplanner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Very simple on-disk cache: one file per query inside the "cache/" folder.
 *
 * Why? It protects against API rate limits, makes repeat searches instant and lets the
 * demo work offline for any destination that was searched before.
 */
public class CacheManager {

    private static final File CACHE_DIR = new File("cache");

    /** Returns the cached text for this key, or null if nothing is cached. */
    public static String get(String key) {
        File file = fileFor(key);
        if (!file.exists()) return null;
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null; // unreadable cache file = just treat as a cache miss
        }
    }

    public static void put(String key, String value) {
        try {
            if (!CACHE_DIR.exists()) CACHE_DIR.mkdirs();
            Files.writeString(fileFor(key).toPath(), value, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // caching is optional - failing to write must never break the app
            System.out.println("Cache write skipped: " + e.getMessage());
        }
    }

    /** Deletes all cached files and returns how many were removed. */
    public static int clear() {
        int count = 0;
        File[] files = CACHE_DIR.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isFile() && f.delete()) count++;
        }
        return count;
    }

    /** Turns a query key into a safe, readable file name. */
    private static File fileFor(String key) {
        String safe = key.toLowerCase().replaceAll("[^a-z0-9]+", "_");
        if (safe.length() > 60) safe = safe.substring(0, 60);
        return new File(CACHE_DIR, safe + "_" + Integer.toHexString(key.hashCode()) + ".json");
    }
}
