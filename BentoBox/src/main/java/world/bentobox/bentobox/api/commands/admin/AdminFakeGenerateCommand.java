package world.bentobox.bentobox.api.commands.admin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.events.island.IslandEvent.Reason;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.island.NewIsland;

/**
 * Admin command to test island generation queue by simulating multiple island
 * creations. This helps test race conditions without needing multiple real
 * players.
 */
public class AdminFakeGenerateCommand extends CompositeCommand {

	public AdminFakeGenerateCommand(CompositeCommand parent) {
		super(parent, "fakegenerateisland", "fgi");
	}

	@Override
	public void setup() {
		setPermission("admin.fakegenerateisland");
		setOnlyPlayer(true);
		setParametersHelp("commands.admin.fakegenerateisland.parameters");
		setDescription("commands.admin.fakegenerateisland.description");
	}

	@Override
	public boolean canExecute(User user, String label, List<String> args) {
		if (args.isEmpty()) {
			user.sendMessage("commands.admin.fakegenerateisland.usage");
			return false;
		}

		try {
			int count = Integer.parseInt(args.get(0));
			if (count <= 0 || count > 100) {
				user.sendMessage("commands.admin.fakegenerateisland.invalid-count", "[count]", String.valueOf(count));
				return false;
			}
			return true;
		} catch (NumberFormatException e) {
			user.sendMessage("commands.admin.fakegenerateisland.invalid-count", "[count]", args.get(0));
			return false;
		}
	}

	@Override
	public boolean execute(User user, String label, List<String> args) {
		int count = Integer.parseInt(args.get(0));

		// Get the game mode addon for this world
		GameModeAddon addon = getAddon();
		if (addon == null) {
			user.sendMessage("general.errors.invalid-world");
			return false;
		}

		user.sendMessage("commands.admin.fakegenerateisland.starting", "[count]", String.valueOf(count), "[world]",
				addon.getOverWorld().getName());

		// Track how many tasks have been queued
		AtomicInteger queued = new AtomicInteger(0);
		AtomicInteger failed = new AtomicInteger(0);

		// Create multiple island generation tasks simultaneously
		// This simulates multiple players creating islands at the same time
		for (int i = 0; i < count; i++) {
			final int taskNum = i + 1;

			// Create a fake user UUID for each task
			UUID fakeUserUUID = UUID.randomUUID();

			// Schedule island creation on the next tick to simulate concurrent requests
			Bukkit.getGlobalRegionScheduler().runDelayed(BentoBox.getInstance(), t -> {
				try {
					// Create fake user (will be offline, but queue handles this)
					User fakeUser = User.getInstance(fakeUserUUID);

					// Build island creation request with FORCE flag to bypass validation
					// This allows fake island generation without real players
					NewIsland.builder().addon(addon).player(fakeUser).reason(Reason.CREATE).force() // Enable force mode
																									// for fake generate
							.build();

					queued.incrementAndGet();
				} catch (Exception e) {
					failed.incrementAndGet();
					BentoBox.getInstance().logError("FakeGenerate task " + taskNum + " failed: " + e.getMessage());
				}
			}, (long) i + 1); // Stagger each task by 1 tick to simulate real concurrent requests

			// Log progress every 10 tasks
			if (taskNum % 10 == 0) {
				BentoBox.getInstance().log("FakeGenerate: Queued " + taskNum + "/" + count + " island creations...");
			}
		}

		// Log completion after all tasks should have been processed (count ticks +
		// queue processing time)
		long completionDelay = count + 30L; // Add buffer for queue processing
		Bukkit.getGlobalRegionScheduler().runDelayed(BentoBox.getInstance(), t -> {
			BentoBox.getInstance().log("FakeGenerate: Completed! Queued " + queued.get() + "/" + count + " islands ("
					+ failed.get() + " failed)");
		}, completionDelay);

		return true;
	}
}
