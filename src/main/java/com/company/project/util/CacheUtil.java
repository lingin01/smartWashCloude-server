package com.company.project.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class CacheUtil {

    private final ConcurrentHashMap<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    public CacheUtil() {
        cleaner.scheduleAtFixedRate(this::cleanExpired, 60, 60, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        CacheEntry<?> entry = cache.get(key);
        if (entry == null) return null;
        if (entry.isExpired()) {
            cache.remove(key);
            return null;
        }
        return (T) entry.value;
    }

    public <T> void put(String key, T value, long ttlSeconds) {
        cache.put(key, CacheEntry.create(value, ttlSeconds));
    }

    public <T> void put(String key, T value) {
        cache.put(key, CacheEntry.create(value, 3600));
    }

    public void remove(String key) {
        cache.remove(key);
    }

    public boolean exists(String key) {
        CacheEntry<?> entry = cache.get(key);
        if (entry == null) return false;
        if (entry.isExpired()) {
            cache.remove(key);
            return false;
        }
        return true;
    }

    private void cleanExpired() {
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private record CacheEntry<T>(T value, long expireAt) {

        static <T> CacheEntry<T> create(T value, long ttlSeconds) {
            return new CacheEntry<>(value, System.currentTimeMillis() + ttlSeconds * 1000);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
