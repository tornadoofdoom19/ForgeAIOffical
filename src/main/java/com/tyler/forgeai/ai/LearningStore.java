
package com.tyler.forgeai.ai;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * LearningStore: simple JSON persistence for learned data (hotbar layouts, heuristics, portals, observations).
 */
public class LearningStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-learning");
    private static final String DATA_PATH = "config/forgeai_learning.json";
    private final Gson gson = new Gson();
    private final Map<String, Object> store = new HashMap<>();

    public void init() {
        File f = new File(DATA_PATH);
        if (f.exists()) {
            try (FileReader reader = new FileReader(f)) {
                Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> loaded = gson.fromJson(reader, mapType);
                if (loaded != null) store.putAll(loaded);
                LOGGER.info("Loaded learning data entries: {}", store.size());
            } catch (Exception e) {
                LOGGER.warn("Failed to load learning data: {}", e.getMessage());
            }
        }
    }

    public synchronized void put(String key, Object value) {
        store.put(key, value);
        persist();
    }

    public synchronized Object get(String key) {
        return store.get(key);
    }

    public synchronized boolean contains(String key) { return store.containsKey(key); }

    private void persist() {
        try (FileWriter writer = new FileWriter(DATA_PATH)) {
            gson.toJson(store, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to persist learning store: {}", e.getMessage());
        }
    }
}
