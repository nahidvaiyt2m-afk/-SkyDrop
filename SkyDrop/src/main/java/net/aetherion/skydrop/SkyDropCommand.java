package net.aetherion.skydrop;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SkyDropCommand implements CommandExecutor, TabCompleter {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final List<String> SUBS = List.of(
            "help", "wand", "pos1", "pos2", "create", "delete", "list", "info",
            "additem", "removeitem", "interval", "amount", "height",
            "start", "stop", "now", "reload");

    private final SkyDropPlugin plugin;

    public SkyDropCommand(SkyDropPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skydrop.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "wand" -> {
                Player p = player(sender);
                if (p == null) return true;
                p.getInventory().addItem(plugin.createWand());
                p.sendMessage(plugin.msg("wand-given"));
            }
            case "pos1" -> {
                Player p = player(sender);
                if (p != null) plugin.setPos1(p, p.getLocation().getBlock().getLocation());
            }
            case "pos2" -> {
                Player p = player(sender);
                if (p != null) plugin.setPos2(p, p.getLocation().getBlock().getLocation());
            }
            case "create" -> create(sender, args);
            case "delete" -> {
                Zone z = zone(sender, args, 1);
                if (z == null) return true;
                plugin.removeZone(z);
                sender.sendMessage(plugin.msg("deleted", "zone", z.name));
            }
            case "list" -> list(sender);
            case "info" -> {
                Zone z = zone(sender, args, 1);
                if (z != null) info(sender, z);
            }
            case "additem" -> addItem(sender, args);
            case "removeitem" -> removeItem(sender, args);
            case "interval" -> setNumber(sender, args, "interval");
            case "amount" -> setNumber(sender, args, "amount");
            case "height" -> setNumber(sender, args, "height");
            case "start" -> {
                Zone z = zone(sender, args, 1);
                if (z == null) return true;
                z.enabled = true;
                plugin.startTask(z);
                plugin.saveZones();
                sender.sendMessage(plugin.msg("started", "zone", z.name));
            }
            case "stop" -> {
                Zone z = zone(sender, args, 1);
                if (z == null) return true;
                z.enabled = false;
                plugin.stopTask(z);
                plugin.saveZones();
                sender.sendMessage(plugin.msg("stopped", "zone", z.name));
            }
            case "now" -> {
                Zone z = zone(sender, args, 1);
                if (z == null) return true;
                if (z.items.isEmpty()) {
                    sender.sendMessage(plugin.msg("no-items", "zone", z.name));
                    return true;
                }
                // manual drop ignores the "require players" setting by design of dropOnce; players in world needed
                plugin.dropOnce(z);
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.loadZones();
                sender.sendMessage(plugin.msg("reloaded"));
            }
            default -> help(sender, label);
        }
        return true;
    }

    // ------------------------------------------------------------- handlers

    private void create(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        if (args.length < 3) {
            p.sendMessage(SkyDropPlugin.color("&#FF5555&l✖ &#D7B3FFUsage: &#55FFFF/skydrop create <name> <seconds>"));
            return;
        }
        String name = args[1];
        if (!NAME.matcher(name).matches()) {
            p.sendMessage(plugin.msg("bad-name"));
            return;
        }
        if (plugin.findZone(name) != null) {
            p.sendMessage(plugin.msg("zone-exists", "zone", name));
            return;
        }
        Integer seconds = positive(args[2]);
        if (seconds == null) {
            p.sendMessage(plugin.msg("bad-number", "value", args[2]));
            return;
        }
        Location a = plugin.getPos1(p);
        Location b = plugin.getPos2(p);
        if (a == null || b == null) {
            p.sendMessage(plugin.msg("no-selection"));
            return;
        }
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            p.sendMessage(plugin.msg("different-worlds"));
            return;
        }
        Zone zone = new Zone(name);
        zone.world = a.getWorld().getName();
        zone.minX = Math.min(a.getBlockX(), b.getBlockX());
        zone.minY = Math.min(a.getBlockY(), b.getBlockY());
        zone.minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        zone.maxX = Math.max(a.getBlockX(), b.getBlockX());
        zone.maxY = Math.max(a.getBlockY(), b.getBlockY());
        zone.maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        zone.intervalSeconds = seconds;
        plugin.zones().put(zone.name, zone);
        plugin.saveZones();
        plugin.startTask(zone);
        p.sendMessage(plugin.msg("created", "zone", name, "size", zone.volume(), "interval", seconds));
    }

    private void list(CommandSender sender) {
        sender.sendMessage(SkyDropPlugin.color(
                "&8&l◆ &#8A2BFF&l━━ &#55FFFF&lSKYDROP ZONES &#8A2BFF&l━━ &8&l◆"));
        if (plugin.zones().isEmpty()) {
            sender.sendMessage(SkyDropPlugin.color("&7No zones yet. Use &#55FFFF/skydrop wand &7then &#55FFFF/skydrop create"));
            return;
        }
        for (Zone z : plugin.zones().values()) {
            String state = z.enabled ? "&#55FF55ON" : "&#FF5555OFF";
            sender.sendMessage(SkyDropPlugin.color("&#FF55FF" + z.name + " &8» " + state
                    + " &8| &#D7B3FF" + z.world + " &8| &#55FFFFevery " + z.intervalSeconds
                    + "s &8| &#FFD700" + z.items.size() + " items"));
        }
    }

    private void info(CommandSender sender, Zone z) {
        sender.sendMessage(SkyDropPlugin.color(
                "&8&l◆ &#8A2BFF&l━━ &#FF55FF&l" + z.name + " &#8A2BFF&l━━ &8&l◆"));
        sender.sendMessage(SkyDropPlugin.color("&#D7B3FFWorld: &#FFFFFF" + z.world
                + " &8| &#D7B3FFState: " + (z.enabled ? "&#55FF55ON" : "&#FF5555OFF")));
        sender.sendMessage(SkyDropPlugin.color("&#D7B3FFArea: &#55FFFF" + z.minX + "," + z.minY + "," + z.minZ
                + " &8→ &#55FFFF" + z.maxX + "," + z.maxY + "," + z.maxZ + " &8(" + z.volume() + " blocks)"));
        sender.sendMessage(SkyDropPlugin.color("&#D7B3FFEvery &#FF55FF" + z.intervalSeconds
                + "s &8| &#D7B3FFItems per drop: &#FF55FF" + z.itemsPerDrop
                + " &8| &#D7B3FFHeight above: &#FF55FF" + z.heightAbove));
        StringBuilder sb = new StringBuilder("&#D7B3FFItems: ");
        if (z.items.isEmpty()) sb.append("&7none");
        for (Zone.Entry e : z.items) {
            sb.append("&#FFD700").append(e.material.name()).append(" &8(").append(e.min).append("-").append(e.max).append(") ");
        }
        sender.sendMessage(SkyDropPlugin.color(sb.toString()));
    }

    private void addItem(CommandSender sender, String[] args) {
        Zone z = zone(sender, args, 1);
        if (z == null) return;
        if (args.length < 3) {
            sender.sendMessage(SkyDropPlugin.color("&#FF5555&l✖ &#D7B3FFUsage: &#55FFFF/skydrop additem <zone> <item> [min] [max]"));
            return;
        }
        Material mat = Material.matchMaterial(args[2]);
        if (mat == null || mat.isAir() || !mat.isItem()) {
            sender.sendMessage(plugin.msg("bad-material", "value", args[2]));
            return;
        }
        int min = 1;
        int max = 1;
        if (args.length >= 4) {
            Integer v = positive(args[3]);
            if (v == null) {
                sender.sendMessage(plugin.msg("bad-number", "value", args[3]));
                return;
            }
            min = v;
            max = v;
        }
        if (args.length >= 5) {
            Integer v = positive(args[4]);
            if (v == null) {
                sender.sendMessage(plugin.msg("bad-number", "value", args[4]));
                return;
            }
            max = v;
        }
        z.items.removeIf(e -> e.material == mat);
        Zone.Entry entry = new Zone.Entry(mat, min, max);
        z.items.add(entry);
        plugin.saveZones();
        sender.sendMessage(plugin.msg("item-added", "item", mat.name(), "min", entry.min, "max", entry.max, "zone", z.name));
    }

    private void removeItem(CommandSender sender, String[] args) {
        Zone z = zone(sender, args, 1);
        if (z == null) return;
        if (args.length < 3) {
            sender.sendMessage(SkyDropPlugin.color("&#FF5555&l✖ &#D7B3FFUsage: &#55FFFF/skydrop removeitem <zone> <item>"));
            return;
        }
        Material mat = Material.matchMaterial(args[2]);
        boolean removed = mat != null && z.items.removeIf(e -> e.material == mat);
        if (!removed) {
            sender.sendMessage(plugin.msg("item-not-found", "item", args[2]));
            return;
        }
        plugin.saveZones();
        sender.sendMessage(plugin.msg("item-removed", "item", mat.name(), "zone", z.name));
    }

    private void setNumber(CommandSender sender, String[] args, String what) {
        Zone z = zone(sender, args, 1);
        if (z == null) return;
        if (args.length < 3) {
            sender.sendMessage(SkyDropPlugin.color("&#FF5555&l✖ &#D7B3FFUsage: &#55FFFF/skydrop " + what + " <zone> <number>"));
            return;
        }
        Integer v = positive(args[2]);
        if (v == null) {
            sender.sendMessage(plugin.msg("bad-number", "value", args[2]));
            return;
        }
        switch (what) {
            case "interval" -> {
                z.intervalSeconds = v;
                plugin.startTask(z);
            }
            case "amount" -> z.itemsPerDrop = v;
            case "height" -> z.heightAbove = v;
            default -> { }
        }
        plugin.saveZones();
        sender.sendMessage(plugin.msg("updated", "zone", z.name));
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(SkyDropPlugin.color("&8&l◆ &#8A2BFF&l━━ &#FF55FF&l✦ &#55FFFF&lSKYDROP &#FF55FF&l✦ &#8A2BFF&l━━ &8&l◆"));
        String[][] lines = {
                {"wand", "Get the selection wand (left = pos1, right = pos2)"},
                {"pos1 / pos2", "Set a corner at your position"},
                {"create <name> <seconds>", "Create a zone from your selection"},
                {"additem <zone> <item> [min] [max]", "Add an item (random amount min-max)"},
                {"removeitem <zone> <item>", "Remove an item"},
                {"interval <zone> <seconds>", "Change time between drops"},
                {"amount <zone> <number>", "Items spawned per drop"},
                {"height <zone> <blocks>", "Start height above the zone"},
                {"start / stop <zone>", "Turn a zone on or off"},
                {"now <zone>", "Drop once right now"},
                {"list / info <zone>", "Show zones"},
                {"delete <zone>", "Delete a zone"},
                {"reload", "Reload config and zones"}
        };
        for (String[] l : lines) {
            sender.sendMessage(SkyDropPlugin.color("&#55FFFF/" + label + " " + l[0] + " &8» &#D7B3FF" + l[1]));
        }
    }

    // -------------------------------------------------------------- helpers

    private Player player(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage(plugin.msg("player-only"));
        return null;
    }

    private Zone zone(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage(SkyDropPlugin.color("&#FF5555&l✖ &#D7B3FFPlease give a zone name."));
            return null;
        }
        Zone z = plugin.findZone(args[index]);
        if (z == null) sender.sendMessage(plugin.msg("unknown-zone", "zone", args[index]));
        return z;
    }

    private static Integer positive(String text) {
        try {
            int v = Integer.parseInt(text);
            return v >= 1 ? v : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ---------------------------------------------------------- tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("skydrop.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(SUBS);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "delete", "info", "additem", "removeitem", "interval", "amount",
                     "height", "start", "stop", "now" -> out.addAll(plugin.zones().keySet());
                default -> { }
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("additem")) {
                for (Material m : Material.values()) {
                    if (m.isItem() && !m.isAir() && !m.name().startsWith("LEGACY_")) out.add(m.name().toLowerCase(Locale.ROOT));
                }
            } else if (sub.equals("removeitem")) {
                Zone z = plugin.findZone(args[1]);
                if (z != null) for (Zone.Entry e : z.items) out.add(e.material.name().toLowerCase(Locale.ROOT));
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
