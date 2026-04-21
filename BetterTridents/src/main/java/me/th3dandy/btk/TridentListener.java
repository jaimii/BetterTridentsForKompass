package me.th3dandy.btk;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

import java.util.Collection;
import java.util.Map;
import java.util.Random;

public class TridentListener implements Listener {

    private final NamespacedKey rangeKey = new NamespacedKey("better_tridents", "reach_distance");
    private final NamespacedKey blockRangeKey = new NamespacedKey("better_tridents", "block_reach");

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // (Keep your existing damage logic here)
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (damager instanceof Player player) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.TRIDENT) {
                double damage = 10.0;
                if (item.containsEnchantment(Enchantment.SHARPNESS)) {
                    damage += (0.5 * item.getEnchantmentLevel(Enchantment.SHARPNESS) + 0.5);
                }
                if (player.getFallDistance() > 0.0F && !player.isInsideVehicle() &&
                        !player.isClimbing() && !player.isSwimming()) {
                    damage *= 1.5;
                }
                event.setDamage(damage);
            }
        } else if (damager instanceof Trident trident) {
            ItemStack item = trident.getItemStack();
            if (item.containsEnchantment(Enchantment.FIRE_ASPECT)) {
                int level = item.getEnchantmentLevel(Enchantment.FIRE_ASPECT);
                int fireTicks = level * 80;
                if (victim.getFireTicks() < fireTicks) {
                    victim.setFireTicks(fireTicks);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent)) return;
        if (!(damageEvent.getDamager() instanceof Trident trident)) return;

        ItemStack tridentItem = trident.getItemStack();
        if (!tridentItem.containsEnchantment(Enchantment.LOOTING)) return;

        // If shooter isn't a player, we can't really apply looting
        if (!(trident.getShooter() instanceof Player shooter)) return;

        int lootingLevel = tridentItem.getEnchantmentLevel(Enchantment.LOOTING);

        // Optimization: If player is holding a better looting item, let vanilla handle it
        ItemStack realMainHand = shooter.getInventory().getItemInMainHand();
        if (realMainHand.getType() != Material.AIR &&
                realMainHand.getEnchantmentLevel(Enchantment.LOOTING) >= lootingLevel) {
            return;
        }

        if (event.getEntity() instanceof Mob mob) {
            LootTable lootTable = mob.getLootTable();
            if (lootTable == null) return;

            // 1. Swap Trident into main hand internally (Trick the system)
            ItemStack originalItem = shooter.getInventory().getItemInMainHand();
            shooter.getInventory().setItemInMainHand(tridentItem);

            try {
                // 2. Build context (It now reads the player's "current" hand - our trident)
                LootContext.Builder builder = new LootContext.Builder(mob.getLocation())
                        .lootedEntity(mob)
                        .killer(shooter);

                // 3. Generate drops
                Collection<ItemStack> newDrops = lootTable.populateLoot(new Random(), builder.build());

                event.getDrops().clear();
                event.getDrops().addAll(newDrops);

            } finally {
                // 4. ALWAYS restore the original item, even if error occurs
                shooter.getInventory().setItemInMainHand(originalItem);
            }
        }
    }

    // ... [Previous onItemHold Logic Remains the Same] ...

    @EventHandler
    public void onItemHold(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());

        resetAttributes(player);

        if (item != null && item.getType() == Material.TRIDENT) {
            AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
            if (speed != null) speed.setBaseValue(4.2);

            applyRangeModifier(player, Attribute.ENTITY_INTERACTION_RANGE, 5.2, rangeKey);
            applyRangeModifier(player, Attribute.BLOCK_INTERACTION_RANGE, 5.2, blockRangeKey);
        } else {
            AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
            if (speed != null) speed.setBaseValue(4.0);
        }
    }

    private void applyRangeModifier(Player player, Attribute attribute, double targetValue, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            double amount = targetValue - instance.getBaseValue();
            AttributeModifier modifier = new AttributeModifier(
                    key, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND
            );
            instance.addModifier(modifier);
        }
    }

    private void resetAttributes(Player player) {
        AttributeInstance entityRange = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        AttributeInstance blockRange = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (entityRange != null) entityRange.removeModifier(rangeKey);
        if (blockRange != null) blockRange.removeModifier(blockRangeKey);
    }

    @EventHandler
    public void onAnvilUse(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getItem(0);
        ItemStack second = event.getInventory().getItem(1);

        if (first == null || first.getType() != Material.TRIDENT || second == null) return;

        boolean isBook = second.getType() == Material.ENCHANTED_BOOK;
        boolean isTrident = second.getType() == Material.TRIDENT;

        if (!isBook && !isTrident) return;

        ItemStack result = first.clone();
        Map<Enchantment, Integer> incoming = isBook
                ? ((EnchantmentStorageMeta) second.getItemMeta()).getStoredEnchants()
                : second.getEnchantments();

        boolean modified = false;

        for (Map.Entry<Enchantment, Integer> entry : incoming.entrySet()) {
            Enchantment enchant = entry.getKey();
            if (isAllowedEnchantment(enchant)) {
                int current = result.getEnchantmentLevel(enchant);
                int adding = entry.getValue();
                int finalLevel;

                if (current == adding) {
                    finalLevel = current + 1;
                } else {
                    finalLevel = Math.max(current, adding);
                }

                if (finalLevel > current) {
                    result.addUnsafeEnchantment(enchant, finalLevel);
                    modified = true;
                }
            }
        }

        if (modified) {
            String rename = event.getView().getRenameText();
            ItemMeta meta = result.getItemMeta();

            if (meta != null && rename != null && !rename.isEmpty()) {
                meta.displayName(net.kyori.adventure.text.Component.text(rename));
                result.setItemMeta(meta);
            }

            event.setResult(result);
            event.getView().setRepairCost(5);
        }
    }


    private boolean isAllowedEnchantment(Enchantment enchantment) {
        return enchantment == Enchantment.SHARPNESS
                || enchantment == Enchantment.LOOTING
                || enchantment == Enchantment.FIRE_ASPECT;
    }
}
