package world.bentobox.bentobox.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.bentobox.BentoBox;

public class BlockEndDragon implements Listener {

	private final BentoBox plugin;

	public BlockEndDragon(@NonNull BentoBox plugin) {
		this.plugin = plugin;
	}

	/**
	 * Removes Ender Dragon and obsidian pillars when the End world is loaded. Uses
	 * EnderDragonBattle API to disable dragon spawn and pillar generation. Also
	 * generates 3x3 fake islands to create distance from dragon spawn point.
	 * 
	 * @param event
	 *            WorldLoadEvent
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onWorldLoad(WorldLoadEvent event) {
		World w = event.getWorld();
		if (w != null && w.getEnvironment().equals(Environment.THE_END) && plugin.getIWM().isIslandEnd(w)) {

			plugin.log("[BlockEndDragon] End world loaded: " + w.getName());

			// Schedule for next tick to ensure world is ready
			plugin.getServer().getScheduler().runTask(plugin, () -> {
				// Get EnderDragonBattle and disable dragon/pillars
				if (w.getEnderDragonBattle() != null) {
					// Remove dragon if already spawned
					if (w.getEnderDragonBattle().getEnderDragon() != null) {
						w.getEnderDragonBattle().getEnderDragon().remove();
						plugin.log("[BlockEndDragon] Removed Ender Dragon from " + w.getName());
					}

					// Set respawn phase to null to prevent pillar generation
					w.getEnderDragonBattle().setRespawnPhase(null);
					plugin.log("[BlockEndDragon] Disabled Ender Dragon battle in " + w.getName());
				} else {
					plugin.log("[BlockEndDragon] EnderDragonBattle is null in " + w.getName());
				}

				// Check settings
				boolean generateFakeIslands = plugin.getIWM().getWorldSettings(w).isEndGenerateFakeIslands();
				plugin.log("[BlockEndDragon] isEndGenerateFakeIslands = " + generateFakeIslands);

				// Generate 3x3 fake islands if enabled
				if (generateFakeIslands) {
					generateFakeIslands(w);
				} else {
					plugin.log("[BlockEndDragon] Fake islands generation is disabled");
				}

				// Also place END_PORTAL block at (0, MaxY-1, 0) as backup
				if (!w.getBlockAt(0, w.getMaxHeight() - 1, 0).getType().equals(Material.END_PORTAL)) {
					w.getBlockAt(0, w.getMaxHeight() - 1, 0).setType(Material.END_PORTAL, false);
					plugin.log("[BlockEndDragon] Placed END_PORTAL backup at (0, " + (w.getMaxHeight() - 1) + ", 0)");
				}
			});
		} else {
			if (w != null) {
				plugin.log("[BlockEndDragon] World " + w.getName() + " is not an island End world. Environment: "
						+ w.getEnvironment() + ", isIslandEnd: " + plugin.getIWM().isIslandEnd(w));
			}
		}
	}

	/**
	 * Generates 3x3 fake islands in the End dimension to create distance from
	 * dragon spawn point. This creates a buffer zone so player islands spawn
	 * further from (0,0).
	 * 
	 * @param w
	 *            End world
	 */
	private void generateFakeIslands(World w) {
		plugin.log("[BlockEndDragon] Generating 3x3 fake islands in " + w.getName());

		// Generate 3x3 grid of fake islands centered around (0,0)
		// Each island is 100 blocks apart
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				int islandX = x * 100; // 100 blocks between islands
				int islandZ = z * 100;
				int islandY = w.getMaxHeight() - 50; // Floating islands

				// Create a small fake island (5x5 platform)
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						w.getBlockAt(islandX + dx, islandY, islandZ + dz).setType(Material.END_STONE, false);
					}
				}
				// Add center block
				w.getBlockAt(islandX, islandY + 1, islandZ).setType(Material.END_STONE, false);
			}
		}

		plugin.log("[BlockEndDragon] Generated 9 fake islands in 3x3 grid");
	}

	/**
	 * Adds a portal frame at the top of the world, when a player joins an island
	 * End world. Also generates 3x3 fake islands as fallback if not already
	 * generated. This prevents the Ender Dragon from spawning: if any portal frame
	 * exists, then the dragon is considered killed already.
	 * 
	 * @param event
	 *            PlayerChangedWorldEvent
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
		World w = event.getPlayer().getWorld();
		if (w != null && w.getEnvironment().equals(Environment.THE_END) && plugin.getIWM().isIslandEnd(w)) {

			plugin.log("[BlockEndDragon] Player entered End world: " + w.getName());

			// Place END_PORTAL backup
			testLocation(event.getPlayer().getLocation());

			// Fallback: Generate fake islands if not already generated
			// Check if fake islands already generated (using a simple check)
			int checkY = w.getMaxHeight() - 50;
			if (w.getBlockAt(100, checkY, 100).getType() != Material.END_STONE) {
				plugin.log("[BlockEndDragon] Fake islands not found, generating...");

				boolean generateFakeIslands = plugin.getIWM().getWorldSettings(w).isEndGenerateFakeIslands();
				if (generateFakeIslands) {
					generateFakeIslands(w);
				} else {
					plugin.log("[BlockEndDragon] Fake islands generation is disabled in config");
				}
			} else {
				plugin.log("[BlockEndDragon] Fake islands already generated");
			}
		}
	}

	private void testLocation(Location location) {
		World w = location.getWorld();
		if (w == null || !plugin.getIWM().isIslandEnd(w)
				|| w.getBlockAt(0, w.getMaxHeight() - 1, 0).getType().equals(Material.END_PORTAL)) {
			return;
		}

		plugin.log("[BlockEndDragon] Placing END_PORTAL backup at (0, " + (w.getMaxHeight() - 1) + ", 0) in "
				+ w.getName());
		// Setting a End Portal at the top will trick dragon legacy check.
		w.getBlockAt(0, w.getMaxHeight() - 1, 0).setType(Material.END_PORTAL, false);
	}

	/**
	 * Adds a portal frame at the top of the world, when a player joins an island
	 * End world. This prevents the Ender Dragon from spawning: if any portal frame
	 * exists, then the dragon is considered killed already.
	 * 
	 * @param event
	 *            PlayerJoinEvent
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPlayerJoinWorld(PlayerJoinEvent event) {
		testLocation(event.getPlayer().getLocation());
	}

	/**
	 * Silently prevents block placing in the dead zone. This is just a simple
	 * protection.
	 * 
	 * @param e
	 *            - event
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onEndBlockPlace(BlockPlaceEvent e) {
		e.setCancelled(testBlock(e.getBlock()));
	}

	private boolean testBlock(Block block) {
		return block.getX() == 0 && block.getZ() == 0 && block.getY() == block.getWorld().getMaxHeight() - 1
				&& block.getWorld().getEnvironment().equals(Environment.THE_END)
				&& plugin.getIWM().inWorld(block.getWorld()) && plugin.getIWM().isEndGenerate(block.getWorld())
				&& plugin.getIWM().isEndIslands(block.getWorld());
	}

	/**
	 * Silently prevents block breaking in the dead zone. This is just a simple
	 * protection.
	 * 
	 * @param e
	 *            - event
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onEndBlockBreak(BlockBreakEvent e) {
		e.setCancelled(testBlock(e.getBlock()));
	}
}
