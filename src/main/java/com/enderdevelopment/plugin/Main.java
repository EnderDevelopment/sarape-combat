package com.enderdevelopment.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class Main extends JavaPlugin implements Listener {
    private static final long WINDOW = 60L;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, Integer> storedAxeDamage = new HashMap<>();
    private final Map<UUID, BukkitTask> lightning = new HashMap<>();
    private final Random random = new Random();

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> states.values().forEach(State::tick), 1L, 1L);
    }
    @Override public void onDisable() { lightning.values().forEach(BukkitTask::cancel); lightning.clear(); }
    private State state(Player p) { return states.computeIfAbsent(p.getUniqueId(), k -> new State()); }
    private void tell(Player p, String text) { p.sendMessage(Component.text(text).color(NamedTextColor.GOLD)); }
    private boolean holding(Player p, Material type) { return p.getInventory().getItemInMainHand().getType() == type; }
    private boolean critical(Player p) { return p.getFallDistance() > 0 && !p.isOnGround() && !p.isSprinting(); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p) || !(event.getEntity() instanceof LivingEntity target)) return;
        State s = state(p); long now = System.currentTimeMillis() / 50L;
        if (now - s.lastHit > WINDOW) s.reset(); s.lastHit = now;
        Material type = p.getInventory().getItemInMainHand().getType();
        if (type.name().endsWith("SWORD")) {
            if (++s.swordHits >= 3) { s.swordHits = 0; s.autoCrits = 2; tell(p, "Sword combo charged auto-crits."); }
            if (++s.swordUltimateHits >= 5) { s.swordUltimateHits = 0; s.swordUltimate = 300; tell(p, "Sword ultimate activated."); }
            if (s.autoCrits > 0) { event.setDamage(event.getDamage() * 1.25); s.autoCrits--; }
            if (s.swordUltimate > 0) event.setDamage(event.getDamage() * 1.5);
        } else if (type.name().endsWith("AXE")) {
            if (critical(p) && ++s.axeCrits >= 5) { s.axeCrits = 0; target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 10)); tell(p, "Axe stun applied for one second."); }
            if (++s.axeUltimateHits >= 7) { s.axeUltimateHits = 0; s.axeCharged = true; tell(p, "Axe ultimate charged."); }
            if (s.axeCharged) { storedAxeDamage.merge(p.getUniqueId(), (int) Math.ceil(event.getFinalDamage()), Integer::sum); event.setDamage(0); }
        } else if (type == Material.TRIDENT) {
            if (++s.tridentHits >= 5) { s.tridentHits = 0; startLightning(p, target); }
        } else if (type.name().endsWith("MACE")) {
            if (++s.maceHits >= 3) { s.maceHits = 0; s.maceCharged = true; tell(p, "Mace ultimate charged."); }
            if (s.maceCharged) apply(p.getInventory().getItemInMainHand(), "wind_burst", 2);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void projectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player p) || !(event.getHitEntity() instanceof LivingEntity target)) return;
        if (!(event.getEntity() instanceof AbstractArrow)) return;
        State s = state(p); Material type = p.getInventory().getItemInMainHand().getType();
        if (type == Material.BOW) {
            if (++s.bowShots % 3 == 0) target.addPotionEffect(randomDebuff());
            if (++s.bowUltimateHits >= 5) { s.bowUltimateHits = 0; s.bowUltimateShots = 3; tell(p, "Bow ultimate charged."); }
            if (s.bowUltimateShots > 0) { swap(p, target); s.bowUltimateShots--; }
        } else if (type == Material.CROSSBOW) {
            if (++s.crossbowHits >= 4) { s.crossbowHits = 0; s.crossbowUltimate = 300; apply(p.getInventory().getItemInMainHand(), "quick_charge", 5); apply(p.getInventory().getItemInMainHand(), "power", 5); tell(p, "Crossbow ultimate activated."); }
            if (target instanceof Player victim && victim.isBlocking()) victim.setCooldown(Material.SHIELD, 20);
            if (random.nextDouble() < .15) target.setFireTicks(100);
        }
    }

    @EventHandler public void bowShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player p && event.getBow() != null && event.getBow().getType() == Material.CROSSBOW && state(p).crossbowUltimate > 0) { apply(event.getBow(), "quick_charge", 5); apply(event.getBow(), "power", 5); }
    }
    @EventHandler(ignoreCancelled = true) public void taken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return; State s = state(p);
        if (p.isBlocking() && holding(p, Material.SHIELD)) { event.setDamage(event.getDamage() * .8); if (++s.shieldBlocks >= 5) { s.shieldUltimate = 300; s.shieldBlocks = 0; tell(p, "Shield ultimate activated."); } }
        if (wearingNetherite(p) && ++s.armorHits >= 5) { s.armorUltimate = 300; s.armorHits = 0; tell(p, "Armor ultimate activated."); }
        if (s.shieldUltimate > 0) event.setCancelled(true);
    }
    @EventHandler public void consume(PlayerItemConsumeEvent event) { if (event.getItem().getType() == Material.GOLDEN_APPLE && state(event.getPlayer()).armorUltimate > 0) event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 240, 3)); }

    @EventHandler public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer(); State s = state(p);
        if (p.isSneaking() && holding(p, Material.IRON_AXE) && s.axeCharged) { int damage = storedAxeDamage.getOrDefault(p.getUniqueId(), 0); if (damage > 0) { storedAxeDamage.remove(p.getUniqueId()); s.axeCharged = false; tell(p, "Released " + damage + " stored axe damage."); } }
        if (p.isSneaking() && holding(p, Material.SHIELD)) upgradeArmor(p);
    }

    private boolean wearingNetherite(Player p) { EntityEquipment e = p.getEquipment(); return e != null && e.getHelmet() != null && e.getChestplate() != null && e.getLeggings() != null && e.getBoots() != null && e.getHelmet().getType() == Material.NETHERITE_HELMET && e.getChestplate().getType() == Material.NETHERITE_CHESTPLATE && e.getLeggings().getType() == Material.NETHERITE_LEGGINGS && e.getBoots().getType() == Material.NETHERITE_BOOTS; }
    private void upgradeArmor(Player p) { EntityEquipment e = p.getEquipment(); if (e == null) return; ItemStack[] armor = {e.getHelmet(), e.getChestplate(), e.getLeggings(), e.getBoots()}; for (ItemStack item : armor) if (item == null || !item.getType().name().startsWith("DIAMOND_")) return; e.setHelmet(new ItemStack(Material.NETHERITE_HELMET)); e.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE)); e.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS)); e.setBoots(new ItemStack(Material.NETHERITE_BOOTS)); tell(p, "Diamond armor upgraded to Netherite."); }
    private void startLightning(Player p, LivingEntity target) { BukkitTask old = lightning.remove(p.getUniqueId()); if (old != null) old.cancel(); BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> { if (target.isValid() && !target.isDead()) { target.getWorld().strikeLightningEffect(target.getLocation()); target.damage(2, p); } }, 0L, 20L); lightning.put(p.getUniqueId(), task); Bukkit.getScheduler().runTaskLater(this, () -> { task.cancel(); lightning.remove(p.getUniqueId()); }, 100L); }
    private PotionEffect randomDebuff() { PotionEffectType[] types = {PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.POISON, PotionEffectType.BLINDNESS}; return new PotionEffect(types[random.nextInt(types.length)], 100, 0); }
    private void swap(Player p, LivingEntity target) { org.bukkit.Location a = p.getLocation(), b = target.getLocation(); p.teleport(b); target.teleport(a); }
    private void apply(ItemStack item, String key, int level) { ItemMeta m = item.getItemMeta(); if (m == null) return; Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key)); if (e != null) { m.addEnchant(e, level, true); item.setItemMeta(m); } }

    private static final class State {
        long lastHit; int swordHits, swordUltimateHits, autoCrits, swordUltimate, axeCrits, axeUltimateHits, tridentHits, bowShots, bowUltimateHits, bowUltimateShots, crossbowHits, crossbowUltimate, shieldBlocks, shieldUltimate, armorHits, armorUltimate, maceHits; boolean axeCharged, maceCharged;
        void reset() { swordHits = swordUltimateHits = axeCrits = axeUltimateHits = tridentHits = bowShots = bowUltimateHits = crossbowHits = shieldBlocks = maceHits = 0; }
        void tick() { if (swordUltimate > 0) swordUltimate--; if (crossbowUltimate > 0) crossbowUltimate--; if (shieldUltimate > 0) shieldUltimate--; if (armorUltimate > 0) armorUltimate--; }
    }
}
