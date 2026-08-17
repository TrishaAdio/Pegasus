/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.paper;

import dev.anirban.pegasus.common.config.ConfigSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Adapts Bukkit's YAML configuration to the shared, server-API-free {@link ConfigSource}. */
public final class YamlConfigSource implements ConfigSource {
    private final FileConfiguration configuration;

    public YamlConfigSource(FileConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Optional<String> getString(String path) {
        Object value = configuration.get(path);
        if (value == null || value instanceof List<?> || value instanceof ConfigurationSection) {
            return Optional.empty();
        }
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    @Override
    public Optional<Number> getNumber(String path) {
        Object value = configuration.get(path);
        if (value instanceof Number number) {
            return Optional.of(number);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> getBoolean(String path) {
        Object value = configuration.get(path);
        if (value instanceof Boolean bool) {
            return Optional.of(bool);
        }
        return Optional.empty();
    }

    @Override
    public List<String> getStringList(String path) {
        Object value = configuration.get(path);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element != null) {
                String text = String.valueOf(element).strip();
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public Map<String, String> getStringMap(String path) {
        ConfigurationSection section = configuration.getConfigurationSection(path);
        if (section == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value != null && !(value instanceof ConfigurationSection)) {
                result.put(key, String.valueOf(value));
            }
        }
        return Map.copyOf(result);
    }
}
