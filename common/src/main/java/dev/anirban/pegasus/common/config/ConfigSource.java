/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.common.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only view of a platform configuration file.
 *
 * <p>Paper implements this over its YAML configuration and Fabric over its JSON file, which keeps
 * {@link ConfigValidator} free of any server API and therefore unit-testable.
 */
public interface ConfigSource {
    Optional<String> getString(String path);

    Optional<Number> getNumber(String path);

    Optional<Boolean> getBoolean(String path);

    List<String> getStringList(String path);

    Map<String, String> getStringMap(String path);
}
