package dev.veyno.aiPof.config;

import dev.veyno.aiPof.domain.BlockExclusions;

public class ConfigService {
    private final GameConfig config;
    private final BlockExclusions blockExclusions;

    public ConfigService(GameConfig config) {
        this.config = config;
        this.blockExclusions = new BlockExclusions(config.getBlockExclusions());
    }

    public int getMinPlayers() {
        return config.getMinPlayers();
    }

    public int getStartCountdownSeconds() {
        return config.getStartCountdownSeconds();
    }

    public int getPillarHeight() {
        return config.getPillarHeight();
    }

    public int getPillarSpacing() {
        return config.getPillarSpacing();
    }

    public int getItemIntervalSeconds() {
        return config.getItemIntervalSeconds();
    }

    public int getRoundRestartCooldownSeconds() {
        return config.getRoundRestartCooldownSeconds();
    }

    public boolean isItemCountEnabled() {
        return config.getItemCount().enabled();
    }

    public int getItemCountMax() {
        return config.getItemCount().max();
    }

    public double getItemCountBaseWeight() {
        return config.getItemCount().baseWeight();
    }

    public double getProjectileKnockbackStrength() {
        return config.getProjectileKnockbackStrength();
    }

    public int getWaitingBoxRadius() {
        return config.getWaitingBox().radius();
    }

    public int getWaitingBoxHeight() {
        return config.getWaitingBox().height();
    }

    public int getWaitingBoxYOffset() {
        return config.getWaitingBox().yOffset();
    }

    public int getWaitingBoxCenterX() {
        return config.getWaitingBox().centerX();
    }

    public int getWaitingBoxCenterZ() {
        return config.getWaitingBox().centerZ();
    }

    public int getStartBoxRadius() {
        return config.getStartBox().radius();
    }

    public int getStartBoxHeight() {
        return config.getStartBox().height();
    }

    public int getStartBoxYOffset() {
        return config.getStartBox().yOffset();
    }

    public BlockExclusions getBlockExclusions() {
        return blockExclusions;
    }
}
