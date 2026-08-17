/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.common.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Flat-path {@link ConfigSource} backed by a plain map. Used by the Fabric JSON adapter and by
 * the unit tests, so validation behaviour is verified against the same code paths.
 */
public final class MapConfigSource implements ConfigSource {
    private final Map<String, Object> values;

    public MapConfigSource(Map<String, Object> values) {
        this.values = values == null ? Map.of() : new LinkedHashMap<>(values);
    }

    @Override
    public Optional<String> getString(String path) {
        Object value = values.get(path);
        if (value == null) {
            return Optional.empty();
        }
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    @Override
    public Optional<Number> getNumber(String path) {
        Object value = values.get(path);
        if (value instanceof Number number) {
            return Optional.of(number);
        }
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.valueOf(String.valueOf(value).strip()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Boolean> getBoolean(String path) {
        Object value = values.get(path);
        if (value instanceof Boolean bool) {
            return Optional.of(bool);
        }
        if (value == null) {
            return Optional.empty();
        }
        String text = String.valueOf(value).strip();
        if (text.equalsIgnoreCase("true")) {
            return Optional.of(Boolean.TRUE);
        }
        if (text.equalsIgnoreCase("false")) {
            return Optional.of(Boolean.FALSE);
        }
        return Optional.empty();
    }

    @Override
    public List<String> getStringList(String path) {
        Object value = values.get(path);
        if (value instanceof List<?> list) {
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
        return List.of();
    }

    @Override
    public Map<String, String> getStringMap(String path) {
        Object value = values.get(path);
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> {
            if (key != null && mapValue != null) {
                result.put(String.valueOf(key), String.valueOf(mapValue));
            }
        });
        return Map.copyOf(result);
    }
}
