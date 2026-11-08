package net.aetherion.skydrop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class SkyDropPlugin extends JavaPlugin {

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final Map<String, Zone> zones = new LinkedHashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private final Random random = new Random();
    private NamespacedKey wandKey;
    private File zonesFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        wandKey = new NamespacedKey(this, "wand");
        zonesFile = new File(getDataFolder(), "zones.yml");
        loadZones();

        PluginCommand command = getCommand("skydrop");
        if (command != null) {
            SkyDropCommand handler = new SkyDropCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
        Bukkit.getPluginManager().registerEvents(new SelectionListener(this), this);
        getLogger().info("SkyDrop enabled with " + zones.size() + " zone(s).");
    }

    @Override
    public void onDisable() {
        for (Zone zone : zones.values()) stopTask(zone);
        saveZones();
    }

    // ------------------------------------------------------------------ text

    public static Component color(String text) {
        return SERIALIZER.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    /** Message with prefix and {placeholders} replaced. kv = key, value, key, value... */
    public Component msg(String key, Object... kv) {
        String prefix = getConfig().getString("messages.prefix", "");
        String raw = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            raw = raw.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return color(prefix + raw);
    }

    // ------------------------------------------------------------- selection

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(color("&#8A2BFF&l✦ &#FF55FF&lSkyDrop Wand &#8A2BFF&l✦"));
        meta.lore(java.util.List.of(
                color("&#D7B3FFLeft click a block &8» &#55FFFFPosition 1"),
                color("&#D7B3FFRight click a block &8» &#55FFFFPosition 2")));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public void setPos1(Player player, Location loc) {
        pos1.put(player.getUniqueId(), loc.clone());
        player.sendMessage(msg("pos1-set", "x", loc.getBlockX(), "y", loc.getBlockY(), "z", loc.getBlockZ()));
    }

    public void setPos2(Player player, Location loc) {
        pos2.put(player.getUniqueId(), loc.clone());
        player.sendMessage(msg("pos2-set", "x", loc.getBlockX(), "y", loc.getBlockY(), "z", loc.getBlockZ()));
    }

    public Location getPos1(Player player) {
        return pos1.get(player.getUniqueId());
    }

    public Location getPos2(Player player) {
        return pos2.get(player.getUniqueId());
    }

    // ----------------------------------------------------------------- zones

    public Map<String, Zone> zones() {
        return zones;
    }

    public Zone findZone(String name) {
        for (Map.Entry<String, Zone> e : zones.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    public void loadZones() {
        for (Zone zone : zones.values()) stopTask(zone);
        zones.clear();
        if (!zonesFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(zonesFile);
        var section = yaml.getConfigurationSection("zones");
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            var zs = section.getConfigurationSection(name);
            if (zs == null) continue;
            Zone zone = Zone.load(name, zs);
            zones.put(name, zone);
            startTask(zone);
        }
    }

    public void saveZones() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Zone zone : zones.values()) {
            zone.save(yaml.createSection("zones." + zone.name));
        }
        try {
            getDataFolder().mkdirs();
            yaml.save(zonesFile);
        } catch (IOException ex) {
            getLogger().severe("Could not save zones.yml: " + ex.getMessage());
        }
    }

    public void removeZone(Zone zone) {
        stopTask(zone);
        zones.remove(zone.name);
        saveZones();
    }

    // ----------------------------------------------------------------- timer

    public void startTask(Zone zone) {
        stopTask(zone);
        if (!zone.enabled) return;
        long ticks = Math.max(1L, zone.intervalSeconds) * 20L;
        zone.task = Bukkit.getScheduler().runTaskTimer(this, () -> dropOnce(zone), ticks, ticks);
    }

    public void stopTask(Zone zone) {
        if (zone.task != null) {
            zone.task.cancel();
            zone.task = null;
        }
    }

    /** Runs one drop cycle for the zone. Returns false if nothing could be dropped. */
    public boolean dropOnce(Zone zone) {
        World world = Bukkit.getWorld(zone.world == null ? "" : zone.world);
        if (world == null || zone.items.isEmpty()) return false;
        if (getConfig().getBoolean("settings.require-players-in-world", true) && world.getPlayers().isEmpty()) {
            return false;
        }

        boolean glow = getConfig().getBoolean("settings.glow-items", true);
        boolean particles = getConfig().getBoolean("settings.particles", true);
        int topY = Math.min(world.getMaxHeight() - 1, zone.maxY + zone.heightAbove);

        for (int i = 0; i < zone.itemsPerDrop; i++) {
            Zone.Entry entry = zone.items.get(random.nextInt(zone.items.size()));
            int amount = entry.min + random.nextInt(entry.max - entry.min + 1);

            double x = zone.minX + random.nextDouble() * (zone.maxX - zone.minX + 1);
            double z = zone.minZ + random.nextDouble() * (zone.maxZ - zone.minZ + 1);
            Location loc = new Location(world, x, topY, z);

            // make sure the chunk is loaded so the item really spawns
            world.getChunkAt(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);

            ItemStack stack = new ItemStack(entry.material, amount);
            world.dropItem(loc, stack, item -> {
                item.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                item.setPickupDelay(0);
                item.setGlowing(glow);
            });
            if (particles) {
                world.spawnParticle(Particle.END_ROD, loc, 12, 0.3, 0.3, 0.3, 0.02);
            }
        }

        String sound = getConfig().getString("settings.drop-sound", "");
        if (sound != null && !sound.isBlank()) {
            world.playSound(new Location(world, zone.centerX(), zone.centerY(), zone.centerZ()),
                    sound, SoundCategory.MASTER, 3.0f, 1.0f);
        }
        if (getConfig().getBoolean("settings.announce", true)) {
            Component message = msg("drop-announce",
                    "zone", zone.name,
                    "count", zone.itemsPerDrop,
                    "x", (int) zone.centerX(),
                    "y", (int) zone.centerY(),
                    "z", (int) zone.centerZ());
            for (Player player : world.getPlayers()) player.sendMessage(message);
        }
        return true;
    }
}
