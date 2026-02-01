package dev.veyno.aiPof.service;

import dev.veyno.aiPof.config.ConfigService;
import dev.veyno.aiPof.domain.Round;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

public class ItemService {
    private final ConfigService config;

    public ItemService(ConfigService config) {
        this.config = config;
    }

    public BukkitTask scheduleItemDrops(JavaPlugin plugin, Round round, Runnable afterTick) {
        int intervalSeconds = config.getItemIntervalSeconds();
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : new HashSet<>(round.getAlivePlayersMutable())) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    round.removeAlive(uuid);
                    continue;
                }
                Material material = round.getItemPool().get(round.getRandom().nextInt(round.getItemPool().size()));
                int count = rollItemCount(round.getRandom());
                ItemStack stack = new ItemStack(material, count);
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                leftover.values().forEach(item -> round.getWorld().dropItemNaturally(player.getLocation(), item));
            }
            afterTick.run();
        }, 20L * intervalSeconds, 20L * intervalSeconds);
    }

    private int rollItemCount(Random random) {
        if (!config.isItemCountEnabled()) {
            return 1;
        }
        int max = Math.max(1, config.getItemCountMax());
        double baseWeight = config.getItemCountBaseWeight();
        double totalWeight = 0.0;
        for (int count = 1; count <= max; count++) {
            totalWeight += baseWeight / (count * count);
        }
        double roll = random.nextDouble() * totalWeight;
        double running = 0.0;
        for (int count = 1; count <= max; count++) {
            running += baseWeight / (count * count);
            if (roll <= running) {
                return count;
            }
        }
        return 1;
    }
}
