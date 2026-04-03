package world.bentobox.level.calculators;

import java.text.ParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Pair;
import world.bentobox.bentobox.util.Util;
import world.bentobox.level.Level;
import world.bentobox.level.calculators.Results.Result;

public class IslandLevelCalculator {
	private final UUID calcId = UUID.randomUUID();
	private static final String LINE_BREAK = "==================================";
	public static final long MAX_AMOUNT = 10000000;
	private final Level addon;
	private final Queue<Pair<Integer, Integer>> chunksToCheck;
	private final Island island;
	private final CompletableFuture<Results> r;
	private final Results results;
	private long duration;
	private final boolean zeroIsland;
	private final Map<Environment, World> worlds = new EnumMap<>(Environment.class);
	private final int seaHeight;
	private final Semaphore cpuLimit;
	private final Map<Material, Integer> limitCount = new ConcurrentHashMap<>();
	private final boolean[] isLimited;
	private final Material[] allMaterials;

	record ChunkCoord(World world, int x, int z) {}
	record ChunkPair(World world, ChunkCoord coord, ChunkSnapshot chunkSnapshot) {}

	public IslandLevelCalculator(Level addon, Island island, CompletableFuture<Results> r, boolean zeroIsland) {
		this.addon = addon;
		this.island = island;
		this.r = r;
		this.zeroIsland = zeroIsland;
		
		int availableCores = Runtime.getRuntime().availableProcessors();
		int threadLimit = Math.max(1, availableCores / 2);
		this.cpuLimit = new Semaphore(threadLimit);
		
		this.allMaterials = Material.values();
		this.isLimited = new boolean[allMaterials.length];
		for (Material m : allMaterials) {
			if (addon.getBlockConfig().getLimit(m) != null) {
				isLimited[m.ordinal()] = true;
			}
		}

		results = new Results();
		duration = System.currentTimeMillis();
		chunksToCheck = getChunksToScan(island);
		results.setInitialCount(addon.getInitialIslandCount(island));
		
		// Setup worlds
		worlds.put(Environment.NORMAL, Util.getWorld(island.getWorld()));
		if (addon.getSettings().isNether()) {
			World nether = addon.getPlugin().getIWM().getNetherWorld(island.getWorld());
			if (nether != null) worlds.put(Environment.NETHER, nether);
		}
		if (addon.getSettings().isEnd()) {
			World end = addon.getPlugin().getIWM().getEndWorld(island.getWorld());
			if (end != null) worlds.put(Environment.THE_END, end);
		}
		
		StringBuilder worldList = new StringBuilder();
		worlds.values().forEach(w -> worldList.append(w.getName()).append(" "));
		BentoBox.getInstance().log("Level detection initialized for " + island.getOwner() + ". Cores: " + availableCores + ". Threads: " + threadLimit + ". Worlds: [ " + worldList.toString().trim() + " ]");
		
		seaHeight = addon.getPlugin().getIWM().getSeaHeight(island.getWorld());
	}

	private Queue<Pair<Integer, Integer>> getChunksToScan(Island island) {
		Queue<Pair<Integer, Integer>> chunkQueue = new ConcurrentLinkedQueue<>();
		for (int x = island.getMinProtectedX(); x < (island.getMinProtectedX() + island.getProtectionRange() * 2 + 16); x += 16) {
			for (int z = island.getMinProtectedZ(); z < (island.getMinProtectedZ() + island.getProtectionRange() * 2 + 16); z += 16) {
				chunkQueue.add(new Pair<>(x >> 4, z >> 4));
			}
		}
		return chunkQueue;
	}

	public void scanIsland(Pipeliner pipeliner) {
		List<CompletableFuture<Void>> allChunkFutures = new ArrayList<>();
		ExecutorService vThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
		
		while (!chunksToCheck.isEmpty()) {
			Pair<Integer, Integer> p = chunksToCheck.poll();
			for (World world : worlds.values()) {
				allChunkFutures.add(Util.getChunkAtAsync(world, p.x, p.z, true).thenAcceptAsync(chunk -> {
					if (chunk == null) return;
					try {
						cpuLimit.acquire();
						scanAsync(new ChunkPair(world, new ChunkCoord(world, chunk.getX(), chunk.getZ()), chunk.getChunkSnapshot()));
					} catch (InterruptedException e) { Thread.currentThread().interrupt(); }
					finally { cpuLimit.release(); }
				}, vThreadExecutor));
			}
		}

		CompletableFuture.allOf(allChunkFutures.toArray(new CompletableFuture[0])).thenRun(() -> {
			pipeliner.getInProcessQueue().remove(this);
			BentoBox.getInstance().log("Completed Level scan for " + island.getOwner() + " in " + (System.currentTimeMillis() - duration) + "ms.");
			this.tidyUp();
			this.getR().complete(getResults());
			vThreadExecutor.shutdown();
		}).exceptionally(ex -> {
			pipeliner.getInProcessQueue().remove(this);
			getR().completeExceptionally(ex);
			vThreadExecutor.shutdown();
			return null;
		});
	}

	private void scanAsync(ChunkPair cp) {
		int chunkX = cp.coord().x() << 4; int chunkZ = cp.coord().z() << 4;
		int minX = island.getMinProtectedX(); int maxX = island.getMaxProtectedX();
		int minZ = island.getMinProtectedZ(); int maxZ = island.getMaxProtectedZ();
		
		final AtomicLong localRawPoints = new AtomicLong(0);
		final AtomicLong localWaterPoints = new AtomicLong(0);
		final long[] localMd = new long[allMaterials.length];
		final long[] localUw = new long[allMaterials.length];
		final long[] localLimitCounts = new long[allMaterials.length];

		int minHeight = cp.world.getMinHeight();
		int maxHeight = cp.world.getMaxHeight();
		int splitY = Math.max(minHeight, Math.min(maxHeight, seaHeight));

		for (int cy = minHeight >> 4; cy < maxHeight >> 4; cy++) {
			if (cp.chunkSnapshot.isSectionEmpty(cy - (minHeight >> 4))) continue;
			int minY = cy << 4; int maxY = minY + 16;
			for (int x = 0; x < 16; x++) {
				int globalX = chunkX + x;
				if (globalX < minX || globalX >= maxX) continue;
				for (int z = 0; z < 16; z++) {
					int globalZ = chunkZ + z;
					if (globalZ < minZ || globalZ >= maxZ) continue;
					
					// 1. UNDERWATER
					int yLimit = Math.min(maxY, splitY + 1);
					for (int y = Math.max(minY, minHeight); y < yLimit; y++) {
						Material m = cp.chunkSnapshot.getBlockType(x, y, z);
						if (m.isAir() || m == Material.WATER) continue;
						int id = m.ordinal();
						if (isLimited[id]) localLimitCounts[id]++;
						else { localWaterPoints.addAndGet(getValue(m)); localUw[id]++; }
					}
					// 2. ABOVE WATER
					for (int y = Math.max(minY, splitY + 1); y < maxY; y++) {
						Material m = cp.chunkSnapshot.getBlockType(x, y, z);
						if (m.isAir()) continue;
						int id = m.ordinal();
						if (isLimited[id]) localLimitCounts[id]++;
						else { localRawPoints.addAndGet(getValue(m)); localMd[id]++; }
					}
				}
			}
		}

		for (int id = 0; id < isLimited.length; id++) {
			if (localLimitCounts[id] > 0) {
				Material m = allMaterials[id];
				int limit = addon.getBlockConfig().getLimit(m);
				int val = getValue(m);
				for (int i = 0; i < localLimitCounts[id]; i++) {
					int globalCount = limitCount.compute(m, (k, v) -> (v == null) ? 1 : v + 1);
					if (globalCount <= limit) { localRawPoints.addAndGet(val); localMd[id]++; }
					else { results.ofCount.add(m); }
				}
			}
		}

		if (localRawPoints.get() > 0) results.rawBlockCount.addAndGet(localRawPoints.get());
		if (localWaterPoints.get() > 0) results.underWaterBlockCount.addAndGet(localWaterPoints.get());
		for (int i = 0; i < allMaterials.length; i++) {
			if (localMd[i] > 0) results.mdCount.add(allMaterials[i], (int)localMd[i]);
			if (localUw[i] > 0) results.uwCount.add(allMaterials[i], (int)localUw[i]);
		}
	}

	private int getValue(Material m) {
		Integer value = addon.getBlockConfig().getValue(island.getWorld(), m);
		if (value == null) { results.ncCount.add(m); return 0; }
		return value;
	}

	public void tidyUp() {
		results.rawBlockCount.addAndGet((long) (results.underWaterBlockCount.get() * addon.getSettings().getUnderWaterMultiplier()));
		if (this.addon.getSettings().isSumTeamDeaths()) {
			for (UUID uuid : this.island.getMemberSet()) this.results.deathHandicap.addAndGet(this.addon.getPlayers().getDeaths(island.getWorld(), uuid));
		} else {
			this.results.deathHandicap.set(this.island.getOwner() == null ? 0 : this.addon.getPlayers().getDeaths(island.getWorld(), this.island.getOwner()));
		}
		long blockAndDeathPoints = this.results.rawBlockCount.get();
		this.results.totalPoints.set(blockAndDeathPoints);
		if (this.addon.getSettings().getDeathPenalty() > 0) blockAndDeathPoints -= this.results.deathHandicap.get() * this.addon.getSettings().getDeathPenalty();
		this.results.level.set(calculateLevel(blockAndDeathPoints));
		long currentLevel = this.results.level.get();
		long pointsNeeded = 0;
		if (addon.getSettings().getLevelCalc().equalsIgnoreCase("blocks / level_cost")) {
			pointsNeeded = ((currentLevel + 1) * addon.getSettings().getLevelCost()) - blockAndDeathPoints;
		} else {
			long nextLevel = currentLevel; long blocks = blockAndDeathPoints;
			while (nextLevel < currentLevel + 1 && blocks - blockAndDeathPoints < 1000000) nextLevel = calculateLevel(++blocks);
			pointsNeeded = blocks - blockAndDeathPoints;
		}
		this.results.pointsToNextLevel.set(Math.max(0, pointsNeeded));
		results.report = getReport();
		addon.getPipeliner().setTime(System.currentTimeMillis() - duration);
	}

	private List<String> getReport() {
		List<String> reportLines = new ArrayList<>();
		reportLines.add("Level Log for island in " + addon.getPlugin().getIWM().getFriendlyName(island.getWorld()) + " at " + Util.xyz(island.getCenter().toVector()));
		reportLines.add("Total block value count = " + String.format("%,d", results.rawBlockCount.get()));
		reportLines.add("New level = " + results.getLevel());
		return reportLines;
	}

	private Collection<String> sortedReport(int total, Multiset<Object> uwCount) {
		Collection<String> result = new ArrayList<>();
		Multisets.copyHighestCountFirst(uwCount).entrySet().forEach(en -> {
			if (en.getElement() instanceof Material md) {
				int value = Objects.requireNonNullElse(addon.getBlockConfig().getValue(island.getWorld(), md), 0);
				result.add(Util.prettifyText(md.name()) + ": " + String.format("%,d", en.getCount()) + " blocks x " + value);
			}
		});
		return result;
	}

	private long calculateLevel(long points) {
		try {
			String formula = addon.getSettings().getLevelCalc().replace("blocks", String.valueOf(points)).replace("level_cost", String.valueOf(addon.getSettings().getLevelCost()));
			return (long) EquationEvaluator.eval(formula);
		} catch (Exception e) { return 0; }
	}

	public boolean isNotZeroIsland() { return !zeroIsland; }
	public Island getIsland() { return island; }
	public CompletableFuture<Results> getR() { return r; }
	public Results getResults() { return results; }
	@Override public boolean equals(Object o) { if (this == o) return true; if (!(o instanceof IslandLevelCalculator that)) return false; return calcId.equals(that.calcId); }
	@Override public int hashCode() { return calcId.hashCode(); }
}
