package net.aetherion.skydrop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Wand: left click a block = position 1, right click a block = position 2. */
public final class SelectionListener implements Listener {

    private final SkyDropPlugin plugin;

    public SelectionListener(SkyDropPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !plugin.isWand(item)) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("skydrop.admin")) return;
        event.setCancelled(true);
        if (event.getClickedBlock() == null) return;
        if (action == Action.LEFT_CLICK_BLOCK) {
            plugin.setPos1(player, event.getClickedBlock().getLocation());
        } else {
            plugin.setPos2(player, event.getClickedBlock().getLocation());
        }
    }
}
