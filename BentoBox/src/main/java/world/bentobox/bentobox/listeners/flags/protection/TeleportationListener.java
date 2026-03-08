package world.bentobox.bentobox.listeners.flags.protection;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import io.canvasmc.canvas.event.EntityTeleportAsyncEvent;

import world.bentobox.bentobox.api.flags.FlagListener;
import world.bentobox.bentobox.lists.Flags;

/**
 * Handles teleporting due to enderpearl or chorus fruit. Uses Canvas
 * EntityTeleportAsyncEvent for Folia compatibility.
 *
 * @author tastybento
 *
 */
public class TeleportationListener extends FlagListener {

	/**
	 * Ender pearl and chorus fruit teleport checks Uses Canvas
	 * EntityTeleportAsyncEvent for Folia compatibility.
	 *
	 * @param e
	 *            - entity teleport event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlayerTeleport(final EntityTeleportAsyncEvent e) {
		if (!(e.getEntity() instanceof org.bukkit.entity.Player player)) {
			return;
		}

		// Schedule on the destination world's region thread for Folia compatibility
		Bukkit.getRegionScheduler().run(getPlugin(), e.getTo(), t -> {
			// Check block type at destination to determine cause
			Material blockType = e.getTo().getBlock().getType();

			if (blockType == Material.END_STONE || blockType == Material.CHORUS_FLOWER) {
				checkIsland(e, player, e.getTo(), Flags.CHORUS_FRUIT);
			} else {
				// Default to ender pearl check for other teleports
				checkIsland(e, player, e.getTo(), Flags.ENDER_PEARL);
			}
		});
	}
}