package net.aetherion.skydrop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/** One drop zone: a cuboid area, a timer and a list of items. */
public final class Zone {

    /** An item that can drop, with a random amount between min and max. */
    public static final class Entry {
        public final Material material;
        public int min;
        public int max;

        public Entry(Material material, int min, int max) {
            this.material = material;
            this.min = Math.max(1, Math.min(min, max));
            this.max = Math.max(1, Math.max(min, max));
        }

        @Override
        public String toString() {
            return material.name() + ":" + min + "-" + max;
        }

        public static Entry parse(String text) {
            String[] parts = text.split(":");
            Material mat = Material.matchMaterial(parts[0]);
            if (mat == null || mat.isAir() || !mat.isItem()) return null;
            int min = 1, max = 1;
            if (parts.length > 1) {
                String[] range = parts[1].split("-");
                try {
                    min = Integer.parseInt(range[0].trim());
                    max = range.length > 1 ? Integer.parseInt(range[1].trim()) : min;
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
            return new Entry(mat, min, max);
        }
    }

    public final String name;
    public String world;
    public int minX, minY, minZ, maxX, maxY, maxZ;
    public int intervalSeconds = 60;
    public int itemsPerDrop = 5;
    public int heightAbove = 25;
    public boolean enabled = true;
    public final List<Entry> items = new ArrayList<>();
    public BukkitTask task;

    public Zone(String name) {
        this.name = name;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public double centerX() { return (minX + maxX + 1) / 2.0; }
    public double centerY() { return (minY + maxY + 1) / 2.0; }
    public double centerZ() { return (minZ + maxZ + 1) / 2.0; }

    public void save(ConfigurationSection s) {
        s.set("world", world);
        s.set("min-x", minX);
        s.set("min-y", minY);
        s.set("min-z", minZ);
        s.set("max-x", maxX);
        s.set("max-y", maxY);
        s.set("max-z", maxZ);
        s.set("interval-seconds", intervalSeconds);
        s.set("items-per-drop", itemsPerDrop);
        s.set("height-above", heightAbove);
        s.set("enabled", enabled);
        List<String> list = new ArrayList<>();
        for (Entry e : items) list.add(e.toString());
        s.set("items", list);
    }

    public static Zone load(String name, ConfigurationSection s) {
        Zone z = new Zone(name);
        z.world = s.getString("world");
        z.minX = s.getInt("min-x");
        z.minY = s.getInt("min-y");
        z.minZ = s.getInt("min-z");
        z.maxX = s.getInt("max-x");
        z.maxY = s.getInt("max-y");
        z.maxZ = s.getInt("max-z");
        z.intervalSeconds = Math.max(1, s.getInt("interval-seconds", 60));
        z.itemsPerDrop = Math.max(1, s.getInt("items-per-drop", 5));
        z.heightAbove = Math.max(1, s.getInt("height-above", 25));
        z.enabled = s.getBoolean("enabled", true);
        for (String line : s.getStringList("items")) {
            Entry e = Entry.parse(line);
            if (e != null) z.items.add(e);
        }
        return z;
    }
}
