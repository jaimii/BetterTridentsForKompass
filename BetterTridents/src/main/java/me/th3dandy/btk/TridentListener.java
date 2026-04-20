package me.th3dandy.btk;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.Map;

public class TridentListener implements Listener {

    private final NamespacedKey rangeKey = new NamespacedKey("better_tridents", "reach_distance");
    private final NamespacedKey blockRangeKey = new NamespacedKey("better_tridents", "block_reach");

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (damager instanceof Player player) {
            ItemStack item = player.getInventory().getItemInMainHand();

            if (item.getType() == Material.TRIDENT) {
                double finalDamage = 10.0;

                if (item.containsEnchantment(Enchantment.SHARPNESS)) {
                    int level = item.getEnchantmentLevel(Enchantment.SHARPNESS);
                    finalDamage += 0.5 * level + 0.5;
                }

                if (player.getFallDistance() > 0.0F && !player.isOnGround() && !player.isInsideVehicle()) {
                    finalDamage *= 1.5;
                }

                event.setDamage(finalDamage);
            }
        } else if (damager instanceof Trident trident) {
            ItemStack item = trident.getItem();

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
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.TRIDENT) {
            AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
            if (attackSpeed != null) attackSpeed.setBaseValue(4.2);

            applyRangeAttribute(player, Attribute.ENTITY_INTERACTION_RANGE, 5.2, rangeKey);
            applyRangeAttribute(player, Attribute.BLOCK_INTERACTION_RANGE, 5.2, blockRangeKey);
        } else {
            resetAttribute(player, Attribute.ENTITY_INTERACTION_RANGE, rangeKey);
            resetAttribute(player, Attribute.BLOCK_INTERACTION_RANGE, blockRangeKey);
        }
    }

    private void applyRangeAttribute(Player player, Attribute attribute, double value, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && instance.getModifier(key) == null) {
            double amount = value - instance.getBaseValue();

            AttributeModifier modifier = new AttributeModifier(
                    key,
                    amount,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            );
            instance.addModifier(modifier);
        }
    }

    private void resetAttribute(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && instance.getModifier(key) != null) {
            instance.removeModifier(key);
        }
    }

    @EventHandler
    public void onAnvilUse(PrepareAnvilEvent event) {
        ItemStack firstItem = event.getInventory().getItem(0);
        ItemStack secondItem = event.getInventory().getItem(1);

        if (firstItem != null && firstItem.getType() == Material.TRIDENT && secondItem != null) {
            ItemStack result = firstItem.clone();
            boolean modified = false;

            if (secondItem.getType() == Material.ENCHANTED_BOOK) {
                EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) secondItem.getItemMeta();
                if (bookMeta != null) {
                    for (Map.Entry<Enchantment, Integer> entry : bookMeta.getStoredEnchants().entrySet()) {
                        if (isAllowedEnchantment(entry.getKey())) {
                            result.addUnsafeEnchantment(entry.getKey(), entry.getValue());
                            modified = true;
                        }
                    }
                }
            } else if (secondItem.getType() == Material.TRIDENT) {
                for (Map.Entry<Enchantment, Integer> entry : secondItem.getEnchantments().entrySet()) {
                    if (isAllowedEnchantment(entry.getKey())) {
                        int level = Math.max(result.getEnchantmentLevel(entry.getKey()), entry.getValue());
                        result.addUnsafeEnchantment(entry.getKey(), level);
                        modified = true;
                    }
                }
            }

            if (modified) {
                event.setResult(result);
                // FIX 6: setRepairCost moved to AnvilView
                AnvilView view = event.getView();
                view.setRepairCost(1);
            }
        }
    }

    private boolean isAllowedEnchantment(Enchantment enchantment) {
        return enchantment == Enchantment.SHARPNESS
                || enchantment == Enchantment.LOOTING
                || enchantment == Enchantment.FIRE_ASPECT;
    }
}
