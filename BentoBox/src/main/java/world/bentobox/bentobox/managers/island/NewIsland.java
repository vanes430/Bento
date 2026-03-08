package world.bentobox.bentobox.managers.island;

import java.io.IOException;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.util.Vector;

import world.bentobox.bentobox.BStats;
import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.events.IslandBaseEvent;
import world.bentobox.bentobox.api.events.island.IslandCreateEvent;
import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.api.events.island.IslandEvent.Reason;
import world.bentobox.bentobox.api.events.island.IslandResetEvent;
import world.bentobox.bentobox.api.logs.LogEntry;
import world.bentobox.bentobox.api.logs.LogEntry.LogType;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.BlueprintsManager;
import world.bentobox.bentobox.managers.IslandsManager;

/**
 * Handles the creation and pasting of a new island for a player. This class
 * uses a builder pattern for flexible construction.
 *
 * <p>
 * The process involves:
 * <ul>
 * <li>Determining the next available location for the island</li>
 * <li>Cleaning up user data (e.g., deaths, permissions)</li>
 * <li>Firing relevant events for plugins/addons</li>
 * <li>Pasting the blueprint or skipping paste if requested</li>
 * <li>Setting up metrics and logging</li>
 * </ul>
 * </p>
 *
 * @author tastybento
 */
public class NewIsland {
	private final BentoBox plugin;
	private Island island;
	private final User user;
	private final Reason reason;
	private final World world;
	private String name;
	private final boolean noPaste;
	private final GameModeAddon addon;
	private final boolean force;

	private NewIslandLocationStrategy locationStrategy;

	/**
	 * Queue for island operations to prevent race conditions Uses AtomicInteger for
	 * thread-safe position tracking
	 */
	private static final Queue<Runnable> operationQueue = new ConcurrentLinkedQueue<>();
	private static final AtomicInteger queueSize = new AtomicInteger(0);
	private static final AtomicBoolean isProcessing = new AtomicBoolean(false);

	/**
	 * Constructs a new island using the provided builder. Fires a PRECREATE event
	 * before proceeding. Island creation is queued to prevent race conditions with
	 * concurrent requests.
	 *
	 * @param builder
	 *            Builder containing all required parameters
	 * @throws IOException
	 *             if insufficient parameters or event is cancelled
	 */
	public NewIsland(Builder builder) throws IOException {
		plugin = BentoBox.getInstance();
		this.user = builder.user2;
		this.reason = builder.reason2;
		this.world = builder.world2;
		this.name = builder.name2;
		this.noPaste = builder.noPaste2;
		this.addon = builder.addon2;
		this.locationStrategy = builder.locationStrategy2;
		this.force = builder.force2;

		// Use default location strategy if none provided
		if (this.locationStrategy == null) {
			this.locationStrategy = new DefaultNewIslandLocationStrategy();
		}
		// Fire pre-create event to allow cancellation or modification
		IslandBaseEvent event = IslandEvent.builder().involvedPlayer(user.getUniqueId()).reason(Reason.PRECREATE)
				.build();
		if (event.getNewEvent().map(IslandBaseEvent::isCancelled).orElse(event.isCancelled())) {
			// Event was cancelled, abort creation
			return;
		}

		// Atomically increment queue size and get position (thread-safe!)
		int position = queueSize.incrementAndGet();

		// Add to queue for sequential processing
		operationQueue.add(() -> {
			try {
				// VALIDATION: Check if player still needs island creation
				// Skip online check if force flag is set (for admin fake generate)
				if (!force && !user.isOnline()) {
					plugin.log("[NewIsland] Queue: Player " + user.getName() + " is offline, skipping");
					user.sendMessage("commands.island.create.cannot-create-island");
				} else if (!force && plugin.getIslands().hasIsland(world, user)) {
					plugin.log("[NewIsland] Queue: Player " + user.getName() + " already has island, skipping");
					user.sendMessage("commands.island.create.already-have-island");
				} else {
					// Player still needs island, proceed with creation (or force mode)
					newIsland(builder.oldIsland2);
				}
			} catch (IOException e) {
				plugin.logError("Failed to create island from queue: " + e.getMessage());
				if (!force) {
					user.sendMessage("commands.island.create.unable-create-island");
				}
			} finally {
				queueSize.decrementAndGet();
			}
		});

		// Notify player of queue position
		if (position > 1) {
			user.sendMessage("commands.island.create.queued", "[position]", String.valueOf(position));
		}

		// Start processing if not already running
		processQueue();
	}

	/**
	 * Adds an island deletion to the operation queue to prevent race conditions
	 * with island creation.
	 *
	 * @param plugin
	 *            BentoBox plugin instance
	 * @param island
	 *            Island to delete
	 * @param removeBlocks
	 *            Whether to remove blocks
	 * @param involvedPlayer
	 *            Player involved in deletion (may be null)
	 */
	public static void queueDeleteIsland(BentoBox plugin, Island island, boolean removeBlocks, UUID involvedPlayer) {
		int position = queueSize.incrementAndGet();

		operationQueue.add(() -> {
			try {
				// Perform actual deletion via IslandsManager
				plugin.getIslands().deleteIslandInternal(island, removeBlocks, involvedPlayer);
			} finally {
				queueSize.decrementAndGet();
			}
		});

		processQueue();
	}

	/**
	 * Processes the operation queue sequentially Each task must complete before the
	 * next one starts Uses AtomicBoolean.compareAndSet to prevent multiple threads
	 * processing Adds 20 tick (1 second) delay between tasks for Folia safety
	 */
	private static void processQueue() {
		if (!isProcessing.compareAndSet(false, true)) {
			return; // Another thread is already processing
		}

		Runnable task = operationQueue.poll();

		if (task != null) {
			// Run task with 20 tick (1 second) delay BEFORE execution
			// This ensures proper spacing between island creations
			Bukkit.getGlobalRegionScheduler().runDelayed(BentoBox.getInstance(), t -> {
				try {
					task.run();
				} catch (Exception e) {
					BentoBox.getInstance().logError("Queue task failed: " + e.getMessage());
				} finally {
					// Release lock AFTER task completes
					isProcessing.set(false);
					// Process next task in queue (will also have 20 tick delay)
					processQueue();
				}
			}, 20L);
		} else {
			// Queue is empty, release lock
			isProcessing.set(false);
		}
	}

	/**
	 * @return the island that was created
	 */
	public Island getIsland() {
		return island;
	}

	/**
	 * Start building a new island
	 * 
	 * @return New island builder object
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for NewIsland. Allows flexible construction and validation of
	 * required parameters.
	 */
	public static class Builder {
		private Island oldIsland2;
		private User user2;
		private Reason reason2;
		private World world2;
		private String name2 = BlueprintsManager.DEFAULT_BUNDLE_NAME;
		private boolean noPaste2;
		private GameModeAddon addon2;
		private NewIslandLocationStrategy locationStrategy2;
		private boolean force2;

		/**
		 * Sets the old island to be replaced. Also sets the world to the old island's
		 * world.
		 */
		public Builder oldIsland(Island oldIsland) {
			this.oldIsland2 = oldIsland;
			this.world2 = oldIsland.getWorld();
			return this;
		}

		/**
		 * Sets the player for whom the island is being created.
		 */
		public Builder player(User player) {
			this.user2 = player;
			return this;
		}

		/**
		 * Sets the reason for island creation. Only CREATE or RESET are allowed.
		 * 
		 * @param reason
		 *            reason, can only be {@link Reason#CREATE} or {@link Reason#RESET}.
		 */
		public Builder reason(Reason reason) {
			if (!reason.equals(Reason.CREATE) && !reason.equals(Reason.RESET)) {
				throw new IllegalArgumentException("Reason must be CREATE or RESET.");
			}
			this.reason2 = reason;
			return this;
		}

		/**
		 * Sets the game mode addon and its world.
		 */
		public Builder addon(GameModeAddon addon) {
			this.addon2 = addon;
			this.world2 = addon.getOverWorld();
			return this;
		}

		/**
		 * Indicates that no blocks should be pasted for the island.
		 */
		public Builder noPaste() {
			this.noPaste2 = true;
			return this;
		}

		/**
		 * Sets the name of the blueprint bundle to use for the island.
		 */
		public Builder name(String name) {
			this.name2 = name;
			return this;
		}

		/**
		 * Sets the location strategy for finding the next island location.
		 *
		 * @param strategy
		 *            - the location strategy to use
		 * @since 1.8.0
		 */
		public Builder locationStrategy(NewIslandLocationStrategy strategy) {
			this.locationStrategy2 = strategy;
			return this;
		}

		/**
		 * Enables force mode to bypass validation (for admin fake generate). When
		 * enabled, skips online check and hasIsland check.
		 *
		 * @return this builder
		 * @since 2.0.0
		 */
		public Builder force() {
			this.force2 = true;
			return this;
		}

		/**
		 * Builds the island.
		 *
		 * @return Island
		 * @throws IOException
		 *             if insufficient parameters (e.g., no user)
		 */
		public Island build() throws IOException {
			if (user2 != null) {
				NewIsland newIsland = new NewIsland(this);
				return newIsland.getIsland();
			}
			throw new IOException("Insufficient parameters. Must have a user!");
		}
	}

	/**
	 * Creates a new island for the user. Handles location finding, user cleanup,
	 * event firing, blueprint pasting, and logging.
	 * 
	 * @param oldIsland
	 *            old island that is being replaced, if any
	 * @throws IOException
	 *             if an island cannot be made. Message is the tag to show the user.
	 */
	public void newIsland(Island oldIsland) throws IOException {
		// Find the new island location, either reserved or next available
		Location next = checkReservedIsland();
		if (next == null) {
			next = this.makeNextIsland();
		}
		// Clean up user data before moving to new island
		cleanUpUser(next);
		// Fire event for plugins/addons to react or cancel
		IslandBaseEvent event = IslandEvent.builder().involvedPlayer(user.getUniqueId()).reason(reason).island(island)
				.location(island.getCenter())
				.blueprintBundle(plugin.getBlueprintsManager().getBlueprintBundles(addon).get(name))
				.oldIsland(oldIsland).build();
		if (event.getNewEvent().map(IslandBaseEvent::isCancelled).orElse(event.isCancelled())) {
			// Event was cancelled, abort creation
			return;
		}
		event = event.getNewEvent().orElse(event);
		// Get the new BlueprintBundle if it was changed by the event
		switch (reason) {
			case CREATE -> name = ((IslandCreateEvent) event).getBlueprintBundle().getUniqueId();
			case RESET -> {
				// No blueprint for RESET as new island is not created automatically.
				// Player has to create a new island manually.
			}
			default -> {
				// Do nothing for other cases
			}
		}
		// Set the player's primary island
		plugin.getIslands().setPrimaryIsland(user.getUniqueId(), island);
		// Run post-creation tasks after creating the island
		if (noPaste) {
			// If noPaste is true, skip blueprint paste and run post-creation immediately
			Bukkit.getRegionScheduler().run(plugin, island.getCenter(), t -> postCreationTask(oldIsland));
		} else {
			if (user.getWorld().equals(island.getWorld())) {
				// Determine if NMS (native Minecraft server) paste is needed based on player
				// state
				double dist = user.getLocation().distance(island.getCenter());
				boolean useNMS = (user.getPlayer() instanceof ConsoleCommandSender)
						|| !user.getWorld().equals(island.getWorld()) || (dist >= Bukkit.getViewDistance() * 16D);
				// Paste the blueprint, then run post-creation tasks
				plugin.getBlueprintsManager().paste(addon, island, name, () -> postCreationTask(oldIsland), useNMS);
			} else {
				plugin.getBlueprintsManager().paste(addon, island, name, () -> postCreationTask(oldIsland), true);
			}
		}
		// Set default island flags/settings
		island.setFlagsDefaults();
		// Register metrics for island creation
		plugin.getMetrics().ifPresent(BStats::increaseIslandsCreatedCount);
		// Add history record for island creation
		island.log(new LogEntry.Builder(LogType.JOINED).data(user.getUniqueId().toString(), "owner").build());
		// Save island to database
		IslandsManager.updateIsland(island);
	}

	/**
	 * Registers islands in associated worlds (Nether/End) if they exist and are
	 * enabled. This ensures the database has records for these islands immediately.
	 * Uses the same X/Z coordinates across all dimensions for consistency.
	 */
	private void registerAssociatedIslands() {
		// Nether - use same X/Z as Overworld island
		if (addon.getNetherWorld() != null && addon.getWorldSettings().isNetherIslands()) {
			Location netherLoc = new Location(addon.getNetherWorld(), island.getCenter().getX(),
					island.getCenter().getY(), island.getCenter().getZ());
			// Check if island already exists to avoid duplicates or overwrites
			if (!plugin.getIslands().isIslandAt(netherLoc)) {
				plugin.getIslands().createIsland(netherLoc, user.getUniqueId(), island.getProtectionRange());
			}
		}
		// End - use same X/Z as Overworld island (no offset needed - fake islands
		// already protect spawn)
		if (addon.getEndWorld() != null && addon.getWorldSettings().isEndIslands()) {
			// Use same X/Z coordinates as Overworld
			Location endLoc = new Location(addon.getEndWorld(), island.getCenter().getX(), island.getCenter().getY(),
					island.getCenter().getZ());

			// Check if island already exists
			if (!plugin.getIslands().isIslandAt(endLoc)) {
				plugin.getIslands().createIsland(endLoc, user.getUniqueId(), island.getProtectionRange());
			}
		}
	}

	/**
	 * Tasks to run after the new island has been created. Handles spawn point
	 * setup, player teleportation, and cleanup.
	 * 
	 * @param oldIsland
	 *            - old island that will be deleted
	 */
	private void postCreationTask(Island oldIsland) {
		// Register associated islands in other dimensions
		registerAssociatedIslands();

		// Set initial spawn point if one exists
		if (island.getSpawnPoint(Environment.NORMAL) != null) {
			plugin.getIslands().setHomeLocation(island, island.getSpawnPoint(Environment.NORMAL), "");
			plugin.getIslands().setPrimaryIsland(user.getUniqueId(), island);
		}
		// If player is online, handle teleportation and movement
		if (user.isOnline()) {
			if (reason.equals(Reason.RESET) || (reason.equals(Reason.CREATE)
					&& plugin.getIWM().isTeleportPlayerToIslandUponIslandCreation(world))) {
				// Stop the player from falling or moving
				user.getPlayer().setVelocity(new Vector(0, 0, 0));
				user.getPlayer().setFallDistance(0F);
				// Teleport player after island is built, then tidy up
				plugin.getIslands().homeTeleportAsync(island, user, true).thenRun(() -> tidyUp(oldIsland));
				return;
			} else {
				// Notify player they can teleport to their island
				user.sendMessage("commands.island.create.you-can-teleport-to-your-island");
			}
		} else {
			// If player is offline, remove player data to clear cache
			User.removePlayer(user.getPlayer());
		}
		tidyUp(oldIsland);
	}

	/**
	 * Cleans up a user before moving them to a new island. Resets deaths and checks
	 * range permissions.
	 * 
	 * @param loc
	 *            - the new island location
	 */
	private void cleanUpUser(Location loc) {
		// Reset deaths if configured
		if (plugin.getIWM().isDeathsResetOnNewIsland(world)) {
			plugin.getPlayers().setDeaths(world, user.getUniqueId(), 0);
		}
		// Set protection range based on user's permission, if different from default
		island.setProtectionRange(user.getPermissionValue(
				plugin.getIWM().getAddon(island.getWorld()).map(GameModeAddon::getPermissionPrefix).orElse("")
						+ "island.range",
				island.getProtectionRange()));
	}

	/**
	 * Finds the next available location for a new island and adds it to the grid.
	 * For End dimension, generates fake islands if needed, then finds location
	 * outside 500x500.
	 *
	 * @return location of new island
	 * @throws IOException
	 *             if no unoccupied spots or island cannot be added to grid
	 */
	private Location makeNextIsland() throws IOException {
		// Use default location strategy (skips 500x500 center area for dragon spawn)
		Location next = this.locationStrategy.getNextLocation(world, user);
		if (next == null) {
			// CRITICAL: No spot found - world may be full!
			long islandCount = plugin.getIslands().getIslandCount(world);
			plugin.logError("=================================================================");
			plugin.logError("CRITICAL: Island creation FAILED - no unoccupied spot found!");
			plugin.logError("Current island count: " + islandCount);
			plugin.logError("Recommendations:");
			plugin.logError("  1. Increase search radius in NewIsland.java (default: 2000)");
			plugin.logError("  2. Purge abandoned islands (inactive >180 days)");
			plugin.logError("  3. Reduce island protection range");
			plugin.logError("  4. Create new world with larger grid");
			plugin.logError("=================================================================");

			// Notify online admins
			for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
				if (player.hasPermission("acidisland.admin") || player.isOp()) {
					player.sendMessage(
							org.bukkit.ChatColor.RED + "⚠️ CRITICAL: Island creation failed! World may be full!");
					player.sendMessage(
							org.bukkit.ChatColor.RED + "⚠️ Check console for details. Island count: " + islandCount);
				}
			}

			if (plugin.getIWM().getAddon(world).map(GameModeAddon::isEnforceEqualRanges).orElse(true)) {
				plugin.logError("Failed to make island - no unoccupied spot found.");
				plugin.logError("If the world was imported, try multiple times until all unowned islands are known.");
			}
			throw new IOException("commands.island.create.cannot-create-island");
		}
		// Add island to grid
		island = plugin.getIslands().createIsland(next, user.getUniqueId());
		if (island == null) {
			if (plugin.getIWM().getAddon(world).map(GameModeAddon::isEnforceEqualRanges).orElse(true)) {
				plugin.logError("Failed to make island! Island could not be added to the grid.");
			}
			throw new IOException("commands.island.create.unable-create-island");
		}
		return next;
	}

	/**
	 * Checks if the user has a reserved island location. If so, clears the
	 * reservation and returns the location.
	 * 
	 * @return reserved island location, or null if none found
	 */
	private Location checkReservedIsland() {
		if (plugin.getIslands().hasIsland(world, user)) {
			// Island exists, just needs pasting
			island = plugin.getIslands().getIsland(world, user);
			if (island != null && island.isReserved()) {
				Location l = island.getCenter();
				// Clear the reservation
				island.setReserved(false);
				return l;
			}
		}
		return null;
	}

	/**
	 * Cleans up after island creation. Deletes old island and fires exit event.
	 *
	 * @param oldIsland
	 *            the old island to delete
	 */
	private void tidyUp(Island oldIsland) {
		// Delete old island if present
		// Use internal method directly since we're already in queue
		if (oldIsland != null) {
			plugin.getIslands().deleteIslandInternal(oldIsland, true, user.getUniqueId());
		}

		// Fire exit event for plugins/addons
		IslandEvent.builder().involvedPlayer(user.getUniqueId())
				.reason(reason == Reason.RESET ? Reason.RESETTED : Reason.CREATED).island(island)
				.location(island.getCenter()).oldIsland(oldIsland).build();

	}
}