package world.bentobox.level.commands;

import java.util.List;
import java.util.UUID;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.level.Level;
import world.bentobox.level.calculators.Results;
import world.bentobox.level.calculators.Results.Result;

public class IslandLevelCommand extends CompositeCommand {

	private static final String ISLAND_LEVEL_IS = "island.level.island-level-is";
	private static final String LEVEL = "[level]";
	private final Level addon;

	public IslandLevelCommand(Level addon, CompositeCommand parent) {
		super(parent, "level");
		this.addon = addon;
	}

	@Override
	public void setup() {
		this.setPermission("island.level");
		this.setParametersHelp("island.level.parameters");
		this.setDescription("island.level.description");
	}

	@Override
	public boolean execute(User user, String label, List<String> args) {
		if (!args.isEmpty()) {
			// Asking for another player's level?
			// Convert name to a UUID
			final UUID playerUUID = getPlugin().getPlayers().getUUID(args.get(0));
			if (playerUUID == null) {
				user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.get(0));
				return false;
			}
			// Ops, console and admin perms can request and calculate other player levels
			if (!user.isPlayer() || user.isOp() || user.hasPermission(this.getPermissionPrefix() + "admin.level")) {
				return scanIsland(user, playerUUID);
			}
			// Request for another player's island level
			if (!user.getUniqueId().equals(playerUUID)) {
				// Find their island globally
				List<Island> islands = getIslands().getIslands(playerUUID);
				Island targetIsland = islands.stream()
						.filter(i -> addon.isRegisteredGameModeWorld(i.getWorld()))
						.findFirst()
						.orElse(null);
				
				if (targetIsland == null) {
					user.sendMessage("general.errors.player-has-no-island");
					return false;
				}
				
				user.sendMessage(ISLAND_LEVEL_IS, LEVEL,
						addon.getManager().getIslandLevelString(targetIsland.getWorld(), playerUUID));
				return true;
			}
		}
		if (!user.isPlayer()) {
			user.sendMessage("general.errors.use-in-game");
			return false;
		}
		// Self request
		// Check player cooldown
		int coolDown = this.addon.getSettings().getLevelWait();

		if (coolDown > 0) {
			// Check cool down
			if (checkCooldown(user))
				return false;
			// Set cool down
			setCooldown(user.getUniqueId(), coolDown);
		}

		// Self level request
		return scanIsland(user, user.getUniqueId());

	}

	private boolean scanIsland(User user, UUID playerUUID) {
		// Find the island globally if not in current world
		Island islandResult = getIslands().getIsland(user.getWorld(), playerUUID);
		if (islandResult == null) {
			List<Island> islands = getIslands().getIslands(playerUUID);
			islandResult = islands.stream()
					.filter(i -> addon.isRegisteredGameModeWorld(i.getWorld()))
					.findFirst()
					.orElse(null);
		}

		if (islandResult == null) {
			user.sendMessage("general.errors.player-has-no-island");
			return false;
		}
		
		final Island island = islandResult;
		
		// 1. FIRST: Check for ownership if it's a self-request (not op/admin)
		if (user.isPlayer() && user.getUniqueId().equals(playerUUID) && !user.isOp() 
				&& !user.hasPermission(this.getPermissionPrefix() + "admin.level")) {
			if (island.getRank(user.getUniqueId()) < RanksManager.OWNER_RANK) {
				user.sendMessage("stone-generator.messages.owner-only");
				return false;
			}
		}

		// 2. SECOND: Synchronously check if already in queue to prevent double messaging
		if (addon.getPipeliner().isInQueue(island)) {
			user.sendMessage("island.level.in-progress");
			return true;
		}

		// 3. THIRD: Start calculation
		addon.getManager().calculateLevel(playerUUID, island).thenAccept(results -> {
			if (results == null)
				return; // island was deleted or become unowned
			
			if (results.getState().equals(Result.IN_PROGRESS)) {
				// This shouldn't really happen now with the sync check above, but keep for safety
				user.sendMessage("island.level.in-progress");
				return;
			} else if (results.getState().equals(Result.TIMEOUT)) {
				user.sendMessage("island.level.time-out");
				return;
			}
			
			showResult(user, playerUUID, island, -1L, results);
		});

		// Inform user that it's queued (Only if it wasn't already in progress)
		int inQueue = addon.getPipeliner().getIslandsInQueue();
		user.sendMessage("island.level.calculating");
		user.sendMessage("island.level.estimated-wait", TextVariables.NUMBER,
				String.valueOf(addon.getPipeliner().getTime() * (inQueue + 1)));
		if (inQueue > 1) {
			user.sendMessage("island.level.in-queue", TextVariables.NUMBER, String.valueOf(inQueue + 1));
		}
		
		return true;
	}
private void showResult(User user, UUID playerUUID, Island island, Long oldLevel, Results results) {
	if (user.isPlayer()) {
		user.sendMessage(ISLAND_LEVEL_IS, LEVEL, addon.getManager().getIslandLevelString(island.getWorld(), playerUUID));
		// Player
		if (addon.getSettings().getDeathPenalty() != 0) {
			user.sendMessage("island.level.deaths", "[number]", String.valueOf(results.getDeathHandicap()));
		}
		// Send player how many points are required to reach next island level
		if (results.getPointsToNextLevel() >= 0) {
			user.sendMessage("island.level.required-points-to-next-level", "[points]",
					String.valueOf(results.getPointsToNextLevel()), "[progress]",
					String.valueOf(this.addon.getSettings().getLevelCost() - results.getPointsToNextLevel()),
					"[levelcost]", String.valueOf(this.addon.getSettings().getLevelCost()));
		}
		// Tell other team members
		if (oldLevel != null && oldLevel != -1 && results.getLevel() != oldLevel) {
			island.getMemberSet().stream().filter(u -> !u.equals(user.getUniqueId()))
					.forEach(m -> User.getInstance(m).sendMessage(ISLAND_LEVEL_IS, LEVEL,
							addon.getManager().getIslandLevelString(island.getWorld(), playerUUID)));
		}
	} else if (this.addon.getSettings().isLogReportToConsole()) {
		results.getReport().forEach(BentoBox.getInstance()::log);
	}

}

}
