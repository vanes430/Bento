package world.bentobox.bentobox.listeners.flags.worldsettings;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import io.canvasmc.canvas.event.EntityTeleportAsyncEvent;

import world.bentobox.bentobox.api.flags.FlagListener;
import world.bentobox.bentobox.lists.Flags;

/**
 * Removes mobs when teleporting to an island. Uses Canvas
 * EntityTeleportAsyncEvent for Folia compatibility.
 * 
 * @author tastybento
 *
 */
public class RemoveMobsListener extends FlagListener {

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onUserTeleport(EntityTeleportAsyncEvent e) {
		if (!(e.getEntity() instanceof org.bukkit.entity.Player player)) {
			return;
		}

		// Return if this is a small teleport
		if (e.getTo().getWorld().equals(player.getWorld())
				&& e.getTo().distanceSquared(player.getLocation()) < getPlugin().getSettings().getClearRadius()
						* getPlugin().getSettings().getClearRadius()) {
			return;
		}

		// Only process if flag is active
		if (getIslands().locationIsOnIsland(player, e.getTo())
				&& Flags.REMOVE_MOBS.isSetForWorld(e.getTo().getWorld())) {
			Bukkit.getRegionScheduler().run(getPlugin(), e.getTo(), t -> getIslands().clearArea(e.getTo()));
		}

	}

}
