package world.bentobox.bentobox.nms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.material.Colorable;
import org.bukkit.util.BoundingBox;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import net.kyori.adventure.text.Component;
import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.hooks.Hook;
import world.bentobox.bentobox.database.objects.IslandDeletion;
import world.bentobox.bentobox.hooks.FancyNpcsHook;
import world.bentobox.bentobox.hooks.ItemsAdderHook;
import world.bentobox.bentobox.hooks.OraxenHook;
import world.bentobox.bentobox.hooks.SlimefunHook;
import world.bentobox.bentobox.hooks.ZNPCsPlusHook;
import world.bentobox.bentobox.util.MyBiomeGrid;
import world.bentobox.bentobox.util.Util;

/**
 * Regenerates by using a seed world. The seed world is created using the same
 * generator as the game world so that features created by methods like
 * generateNoise or generateCaves can be regenerated.
 * 
 * @author tastybento
 *
 */
public abstract class CopyWorldRegenerator implements WorldRegenerator {

	private final BentoBox plugin;
	private final Optional<FancyNpcsHook> npc;
	private final Optional<ZNPCsPlusHook> znpc;

	protected CopyWorldRegenerator() {
		this.plugin = BentoBox.getInstance();
		// Fancy NPCs Hook
		npc = plugin.getHooks().getHook("FancyNpcs").filter(FancyNpcsHook.class::isInstance)
				.map(FancyNpcsHook.class::cast);
		// ZNPCs Plus Hook
		znpc = plugin.getHooks().getHook("ZNPCsPlus").filter(ZNPCsPlusHook.class::isInstance)
				.map(ZNPCsPlusHook.class::cast);

	}

	/**
	 * Update the low-level chunk information for the given block to the new block
	 * ID and data. This change will not be propagated to clients until the chunk is
	 * refreshed to them.
	 *
	 * @param chunk
	 *            - chunk to be changed
	 * @param x
	 *            - x coordinate within chunk 0 - 15
	 * @param y
	 *            - y coordinate within chunk 0 - world height, e.g. 255
	 * @param z
	 *            - z coordinate within chunk 0 - 15
	 * @param blockData
	 *            - block data to set the block
	 * @param applyPhysics
	 *            - apply physics or not
	 */
	protected abstract void setBlockInNativeChunk(Chunk chunk, int x, int y, int z, BlockData blockData,
			boolean applyPhysics);

	@Override
	public CompletableFuture<Void> regenerate(GameModeAddon gm, IslandDeletion di, World world) {
		return gm.isUsesNewChunkGeneration() ? regenerateCopy(gm, di, world) : regenerateSimple(gm, di, world);
	}

	public CompletableFuture<Void> regenerateCopy(GameModeAddon gm, IslandDeletion di, World world) {
		CompletableFuture<Void> bigFuture = new CompletableFuture<>();
		final int[] chunkCoords = {di.getMinXChunk(), di.getMinZChunk()};
		final CompletableFuture<Void>[] currentTask = new CompletableFuture[]{CompletableFuture.completedFuture(null)};

		final io.papermc.paper.threadedregions.scheduler.ScheduledTask[] taskWrapper = new io.papermc.paper.threadedregions.scheduler.ScheduledTask[1];
		taskWrapper[0] = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
			if (!currentTask[0].isDone())
				return;
			if (chunkCoords[0] > di.getMaxXChunk()) {
				taskWrapper[0].cancel();
				bigFuture.complete(null);
				return;
			}
			List<CompletableFuture<Void>> newTasks = new ArrayList<>();
			for (int i = 0; i < plugin.getSettings().getDeleteSpeed(); i++) {
				if (chunkCoords[0] > di.getMaxXChunk()) {
					break;
				}
				final int x = chunkCoords[0];
				final int z = chunkCoords[1];
				// Only add chunks that are generated
				// if (world.getChunkAt(x, z, false).isGenerated()) {
				newTasks.add(regenerateChunk(di, world, x, z));
				// }
				chunkCoords[1]++;
				if (chunkCoords[1] > di.getMaxZChunk()) {
					chunkCoords[1] = di.getMinZChunk();
					chunkCoords[0]++;
				}
			}
			currentTask[0] = CompletableFuture.allOf(newTasks.toArray(new CompletableFuture[0]));
		}, 0L, 20L);
		return bigFuture;
	}

	@Override
	public CompletableFuture<Void> regenerateChunk(Chunk chunk) {
		return regenerateChunk(null, chunk.getWorld(), chunk.getX(), chunk.getZ());
	}

	private CompletableFuture<Void> regenerateChunk(@Nullable IslandDeletion di, @NonNull World world, int chunkX,
			int chunkZ) {

		// Async check for chunk generation
		return Util.getChunkAtAsync(world, chunkX, chunkZ, false).thenCompose(chunk -> {
			if (chunk == null) {
				return CompletableFuture.completedFuture(null);
			}

			CompletableFuture<Chunk> seedWorldFuture = getSeedWorldChunk(world, chunkX, chunkZ);
			CompletableFuture<Chunk> chunkFuture = CompletableFuture.completedFuture(chunk);

			CompletableFuture<Void> cleanFuture = di != null
					? cleanChunk(chunkFuture, di)
					: CompletableFuture.completedFuture(null);

			return CompletableFuture.allOf(cleanFuture, seedWorldFuture).thenCompose(v -> {
				try {
					Chunk seedChunk = seedWorldFuture.get();
					if (seedChunk == null)
						return CompletableFuture.completedFuture(null);

					CompletableFuture<Void> copyTask = new CompletableFuture<>();
					// Get snapshot on seed thread
					Bukkit.getRegionScheduler().run(plugin, seedChunk.getBlock(8, 64, 8).getLocation(), t -> {
						ChunkSnapshot snapshot = seedChunk.getChunkSnapshot(true, true, true);

						// Write on target thread
						Bukkit.getRegionScheduler().run(plugin, new Location(world, chunkX << 4, 64, chunkZ << 4),
								t2 -> {
									copySnapshotToChunk(chunk, snapshot, di != null ? di.getBox() : null);
									copyTask.complete(null);
								});
					});
					return copyTask;
				} catch (Exception e) {
					e.printStackTrace();
					return CompletableFuture.completedFuture(null);
				}
			});
		});
	}

	private void copySnapshotToChunk(Chunk toChunk, ChunkSnapshot snapshot, BoundingBox limitBox) {
		double baseX = toChunk.getX() << 4;
		double baseZ = toChunk.getZ() << 4;
		int minHeight = toChunk.getWorld().getMinHeight();
		int maxHeight = toChunk.getWorld().getMaxHeight();
		Optional<SlimefunHook> slimefunHook = plugin.getHooks().getHook("Slimefun").map(SlimefunHook.class::cast);
		Optional<Hook> oraxenHook = plugin.getHooks().getHook("Oraxen");

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				if (limitBox != null && !limitBox.contains(baseX + x, 0, baseZ + z)) {
					continue;
				}
				for (int y = minHeight; y < maxHeight; y++) {
					setBlockInNativeChunk(toChunk, x, y, z, snapshot.getBlockData(x, y, z), false);
					if (x % 4 == 0 && y % 4 == 0 && z % 4 == 0) {
						try {
							toChunk.getBlock(x, y, z).setBiome(snapshot.getBiome(x, y, z));
						} catch (Exception ignored) {
						}
					}
					Location loc = new Location(toChunk.getWorld(), baseX + x, y, baseZ + z);
					slimefunHook.ifPresent(hook -> hook.clearBlockInfo(loc, true));
					oraxenHook.ifPresent(h -> OraxenHook.clearBlockInfo(loc));
				}
			}
		}
		Optional<ItemsAdderHook> itemsAdderHook = plugin.getHooks().getHook("ItemsAdder")
				.map(ItemsAdderHook.class::cast);
		itemsAdderHook.ifPresent(hook -> ItemsAdderHook.deleteAllCustomBlocksInChunk(toChunk));
	}

	private CompletableFuture<Chunk> getSeedWorldChunk(World world, int chunkX, int chunkZ) {
		World seed = Bukkit.getWorld(world.getName() + "/bentobox");
		if (seed == null)
			return CompletableFuture.completedFuture(null);
		return Util.getChunkAtAsync(seed, chunkX, chunkZ);
	}

	/**
	 * Cleans up the chunk of inventories and entities
	 * 
	 * @param chunkFuture
	 *            the future chunk to be cleaned
	 * @param di
	 *            island deletion data
	 * @return future completion of this task
	 */
	private CompletableFuture<Void> cleanChunk(CompletableFuture<Chunk> chunkFuture, IslandDeletion di) {
		// when it is complete, then run through all the tile entities in the chunk and
		// clear them, e.g., chests are emptied
		CompletableFuture<Void> invFuture = chunkFuture
				.thenAccept(chunk -> Bukkit.getRegionScheduler().run(plugin, chunk.getBlock(0, 0, 0).getLocation(),
						t -> Arrays.stream(chunk.getTileEntities()).filter(InventoryHolder.class::isInstance)
								.filter(te -> di.inBounds(te.getLocation().getBlockX(), te.getLocation().getBlockZ()))
								.forEach(te -> ((InventoryHolder) te).getInventory().clear())));

		// Similarly, when the chunk is loaded, remove all the entities in the chunk
		// apart from players
		CompletableFuture<Void> entitiesFuture = chunkFuture.thenAccept(chunk -> {
			Bukkit.getRegionScheduler().run(plugin, chunk.getBlock(0, 0, 0).getLocation(), t -> {
				// Remove all entities in chunk, including any dropped items as a result of
				// clearing the blocks above
				Arrays.stream(chunk.getEntities())
						.filter(e -> !(e instanceof Player)
								&& di.inBounds(e.getLocation().getBlockX(), e.getLocation().getBlockZ()))
						.forEach(Entity::remove);
				// Remove any NPCs
				// Fancy NPCs Hook
				npc.ifPresent(hook -> hook.removeNPCsInChunk(chunk));
				// ZNPCs Plus Hook
				znpc.ifPresent(hook -> hook.removeNPCsInChunk(chunk));
			});
		});

		return CompletableFuture.allOf(invFuture, entitiesFuture);
	}

	/**
	 * Copies a chunk to another chunk
	 * 
	 * @param toChunk
	 *            - chunk to be copied into
	 * @param fromChunk
	 *            - chunk to be copied from
	 * @param limitBox
	 *            - limit box that the chunk needs to be in
	 */
	private void copyChunkDataToChunk(Chunk toChunk, Chunk fromChunk, BoundingBox limitBox) {
		double baseX = toChunk.getX() << 4;
		double baseZ = toChunk.getZ() << 4;
		int minHeight = toChunk.getWorld().getMinHeight();
		int maxHeight = toChunk.getWorld().getMaxHeight();
		Optional<SlimefunHook> slimefunHook = plugin.getHooks().getHook("Slimefun").map(SlimefunHook.class::cast);
		Optional<ItemsAdderHook> itemsAdderHook = plugin.getHooks().getHook("ItemsAdder")
				.map(ItemsAdderHook.class::cast);
		Optional<Hook> oraxenHook = plugin.getHooks().getHook("Oraxen");
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				if (limitBox != null && !limitBox.contains(baseX + x, 0, baseZ + z)) {
					continue;
				}
				for (int y = minHeight; y < maxHeight; y++) {
					setBlockInNativeChunk(toChunk, x, y, z, fromChunk.getBlock(x, y, z).getBlockData(), false);
					// 3D biomes, 4 blocks separated
					if (x % 4 == 0 && y % 4 == 0 && z % 4 == 0) {
						toChunk.getBlock(x, y, z).setBiome(fromChunk.getBlock(x, y, z).getBiome());
					}
					// Delete any 3rd party blocks
					Location loc = new Location(toChunk.getWorld(), baseX + x, y, baseZ + z);
					slimefunHook.ifPresent(hook -> hook.clearBlockInfo(loc, true));
					// Oraxen
					oraxenHook.ifPresent(h -> OraxenHook.clearBlockInfo(loc));
				}
			}
		}
		// Items Adder
		itemsAdderHook.ifPresent(hook -> ItemsAdderHook.deleteAllCustomBlocksInChunk(toChunk));

		// Entities
		Arrays.stream(fromChunk.getEntities())
				.forEach(e -> processEntity(e, e.getLocation().toVector().toLocation(toChunk.getWorld())));

		// Tile Entities
		Arrays.stream(fromChunk.getTileEntities()).forEach(bs -> processTileEntity(bs.getBlock(),
				bs.getLocation().toVector().toLocation(toChunk.getWorld()).getBlock()));
	}

	@SuppressWarnings("deprecation")
	private void processEntity(Entity entity, Location location) {
		Entity bpe = location.getWorld().spawnEntity(location, entity.getType());
		bpe.setCustomName(entity.getCustomName());
		if (entity instanceof Villager villager && bpe instanceof Villager villager2) {
			setVillager(villager, villager2);
		}
		if (entity instanceof Colorable c && bpe instanceof Colorable cc) {
			if (c.getColor() != null) {
				cc.setColor(c.getColor());
			}
		}
		if (entity instanceof Tameable t && bpe instanceof Tameable tt) {
			tt.setTamed(t.isTamed());
		}
		if (entity instanceof ChestedHorse ch && bpe instanceof ChestedHorse ch2) {
			ch2.setCarryingChest(ch.isCarryingChest());
		}
		// Only set if child. Most animals are adults
		if (entity instanceof Ageable a && bpe instanceof Ageable aa) {
			if (a.isAdult())
				aa.setAdult();
		}
		if (entity instanceof AbstractHorse horse && bpe instanceof AbstractHorse horse2) {
			horse2.setDomestication(horse.getDomestication());
			horse2.getInventory().setContents(horse.getInventory().getContents());
		}

		if (entity instanceof Horse horse && bpe instanceof Horse horse2) {
			horse2.setStyle(horse.getStyle());
		}
	}

	/**
	 * Set the villager stats
	 * 
	 * @param v
	 *            - villager
	 * @param villager2
	 *            villager
	 */
	private void setVillager(Villager v, Villager villager2) {
		villager2.setVillagerExperience(v.getVillagerExperience());
		villager2.setVillagerLevel(v.getVillagerLevel());
		villager2.setProfession(v.getProfession());
		villager2.setVillagerType(v.getVillagerType());
	}

	private void processTileEntity(Block fromBlock, Block toBlock) {
		// Block state
		BlockState blockState = fromBlock.getState();
		BlockState b = toBlock.getState();

		// Signs
		switch (blockState) {
			case Sign fromSign when b instanceof Sign toSign -> {
				for (Side side : Side.values()) {
					writeSign(fromSign, toSign, side);
				}
			}
			// Chests
			case InventoryHolder ih when b instanceof InventoryHolder toChest ->
				toChest.getInventory().setContents(ih.getInventory().getContents());

			// Spawner type
			case CreatureSpawner spawner when b instanceof CreatureSpawner toSpawner ->
				toSpawner.setSpawnedType(spawner.getSpawnedType());

			// Banners
			case Banner banner when b instanceof Banner toBanner -> {
				toBanner.setBaseColor(banner.getBaseColor());
				toBanner.setPatterns(banner.getPatterns());
			}
			default -> {
			}
		}
	}

	private void writeSign(Sign fromSign, Sign toSign, Side side) {
		SignSide fromSide = fromSign.getSide(side);
		SignSide toSide = toSign.getSide(side);
		int i = 0;

		for (Component line : fromSide.lines()) {
			toSide.line(i++, line);
		}
		toSide.setGlowingText(fromSide.isGlowingText());
	}

	public CompletableFuture<Void> regenerateSimple(GameModeAddon gm, IslandDeletion di, World world) {
		CompletableFuture<Void> bigFuture = new CompletableFuture<>();
		if (world == null) {
			bigFuture.complete(null);
			return bigFuture;
		}
		final int[] chunkCoords = {di.getMinXChunk(), di.getMinZChunk()};
		final CompletableFuture<Void>[] currentTask = new CompletableFuture[]{CompletableFuture.completedFuture(null)};

		final io.papermc.paper.threadedregions.scheduler.ScheduledTask[] taskWrapper = new io.papermc.paper.threadedregions.scheduler.ScheduledTask[1];
		taskWrapper[0] = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
			if (!currentTask[0].isDone())
				return;
			if (chunkCoords[0] > di.getMaxXChunk()) {
				taskWrapper[0].cancel();
				bigFuture.complete(null);
				return;
			}
			List<CompletableFuture<Void>> newTasks = new ArrayList<>();
			for (int i = 0; i < plugin.getSettings().getDeleteSpeed(); i++) {
				if (chunkCoords[0] > di.getMaxXChunk()) {
					break;
				}
				final int x = chunkCoords[0];
				final int z = chunkCoords[1];
				newTasks.add(regenerateChunk(gm, di, world, x, z));
				chunkCoords[1]++;
				if (chunkCoords[1] > di.getMaxZChunk()) {
					chunkCoords[1] = di.getMinZChunk();
					chunkCoords[0]++;
				}
			}
			currentTask[0] = CompletableFuture.allOf(newTasks.toArray(new CompletableFuture[0]));
		}, 1L, 20L);
		return bigFuture;
	}

	@SuppressWarnings("deprecation")
	private CompletableFuture<Void> regenerateChunk(GameModeAddon gm, IslandDeletion di, @Nonnull World world,
			int chunkX, int chunkZ) {
		CompletableFuture<Chunk> chunkFuture = Util.getChunkAtAsync(world, chunkX, chunkZ);
		CompletableFuture<Void> invFuture = chunkFuture
				.thenAccept(chunk -> Arrays.stream(chunk.getTileEntities()).filter(InventoryHolder.class::isInstance)
						.filter(te -> di.inBounds(te.getLocation().getBlockX(), te.getLocation().getBlockZ()))
						.forEach(te -> ((InventoryHolder) te).getInventory().clear()));
		CompletableFuture<Void> entitiesFuture = chunkFuture.thenAccept(chunk -> {
			Bukkit.getRegionScheduler().run(plugin, chunk.getBlock(0, 0, 0).getLocation(), t -> {
				for (Entity e : chunk.getEntities()) {
					if (!(e instanceof Player)) {
						e.remove();
					}
				}
			});
		});
		CompletableFuture<Chunk> copyFuture = chunkFuture.thenCompose(chunk -> {
			CompletableFuture<Chunk> future = new CompletableFuture<>();
			Bukkit.getRegionScheduler().run(plugin, chunk.getBlock(0, 0, 0).getLocation(), t -> {
				try {
					// Reset blocks
					MyBiomeGrid grid = new MyBiomeGrid(chunk.getWorld().getEnvironment());
					ChunkGenerator cg = gm.getDefaultWorldGenerator(chunk.getWorld().getName(), "delete");
					// Will be null if use-own-generator is set to true
					if (cg != null) {
						ChunkGenerator.ChunkData cd = cg.generateChunkData(chunk.getWorld(), new Random(), chunk.getX(),
								chunk.getZ(), grid);
						copyChunkDataToChunk(chunk, cd, grid, di.getBox());
					}
					future.complete(chunk);
				} catch (Exception e) {
					future.completeExceptionally(e);
				}
			});
			return future;
		});
		CompletableFuture<Void> postCopyFuture = copyFuture.thenAccept(chunk ->
		// Remove all entities in chunk, including any dropped items as a result of
		// clearing the blocks above
		Bukkit.getRegionScheduler().run(plugin, chunk.getBlock(0, 0, 0).getLocation(),
				t -> Arrays.stream(chunk.getEntities())
						.filter(e -> !(e instanceof Player)
								&& di.inBounds(e.getLocation().getBlockX(), e.getLocation().getBlockZ()))
						.forEach(Entity::remove)));
		return CompletableFuture.allOf(invFuture, entitiesFuture, postCopyFuture);
	}

	@SuppressWarnings("deprecation")
	private void copyChunkDataToChunk(Chunk chunk, ChunkGenerator.ChunkData chunkData,
			ChunkGenerator.BiomeGrid biomeGrid, BoundingBox limitBox) {
		double baseX = chunk.getX() << 4;
		double baseZ = chunk.getZ() << 4;
		int minHeight = chunk.getWorld().getMinHeight();
		int maxHeight = chunk.getWorld().getMaxHeight();
		Optional<Hook> slimefunHook = plugin.getHooks().getHook("Slimefun");
		Optional<Hook> oraxenHook = plugin.getHooks().getHook("Oraxen");
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				if (!limitBox.contains(baseX + x, 0, baseZ + z)) {
					continue;
				}
				for (int y = minHeight; y < maxHeight; y++) {
					setBlockInNativeChunk(chunk, x, y, z, chunkData.getBlockData(x, y, z), false);
					// 3D biomes, 4 blocks separated
					if (x % 4 == 0 && y % 4 == 0 && z % 4 == 0) {
						chunk.getBlock(x, y, z).setBiome(biomeGrid.getBiome(x, y, z));
					}
					// Delete any 3rd party blocks
					Location loc = new Location(chunk.getWorld(), baseX + x, y, baseZ + z);
					slimefunHook.ifPresent(sf -> ((SlimefunHook) sf).clearBlockInfo(loc, true));
					// Oraxen
					oraxenHook.ifPresent(h -> OraxenHook.clearBlockInfo(loc));
				}
			}
		}
		// Items Adder
		plugin.getHooks().getHook("ItemsAdder").ifPresent(hook -> ItemsAdderHook.deleteAllCustomBlocksInChunk(chunk));
	}
}
