package dev.veyno.aiPof.config;

import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class GameConfig {
    private static final int DEFAULT_MIN_PLAYERS = 2;
    private static final int DEFAULT_START_COUNTDOWN_SECONDS = 3;
    private static final int DEFAULT_ITEM_INTERVAL_SECONDS = 10;
    private static final int DEFAULT_ROUND_RESTART_COOLDOWN_SECONDS = 5;
    private static final int DEFAULT_PILLAR_HEIGHT = 64;
    private static final int DEFAULT_PILLAR_SPACING = 6;
    private static final int DEFAULT_WAITING_BOX_RADIUS = 6;
    private static final int DEFAULT_WAITING_BOX_HEIGHT = 3;
    private static final int DEFAULT_WAITING_BOX_Y_OFFSET = 4;
    private static final int DEFAULT_WAITING_BOX_CENTER_X = 0;
    private static final int DEFAULT_WAITING_BOX_CENTER_Z = 0;
    private static final int DEFAULT_START_BOX_RADIUS = 1;
    private static final int DEFAULT_START_BOX_HEIGHT = 2;
    private static final int DEFAULT_START_BOX_Y_OFFSET = 2;
    private static final int DEFAULT_START_TELEPORT_DELAY_TICKS = 10;
    private static final boolean DEFAULT_ITEM_COUNT_ENABLED = true;
    private static final int DEFAULT_ITEM_COUNT_MAX = 3;
    private static final double DEFAULT_ITEM_COUNT_BASE_WEIGHT = 1.0;
    private static final double DEFAULT_PROJECTILE_KNOCKBACK_STRENGTH = 0.8;
    private static final List<String> DEFAULT_BLOCK_EXCLUSIONS = List.of();

    private final int minPlayers;
    private final int startCountdownSeconds;
    private final int itemIntervalSeconds;
    private final int roundRestartCooldownSeconds;
    private final int pillarHeight;
    private final int pillarSpacing;
    private final WaitingBox waitingBox;
    private final StartBox startBox;
    private final ItemCount itemCount;
    private final int startTeleportDelayTicks;
    private final double projectileKnockbackStrength;
    private final List<String> blockExclusions;

    private GameConfig(
        int minPlayers,
        int startCountdownSeconds,
        int itemIntervalSeconds,
        int roundRestartCooldownSeconds,
        int pillarHeight,
        int pillarSpacing,
        WaitingBox waitingBox,
        StartBox startBox,
        ItemCount itemCount,
        int startTeleportDelayTicks,
        double projectileKnockbackStrength,
        List<String> blockExclusions
    ) {
        this.minPlayers = minPlayers;
        this.startCountdownSeconds = startCountdownSeconds;
        this.itemIntervalSeconds = itemIntervalSeconds;
        this.roundRestartCooldownSeconds = roundRestartCooldownSeconds;
        this.pillarHeight = pillarHeight;
        this.pillarSpacing = pillarSpacing;
        this.waitingBox = waitingBox;
        this.startBox = startBox;
        this.itemCount = itemCount;
        this.startTeleportDelayTicks = startTeleportDelayTicks;
        this.projectileKnockbackStrength = projectileKnockbackStrength;
        this.blockExclusions = List.copyOf(blockExclusions);
    }

    public static GameConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        Logger logger = plugin.getLogger();

        int minPlayers = readPositiveInt(config, logger, "min-players", DEFAULT_MIN_PLAYERS, 1);
        int startCountdownSeconds = readPositiveInt(
            config,
            logger,
            "start-countdown-seconds",
            DEFAULT_START_COUNTDOWN_SECONDS,
            1
        );
        int itemIntervalSeconds = readPositiveInt(
            config,
            logger,
            "item-interval-seconds",
            DEFAULT_ITEM_INTERVAL_SECONDS,
            1
        );
        int roundRestartCooldownSeconds = readPositiveInt(
            config,
            logger,
            "round-restart-cooldown-seconds",
            DEFAULT_ROUND_RESTART_COOLDOWN_SECONDS,
            1
        );
        int pillarHeight = readPositiveInt(config, logger, "pillar-height", DEFAULT_PILLAR_HEIGHT, 1);
        int pillarSpacing = readPositiveInt(config, logger, "pillar-spacing", DEFAULT_PILLAR_SPACING, 1);

        WaitingBox waitingBox = loadWaitingBox(config, logger);
        StartBox startBox = loadStartBox(config, logger);
        ItemCount itemCount = loadItemCount(config, logger);
        int startTeleportDelayTicks = readNonNegativeInt(
            config,
            logger,
            "start-teleport-delay-ticks",
            DEFAULT_START_TELEPORT_DELAY_TICKS
        );
        double projectileKnockbackStrength = readPositiveDouble(
            config,
            logger,
            "projectile-knockback-strength",
            DEFAULT_PROJECTILE_KNOCKBACK_STRENGTH,
            0.0
        );
        List<String> blockExclusions = readStringList(config, logger, "block-exclusions", DEFAULT_BLOCK_EXCLUSIONS);

        return new GameConfig(
            minPlayers,
            startCountdownSeconds,
            itemIntervalSeconds,
            roundRestartCooldownSeconds,
            pillarHeight,
            pillarSpacing,
            waitingBox,
            startBox,
            itemCount,
            startTeleportDelayTicks,
            projectileKnockbackStrength,
            blockExclusions
        );
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getStartCountdownSeconds() {
        return startCountdownSeconds;
    }

    public int getItemIntervalSeconds() {
        return itemIntervalSeconds;
    }

    public int getRoundRestartCooldownSeconds() {
        return roundRestartCooldownSeconds;
    }

    public int getPillarHeight() {
        return pillarHeight;
    }

    public int getPillarSpacing() {
        return pillarSpacing;
    }

    public WaitingBox getWaitingBox() {
        return waitingBox;
    }

    public StartBox getStartBox() {
        return startBox;
    }

    public ItemCount getItemCount() {
        return itemCount;
    }

    public int getStartTeleportDelayTicks() {
        return startTeleportDelayTicks;
    }

    public double getProjectileKnockbackStrength() {
        return projectileKnockbackStrength;
    }

    public List<String> getBlockExclusions() {
        return blockExclusions;
    }

    private static WaitingBox loadWaitingBox(FileConfiguration config, Logger logger) {
        ConfigurationSection section = config.getConfigurationSection("waiting-box");
        if (section == null) {
            warnMissingSection(logger, "waiting-box");
            return new WaitingBox(
                DEFAULT_WAITING_BOX_RADIUS,
                DEFAULT_WAITING_BOX_HEIGHT,
                DEFAULT_WAITING_BOX_Y_OFFSET,
                DEFAULT_WAITING_BOX_CENTER_X,
                DEFAULT_WAITING_BOX_CENTER_Z
            );
        }
        int radius = readPositiveInt(section, logger, "waiting-box.radius", "radius", DEFAULT_WAITING_BOX_RADIUS, 1);
        int height = readPositiveInt(section, logger, "waiting-box.height", "height", DEFAULT_WAITING_BOX_HEIGHT, 1);
        int yOffset = readInt(section, logger, "waiting-box.y-offset", "y-offset", DEFAULT_WAITING_BOX_Y_OFFSET);
        int centerX = readInt(section, logger, "waiting-box.center-x", "center-x", DEFAULT_WAITING_BOX_CENTER_X);
        int centerZ = readInt(section, logger, "waiting-box.center-z", "center-z", DEFAULT_WAITING_BOX_CENTER_Z);
        return new WaitingBox(radius, height, yOffset, centerX, centerZ);
    }

    private static StartBox loadStartBox(FileConfiguration config, Logger logger) {
        ConfigurationSection section = config.getConfigurationSection("start-box");
        if (section == null) {
            warnMissingSection(logger, "start-box");
            return new StartBox(DEFAULT_START_BOX_RADIUS, DEFAULT_START_BOX_HEIGHT, DEFAULT_START_BOX_Y_OFFSET);
        }
        int radius = readPositiveInt(section, logger, "start-box.radius", "radius", DEFAULT_START_BOX_RADIUS, 1);
        int height = readPositiveInt(section, logger, "start-box.height", "height", DEFAULT_START_BOX_HEIGHT, 1);
        int yOffset = readInt(section, logger, "start-box.y-offset", "y-offset", DEFAULT_START_BOX_Y_OFFSET);
        return new StartBox(radius, height, yOffset);
    }

    private static ItemCount loadItemCount(FileConfiguration config, Logger logger) {
        ConfigurationSection section = config.getConfigurationSection("item-count");
        if (section == null) {
            warnMissingSection(logger, "item-count");
            return new ItemCount(DEFAULT_ITEM_COUNT_ENABLED, DEFAULT_ITEM_COUNT_MAX, DEFAULT_ITEM_COUNT_BASE_WEIGHT);
        }
        boolean enabled = section.getBoolean("enabled", DEFAULT_ITEM_COUNT_ENABLED);
        int max = readPositiveInt(section, logger, "item-count.max", "max", DEFAULT_ITEM_COUNT_MAX, 1);
        double baseWeight = readPositiveDouble(
            section,
            logger,
            "item-count.base-weight",
            "base-weight",
            DEFAULT_ITEM_COUNT_BASE_WEIGHT,
            0.0
        );
        return new ItemCount(enabled, max, baseWeight);
    }

    private static int readPositiveInt(FileConfiguration config, Logger logger, String path, int defaultValue, int min) {
        if (!config.contains(path)) {
            warnMissing(logger, path, defaultValue);
            return defaultValue;
        }
        int value = config.getInt(path, defaultValue);
        if (value < min) {
            warnInvalid(logger, path, value, defaultValue);
            return defaultValue;
        }
        return value;
    }

    private static int readPositiveInt(
        ConfigurationSection section,
        Logger logger,
        String fullPath,
        String key,
        int defaultValue,
        int min
    ) {
        if (!section.contains(key)) {
            warnMissing(logger, fullPath, defaultValue);
            return defaultValue;
        }
        int value = section.getInt(key, defaultValue);
        if (value < min) {
            warnInvalid(logger, fullPath, value, defaultValue);
            return defaultValue;
        }
        return value;
    }

    private static int readInt(ConfigurationSection section, Logger logger, String fullPath, String key, int defaultValue) {
        if (!section.contains(key)) {
            warnMissing(logger, fullPath, defaultValue);
            return defaultValue;
        }
        return section.getInt(key, defaultValue);
    }

    private static int readNonNegativeInt(FileConfiguration config, Logger logger, String path, int defaultValue) {
        if (!config.contains(path)) {
            warnMissing(logger, path, defaultValue);
            return defaultValue;
        }
        int value = config.getInt(path, defaultValue);
        if (value < 0) {
            warnInvalid(logger, path, value, defaultValue);
            return defaultValue;
        }
        return value;
    }

    private static double readPositiveDouble(
        FileConfiguration config,
        Logger logger,
        String path,
        double defaultValue,
        double min
    ) {
        if (!config.contains(path)) {
            warnMissing(logger, path, defaultValue);
            return defaultValue;
        }
        double value = config.getDouble(path, defaultValue);
        if (value <= min) {
            warnInvalid(logger, path, value, defaultValue);
            return defaultValue;
        }
        return value;
    }

    private static double readPositiveDouble(
        ConfigurationSection section,
        Logger logger,
        String fullPath,
        String key,
        double defaultValue,
        double min
    ) {
        if (!section.contains(key)) {
            warnMissing(logger, fullPath, defaultValue);
            return defaultValue;
        }
        double value = section.getDouble(key, defaultValue);
        if (value <= min) {
            warnInvalid(logger, fullPath, value, defaultValue);
            return defaultValue;
        }
        return value;
    }

    private static List<String> readStringList(
        FileConfiguration config,
        Logger logger,
        String path,
        List<String> defaultValue
    ) {
        if (!config.contains(path)) {
            warnMissing(logger, path, defaultValue);
            return defaultValue;
        }
        List<String> value = config.getStringList(path);
        return value == null ? defaultValue : value;
    }

    private static void warnMissingSection(Logger logger, String path) {
        logger.warning("Missing config section '" + path + "', using defaults.");
    }

    private static void warnMissing(Logger logger, String path, Object defaultValue) {
        logger.warning("Missing config value '" + path + "', using default: " + defaultValue + ".");
    }

    private static void warnInvalid(Logger logger, String path, Object value, Object defaultValue) {
        logger.warning("Invalid config value '" + path + "'=" + value + ", using default: " + defaultValue + ".");
    }

    public record WaitingBox(int radius, int height, int yOffset, int centerX, int centerZ) {
    }

    public record StartBox(int radius, int height, int yOffset) {
    }

    public record ItemCount(boolean enabled, int max, double baseWeight) {
    }
}
