package me.th3dandy.btk;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class TridentListener implements Listener {

    private static final float CRITICAL_HIT_THRESHOLD = 0.1F;
    private static final double TRIDENT_BASE_DAMAGE = 10.0;
    private static final double TRIDENT_ATTACK_SPEED = 4.2;
    private static final double DEFAULT_ATTACK_SPEED = 4.0;
    private static final double TRIDENT_REACH = 5.2;
    private static final int ANVIL_REPAIR_COST = 5;

    private static final Set<Enchantment> ALLOWED_ENCHANTMENTS = Set.of(
            Enchantment.SHARPNESS,
            Enchantment.LOOTING,
            Enchantment.FIRE_ASPECT
    );

    private final NamespacedKey rangeKey = new NamespacedKey("better_tridents", "reach_distance");
    private final NamespacedKey blockRangeKey = new NamespacedKey("better_tridents", "block_reach");

    private record TridentThrowRecord(UUID shooterUUID, EquipmentSlot hand, int originalSlot) {
    }

    private final Map<String, TridentThrowRecord> thrownTridents = new HashMap<>();

    // Helpers
    private boolean isTrident(ItemStack item) {
        return item != null && item.getType() == Material.TRIDENT;
    }

    private boolean isAir(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    // Attribute Management
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        applyTridentAttributesIfHeld(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        resetAttributes(event.getPlayer());
        applyTridentAttributesIfHeld(event.getPlayer());
    }

    private void applyTridentAttributesIfHeld(Player player) {
        if (isTrident(player.getInventory().getItemInMainHand())) applyTridentAttributes(player);
    }

    @EventHandler
    public void onItemHold(PlayerItemHeldEvent event) {
        resetAttributes(event.getPlayer());
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (isTrident(item)) applyTridentAttributes(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isTrident(event.getItemDrop().getItemStack())) resetAttributes(event.getPlayer());
    }

    private void applyTridentAttributes(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null) speed.setBaseValue(TRIDENT_ATTACK_SPEED);

        applyRangeModifier(player, Attribute.ENTITY_INTERACTION_RANGE, TRIDENT_REACH, rangeKey);
        applyRangeModifier(player, Attribute.BLOCK_INTERACTION_RANGE, TRIDENT_REACH, blockRangeKey);
    }

    private void applyRangeModifier(Player player, Attribute attribute, double targetValue, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(key);

        instance.addModifier(new AttributeModifier(key, targetValue - instance.getBaseValue(),
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
    }

    private void resetAttributes(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null) speed.setBaseValue(DEFAULT_ATTACK_SPEED);

        if (player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE) != null)
            player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE).removeModifier(rangeKey);

        if (player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE) != null)
            player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).removeModifier(blockRangeKey);
    }

    //Riptide
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!isTrident(item) || !item.containsEnchantment(Enchantment.RIPTIDE)) return;

        Player player = event.getPlayer();
        boolean isWet = player.isInWater() || (player.getWorld().hasStorm() && player.getLocation().getBlock().getLightFromSky() > 0);

        if (!isWet && !player.hasCooldown(Material.TRIDENT)) {
            event.setCancelled(true);
            int level = item.getEnchantmentLevel(Enchantment.RIPTIDE);
            player.setVelocity(player.getLocation().getDirection().normalize().multiply(1.8 + (level * 0.5)));

            player.startRiptideAttack(20, (float) TRIDENT_BASE_DAMAGE, item);

            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 1.0f);
            player.setCooldown(Material.TRIDENT, 15);
        }
    }

    // Channeling
    @EventHandler
    public void onTridentHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        ItemStack item = trident.getItemStack();
        if (!item.containsEnchantment(Enchantment.CHANNELING)) return;

        org.bukkit.Location loc = event.getHitEntity() != null ? event.getHitEntity().getLocation() :
                (event.getHitBlock() != null ? event.getHitBlock().getLocation() : trident.getLocation());

        LightningStrike strike = trident.getWorld().spawn(loc, LightningStrike.class);
        if (trident.getShooter() instanceof Player p) strike.setCausingPlayer(p);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onTridentDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isTrident(player.getInventory().getItemInMainHand())) {
            ItemStack item = player.getInventory().getItemInMainHand();
            double damage = TRIDENT_BASE_DAMAGE;
            if (item.containsEnchantment(Enchantment.SHARPNESS))
                damage += (0.5 * item.getEnchantmentLevel(Enchantment.SHARPNESS) + 0.5);
            if (player.getFallDistance() > CRITICAL_HIT_THRESHOLD && !player.isSwimming()) damage *= 1.5;
            event.setDamage(damage);
        } else if (event.getDamager() instanceof Trident trident) {
            ItemStack item = trident.getItemStack();
            if (item.containsEnchantment(Enchantment.SHARPNESS))
                event.setDamage(event.getDamage() + (0.5 * item.getEnchantmentLevel(Enchantment.SHARPNESS) + 0.5));
            if (item.containsEnchantment(Enchantment.FIRE_ASPECT))
                event.getEntity().setFireTicks(item.getEnchantmentLevel(Enchantment.FIRE_ASPECT) * 80);
        }
    }

    // Looting
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent d) || !(d.getDamager() instanceof Trident t))
            return;
        int lvl = t.getItemStack().getEnchantmentLevel(Enchantment.LOOTING);
        if (lvl <= 0 || !(t.getShooter() instanceof Player shooter)) return;

        LootTable lt = ((Mob) event.getEntity()).getLootTable();
        if (lt != null) {
            event.getDrops().clear();
            event.getDrops().addAll(lt.populateLoot(new Random(), new LootContext.Builder(event.getEntity().getLocation()).lootedEntity(event.getEntity()).killer(shooter).luck(lvl).build()));
        }
    }

    // Anvil
    @EventHandler
    public void onAnvilUse(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getItem(0);
        ItemStack second = event.getInventory().getItem(1);
        if (first == null || first.getType() != Material.TRIDENT || second == null) return;

        ItemStack result = first.clone();
        Map<Enchantment, Integer> incoming = second.getType() == Material.ENCHANTED_BOOK
                ? ((EnchantmentStorageMeta) second.getItemMeta()).getStoredEnchants()
                : second.getEnchantments();

        int added = 0;

        for (var entry : incoming.entrySet()) {
            if (!ALLOWED_ENCHANTMENTS.contains(entry.getKey())) continue;
            int cur = result.getEnchantmentLevel(entry.getKey());
            int next = (cur == entry.getValue()) ? cur + 1 : Math.max(cur, entry.getValue());
            if (next > cur) {
                result.addUnsafeEnchantment(entry.getKey(), next);
                added++;
            }
        }

        if (added > 0) {
            ItemMeta meta = result.getItemMeta();

            String renameText = event.getView().getRenameText();

            if (renameText != null && !renameText.isEmpty()) {
                meta.displayName(Component.text(renameText));
            }

            result.setItemMeta(meta);
            event.setResult(result);

            event.getView().setRepairCost(Math.min(added, ANVIL_REPAIR_COST));
        }
    }
}