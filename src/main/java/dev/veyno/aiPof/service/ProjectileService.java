package dev.veyno.aiPof.service;

import dev.veyno.aiPof.config.ConfigService;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public class ProjectileService implements Listener {
    private final double knockbackStrength;

    public ProjectileService(ConfigService configService) {
        this.knockbackStrength = configService.getProjectileKnockbackStrength();
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Entity damager = event.getDamager();
        if (!(damager instanceof Projectile projectile)) {
            return;
        }
        if (!(projectile instanceof Egg || projectile instanceof Snowball || projectile instanceof FishHook)) {
            return;
        }
        Vector knockback = resolveKnockbackVector(projectile, target);
        if (knockback == null) {
            return;
        }
        target.setVelocity(target.getVelocity().add(knockback));
    }

    private Vector resolveKnockbackVector(Projectile projectile, LivingEntity target) {
        Vector direction = null;
        ProjectileSource source = projectile.getShooter();
        if (source instanceof Entity sourceEntity) {
            direction = target.getLocation().toVector().subtract(sourceEntity.getLocation().toVector());
        }
        if (direction == null || direction.lengthSquared() == 0.0) {
            direction = projectile.getVelocity();
        }
        if (direction.lengthSquared() == 0.0) {
            return null;
        }
        return direction.normalize().multiply(knockbackStrength);
    }
}
