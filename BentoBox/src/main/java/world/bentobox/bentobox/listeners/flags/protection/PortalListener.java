package world.bentobox.bentobox.listeners.flags.protection;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import io.canvasmc.canvas.event.EntityPortalAsyncEvent;

import world.bentobox.bentobox.api.flags.FlagListener;
import world.bentobox.bentobox.lists.Flags;
import world.bentobox.bentobox.util.Util;

/**
 * Handles portal protection Uses Canvas EntityPortalAsyncEvent for Folia
 * compatibility.
 * 
 * @author tastybento
 */
public class PortalListener extends FlagListener {

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPlayerPortal(EntityPortalAsyncEvent e) {
		if (!(e.getEntity() instanceof org.bukkit.entity.Player player)) {
			return;
		}

		World fromWorld = e.getFrom();
		World overWorld = Util.getWorld(fromWorld);

		// Only check flags if this is a BentoBox world
		if (overWorld == null || !getIWM().inWorld(overWorld)) {
			return;
		}

		getPlugin().log("[PortalListener] Player " + player.getName() + " trying to portal from " + fromWorld.getName()
				+ " to " + e.getTo().getName());

		// Check NETHER_PORTAL flag
		if (e.getTo().getEnvironment() == World.Environment.NETHER) {
			getPlugin().log("[PortalListener] Checking NETHER_PORTAL flag for " + overWorld.getName() + " - enabled: "
					+ Flags.NETHER_PORTAL.isSetForWorld(overWorld));
			if (!Flags.NETHER_PORTAL.isSetForWorld(overWorld)) {
				getPlugin().log("[PortalListener] Cancelled portal - NETHER_PORTAL flag not allowed");
				e.setCancelled(true);
				return;
			}
		}

		// Check END_PORTAL flag
		if (e.getTo().getEnvironment() == World.Environment.THE_END) {
			getPlugin().log("[PortalListener] Checking END_PORTAL flag for " + overWorld.getName() + " - enabled: "
					+ Flags.END_PORTAL.isSetForWorld(overWorld));
			if (!Flags.END_PORTAL.isSetForWorld(overWorld)) {
				getPlugin().log("[PortalListener] Cancelled portal - END_PORTAL flag not allowed");
				e.setCancelled(true);
				return;
			}
		}
	}

}
