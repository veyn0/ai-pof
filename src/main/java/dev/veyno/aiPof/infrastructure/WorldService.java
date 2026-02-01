package dev.veyno.aiPof.infrastructure;

import dev.veyno.aiPof.domain.Round;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

public class WorldService {
    private final Logger logger = Bukkit.getLogger();

    public World createWorld(String worldName) {
        logger.info(() -> "world-create start name=" + worldName);
        WorldCreator creator = new WorldCreator(worldName)
            .generator(new ChunkGenerator() {
                @Override
                public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
                    return createChunkData(world);
                }
            })
            .generateStructures(false);
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            logger.severe(() -> "world-create failed name=" + worldName);
            throw new IllegalStateException("Konnte Welt nicht erstellen.");
        }
        world.setDifficulty(Difficulty.EASY);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setTime(6000);
        logger.info(() -> "world-create ready name=" + worldName);
        return world;
    }

    public void cleanupWorld(World world) {
        if (world == null) {
            return;
        }
        String name = world.getName();
        logger.info(() -> "world-cleanup start name=" + name);
        Bukkit.unloadWorld(world, false);
        File folder = new File(Bukkit.getWorldContainer(), name);
        deleteWorldFolder(folder);
    }

    public int cleanupUnusedWorldFolders(Set<World> activeWorlds) {
        File container = Bukkit.getWorldContainer();
        File[] folders = container.listFiles(File::isDirectory);
        if (folders == null) {
            return 0;
        }
        Set<String> activeWorldNames = activeWorlds.stream()
            .filter(Objects::nonNull)
            .map(World::getName)
            .collect(Collectors.toSet());
        Set<String> loadedWorlds = Bukkit.getWorlds().stream()
            .map(World::getName)
            .collect(Collectors.toSet());
        int deleted = 0;
        for (File folder : folders) {
            String name = folder.getName();
            if (!name.startsWith(Round.WORLD_PREFIX)) {
                continue;
            }
            if (activeWorldNames.contains(name) || loadedWorlds.contains(name)) {
                continue;
            }
            deleteWorldFolder(folder);
            deleted++;
        }
        return deleted;
    }

    private void deleteWorldFolder(File folder) {
        if (folder == null || !folder.exists()) {
            return;
        }
        try {
            Files.walk(folder.toPath())
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        logger.log(Level.WARNING, "world-cleanup delete-failed path=" + path, ignored);
                    }
                });
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "world-cleanup scan-failed path=" + folder.getAbsolutePath(), ex);
        }
    }
}
