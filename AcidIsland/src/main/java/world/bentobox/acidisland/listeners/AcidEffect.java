package world.bentobox.acidisland.listeners;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.earth2me.essentials.Essentials;

import io.canvasmc.canvas.event.EntityPortalAsyncEvent;
import io.canvasmc.canvas.event.EntityPostPortalAsyncEvent;

import world.bentobox.acidisland.AcidIsland;
import world.bentobox.acidisland.events.AcidEvent;
import world.bentobox.acidisland.events.AcidRainEvent;
import world.bentobox.acidisland.events.EntityDamageByAcidEvent;
import world.bentobox.acidisland.events.EntityDamageByAcidEvent.Acid;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;

/**
 * Applies the acid effect to players Uses global scheduler check every 20 ticks
 * instead of PlayerMoveEvent for better performance
 *
 * @author tastybento
 */
public class AcidEffect implements Listener {

	private final AcidIsland addon;
	private final Map<UUID, Long> exposedPlayers;
	private Essentials essentials;
	private boolean essentialsCheck;
	private static final List<PotionEffectType> EFFECTS;
	static {
		if (!inTest()) {
			EFFECTS = List.of(PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA, PotionEffectType.HUNGER,
					PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE, PotionEffectType.WEAKNESS,
					PotionEffectType.POISON, PotionEffectType.DARKNESS, PotionEffectType.UNLUCK);
		} else {
			EFFECTS = List.of();
		}
	}

	private static final List<PotionEffectType> IMMUNE_EFFECTS;
	static {
		if (!inTest()) {
			IMMUNE_EFFECTS = List.of(PotionEffectType.WATER_BREATHING, PotionEffectType.CONDUIT_POWER);
		} else {
			IMMUNE_EFFECTS = List.of();
		}
	}

	/**
	 * This checks the stack trace for @Test to determine if a test is calling the
	 * code and skips. TODO: when we find a way to mock Enchantment, remove this.
	 * 
	 * @return true if it's a test.
	 */
	private static boolean inTest() {
		return Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(e -> e.getClassName().endsWith("Test"));
	}

	public AcidEffect(AcidIsland addon) {
		this.addon = addon;
		exposedPlayers = new ConcurrentHashMap<>();
		startGlobalCheck();
	}

	/**
	 * Starts global scheduler check every 20 ticks (1 second) Uses Folia's region
	 * scheduler for thread safety Only checks acid rain if: 1. Player is in
	 * AcidIsland world 2. It's currently storming 3. Acid rain damage is enabled
	 */
	private void startGlobalCheck() {
		// Schedule repeating task on server global region scheduler (Folia compatible)
		Bukkit.getGlobalRegionScheduler().runAtFixedRate(addon.getPlugin(), (task) -> {
			// Check all online players in AcidIsland worlds
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (player == null || !player.isOnline()) {
					continue;
				}

				UUID uuid = player.getUniqueId();

				// Fast checks - skip if acid is disabled or player is safe
				// These checks don't access blocks, so safe in global scheduler
				if ((addon.getSettings().getAcidRainDamage() == 0 && addon.getSettings().getAcidDamage() == 0)
						|| player.isDead() || player.getGameMode().equals(GameMode.CREATIVE)
						|| player.getGameMode().equals(GameMode.SPECTATOR) || addon.getPlayers().isInTeleport(uuid)
						|| !isInAcidIslandWorld(player.getWorld())
						|| (!player.isOp() && player.hasPermission("acidisland.mod.noburn"))
						|| (player.isOp() && !addon.getSettings().isAcidDamageOp())) {
					exposedPlayers.remove(uuid);
					continue;
				}

				// Schedule acid exposure check on player's region scheduler (Folia safe!)
				// This is where we access blocks, so must be in player's region
				player.getScheduler().run(addon.getPlugin(), t -> {
					// Check acid exposure (block access - safe in player scheduler!)
					boolean inWater = !isSafeFromAcid(player);

					// Only check rain if:
					// 1. Not in water
					// 2. Acid rain damage enabled
					// 3. Currently storming
					// 4. Player is in AcidIsland world (already checked above)
					boolean inRain = !inWater && addon.getSettings().getAcidRainDamage() > 0D
							&& player.getWorld().hasStorm() && !isSafeFromRain(player);

					if (inWater || inRain) {
						if (!exposedPlayers.containsKey(uuid)) {
							// Start hurting them - set delay timer
							exposedPlayers.put(uuid,
									System.currentTimeMillis() + addon.getSettings().getAcidDamageDelay() * 1000);
						}

						// Process acid damage
						processAcidExposure(player);
					} else {
						exposedPlayers.remove(uuid);
					}
				}, null);
			}
		}, 20L, 20L); // Run every 20 ticks (1 second)
	}

	/**
	 * Check if a world is part of AcidIsland addon (overworld, nether, or end)
	 * 
	 * @param world
	 *            - world to check
	 * @return true if world is an AcidIsland world
	 */
	private boolean isInAcidIslandWorld(org.bukkit.World world) {
		return addon.getOverWorld() != null && Util.sameWorld(addon.getOverWorld(), world)
				|| addon.getNetherWorld() != null && Util.sameWorld(addon.getNetherWorld(), world)
				|| addon.getEndWorld() != null && Util.sameWorld(addon.getEndWorld(), world);
	}

	/**
	 * Handles player portal events - marks player as teleporting to prevent acid
	 * damage during world change
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerPortal(EntityPortalAsyncEvent e) {
		if (e.getEntity() instanceof Player player) {
			// Mark player as teleporting to skip acid checks
			addon.getPlayers().setInTeleport(player.getUniqueId());
		}
	}

	/**
	 * Handles player post-portal events - unmarks player after teleport completes
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerPostPortal(EntityPostPortalAsyncEvent e) {
		if (e.getEntity() instanceof Player player) {
			// Unmark player after teleport completes
			addon.getPlayers().removeInTeleport(player.getUniqueId());
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerDeath(PlayerDeathEvent e) {
		exposedPlayers.remove(e.getEntity().getUniqueId());
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerQuit(PlayerQuitEvent e) {
		exposedPlayers.remove(e.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onSeaBounce(PlayerMoveEvent e) {
		Player player = e.getPlayer();
		if (!player.getGameMode().equals(GameMode.CREATIVE) && !player.getGameMode().equals(GameMode.SPECTATOR)
				&& player.getWorld().equals(addon.getOverWorld())
				&& player.getLocation().getBlockY() < player.getWorld().getMinHeight()) {
			player.setVelocity(new Vector(player.getVelocity().getX(), 1D, player.getVelocity().getZ()));
		}
	}

	/**
	 * Processes acid exposure for a player (both water and rain) Called from global
	 * scheduler check
	 * 
	 * @param player
	 *            player
	 * @return true if the exposure should stop
	 */
	protected boolean processAcidExposure(Player player) {
		UUID uuid = player.getUniqueId();

		// Safety checks to stop the task
		if (player.isDead() || (!isInAcidIslandWorld(player.getWorld()))
				|| (player.isOp() && !addon.getSettings().isAcidDamageOp())) {
			exposedPlayers.remove(uuid);
			return true;
		}

		boolean inWater = !isSafeFromAcid(player);
		boolean inRain = !inWater && !isSafeFromRain(player) && player.getWorld().hasStorm()
				&& addon.getSettings().getAcidRainDamage() > 0D;

		if (!inWater && !inRain) {
			exposedPlayers.remove(uuid);
			return true;
		}

		// Check delay
		if (exposedPlayers.containsKey(uuid) && exposedPlayers.get(uuid) < System.currentTimeMillis()) {
			if (inWater) {
				applyWaterAcid(player);
			} else {
				applyRainAcid(player);
			}
		}
		return false;
	}

	private void applyWaterAcid(Player player) {
		double protection = addon.getSettings().getAcidDamage() * getDamageReduced(player);
		double totalDamage = Math.max(0, addon.getSettings().getAcidDamage() - protection);

		AcidEvent event = new AcidEvent(player, totalDamage, protection, addon.getSettings().getAcidEffects());
		addon.getServer().getPluginManager().callEvent(event);

		if (!event.isCancelled()) {
			List<PotionEffectType> configuredEffects = event.getPotionEffects();

			// Only apply default effects if they are NOT already in the configured list
			if (!configuredEffects.contains(PotionEffectType.POISON)) {
				player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 25, 1));
			}
			if (!configuredEffects.contains(PotionEffectType.BLINDNESS)) {
				player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 1));
			}
			if (!configuredEffects.contains(PotionEffectType.NAUSEA)) {
				player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20, 1));
			}

			// Apply other configured effects
			configuredEffects.stream().filter(EFFECTS::contains).forEach(t -> player
					.addPotionEffect(new PotionEffect(t, addon.getSettings().getAcidEffectDuation() * 20, 1)));

			// Apply damage if > 0
			if (totalDamage > 0D) {
				EntityDamageByAcidEvent e = new EntityDamageByAcidEvent(player, totalDamage, Acid.WATER);
				addon.getServer().getPluginManager().callEvent(e);
				if (!e.isCancelled()) {
					player.damage(totalDamage);
				}
			}

			player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 0.5F, 3F);
		}
	}

	private void applyRainAcid(Player player) {
		double protection = addon.getSettings().getAcidRainDamage() * getDamageReduced(player);
		User user = User.getInstance(player);
		double percent = (100 - Math.max(0, Math.min(100, user.getPermissionValue("acidisland.protection.rain", 0))))
				/ 100D;
		double totalDamage = Math.max(0, addon.getSettings().getAcidRainDamage() - protection) * percent;

		AcidRainEvent event = new AcidRainEvent(player, totalDamage, protection,
				addon.getSettings().getAcidRainEffects());
		Bukkit.getPluginManager().callEvent(event);

		if (!event.isCancelled()) {
			event.getPotionEffects().stream().filter(EFFECTS::contains).forEach(t -> player
					.addPotionEffect(new PotionEffect(t, addon.getSettings().getRainEffectDuation() * 20, 1)));

			if (event.getRainDamage() > 0D) {
				EntityDamageByAcidEvent e = new EntityDamageByAcidEvent(player, event.getRainDamage(), Acid.RAIN);
				Bukkit.getPluginManager().callEvent(e);
				if (!e.isCancelled()) {
					player.damage(event.getRainDamage());
					player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 0.5F, 3F);
				}
			}
		}
	}

	/**
	 * Check if player is safe from rain Only performs expensive Y=256 check if: 1.
	 * Player is in AcidIsland world 2. It's currently storming 3. Acid rain damage
	 * is enabled
	 * 
	 * @param player
	 *            - player
	 * @return true if they are safe
	 */
	private boolean isSafeFromRain(Player player) {
		try {
			// Fast checks - no need to scan Y=256 if these fail
			if (isEssentialsGodMode(player) || player.getGameMode() != GameMode.SURVIVAL
					|| (addon.getSettings().isHelmetProtection() && (player.getInventory().getHelmet() != null
							&& player.getInventory().getHelmet().getType().name().contains("HELMET")))
					|| (!addon.getSettings().isAcidDamageSnow()
							&& player.getLocation().getBlock().getTemperature() < 0.1) // snow falls
					|| player.getLocation().getBlock().getHumidity() == 0 // dry
					|| (player.getActivePotionEffects().stream().map(PotionEffect::getType)
							.anyMatch(IMMUNE_EFFECTS::contains))
					// Protect visitors
					|| (addon.getPlugin().getIWM().getIvSettings(player.getWorld()).contains(DamageCause.CUSTOM.name())
							&& !addon.getIslands().userIsOnIsland(player.getWorld(), User.getInstance(player)))) {
				return true;
			}
			// Check if player is under cover (has any block that is not air or water above
			// head up to Y=256)
			// This is the expensive check - only runs if all fast checks pass
			if (isPlayerUnderCover(player)) {
				return true;
			}
			return false;
		} catch (Exception e) {
			// If we can't safely check (e.g., world mismatch in Folia), assume player is
			// safe
			return true;
		}
	}

	/**
	 * Check if player is under cover - uses vanilla-style sky light check (O(1)
	 * performance!) Player is safe from acid rain if sky light < 15 (ANY block
	 * reduces sky light) This means glass, leaves, ice, water, slabs, etc. all
	 * block acid rain
	 * 
	 * @param player
	 *            - player
	 * @return true if player has cover (sky light < 15)
	 */
	private boolean isPlayerUnderCover(Player player) {
		try {
			Location playerLoc = player.getLocation();
			org.bukkit.World world = playerLoc.getWorld();
			if (world == null)
				return false;

			int blockX = playerLoc.getBlockX();
			int blockY = (int) Math.ceil(playerLoc.getY());
			int blockZ = playerLoc.getBlockZ();

			// VANILLA-STYLE CHECK: Use sky light level (O(1) - instant!)
			// Sky light 15 = full sky exposure (NO blocks above = BURN)
			// Sky light < 15 = ANY block above (glass, leaves, slabs, etc. = SAFE)
			byte skyLight = world.getBlockAt(blockX, blockY, blockZ).getLightFromSky();

			// If sky light is less than 15, ANYTHING is blocking the sky
			// This includes: glass, leaves, ice, water, slabs, carpets, etc.
			// All of these block acid rain!
			if (skyLight < 15) {
				return true; // Player is covered!
			}

			// Sky light is 15: Check for overhangs within 5 blocks
			// This handles cases where player is under a tree overhang or building edge
			for (int y = 1; y <= 5; y++) {
				org.bukkit.block.Block block = world.getBlockAt(blockX, blockY + y, blockZ);
				org.bukkit.Material type = block.getType();

				// Any block that's not air blocks acid rain (water already handled by inWater
				// check)
				if (type != Material.AIR) {
					return true; // Found cover!
				}
			}

			return false; // Sky light 15 and no overhangs = FULL EXPOSURE = BURN
		} catch (Exception e) {
			// If we can't safely check (e.g., world mismatch in Folia), assume player is
			// NOT covered
			return false;
		}
	}

	/**
	 * Check if player can be burned by acid
	 * 
	 * @param player
	 *            - player
	 * @return true if player is safe
	 */
	boolean isSafeFromAcid(Player player) {
		// Check for GodMode
		if (isEssentialsGodMode(player) || player.getGameMode() != GameMode.SURVIVAL
		// Protect visitors
				|| (addon.getPlugin().getIWM().getIvSettings(player.getWorld()).contains(DamageCause.CUSTOM.name())
						&& !addon.getIslands().userIsOnIsland(player.getWorld(), User.getInstance(player)))) {
			return true;
		}
		// Not in liquid or on snow
		if (!Util.isChunkLoaded(player.getWorld(), player.getLocation().getBlockX() >> 4,
				player.getLocation().getBlockZ() >> 4)) {
			return true;
		}
		if (!player.getLocation().getBlock().getType().equals(Material.WATER)
				&& !player.getLocation().getBlock().getType().equals(Material.BUBBLE_COLUMN)
				&& (!player.getLocation().getBlock().getType().equals(Material.SNOW)
						|| !addon.getSettings().isAcidDamageSnow())
				&& !player.getLocation().getBlock().getRelative(BlockFace.UP).getType().equals(Material.WATER)) {
			return true;
		}
		// Check if player is on a boat
		if (player.getVehicle() != null && (player.getVehicle().getType().getKey().getKey().contains("boat")
				|| player.getVehicle().getType().getKey().getKey().contains("raft"))) {
			// I'M ON A BOAT! I'M ON A BOAT! A %^&&* BOAT! SNL Sketch.
			// https://youtu.be/avaSdC0QOUM.
			return true;
		}
		// Check if full armor protects
		if (addon.getSettings().isFullArmorProtection() && Arrays.stream(player.getInventory().getArmorContents())
				.allMatch(i -> i != null && !i.getType().equals(Material.AIR))) {
			return true;
		}
		// Check if player has an active water potion or not
		return player.getActivePotionEffects().stream().map(PotionEffect::getType).anyMatch(IMMUNE_EFFECTS::contains);
	}

	/**
	 * Checks if player has Essentials God Mode enabled.
	 * 
	 * @param player
	 *            - player
	 * @return true if God Mode enabled, false if not or if Essentials plug does not
	 *         exist
	 */
	private boolean isEssentialsGodMode(Player player) {
		if (!essentialsCheck && essentials == null) {
			essentials = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
			essentialsCheck = true;
		}
		return essentials != null && essentials.getUser(player).isGodModeEnabled();
	}

	/**
	 * Checks what protection armor provides and slightly damages it as a result of
	 * the acid
	 * 
	 * @param le
	 *            - player
	 * @return A double that reflects how much armor the player has on. The higher
	 *         the value, the more protection they have.
	 */
	public static double getDamageReduced(LivingEntity le) {
		// Full diamond armor value = 20. This normalizes it to a max of 0.8.
		// Enchantments can raise it out further.
		double red = le.getAttribute(Attribute.ARMOR).getValue() * 0.04;
		EntityEquipment inv = le.getEquipment();
		ItemStack boots = inv.getBoots();
		ItemStack helmet = inv.getHelmet();
		ItemStack chest = inv.getChestplate();
		ItemStack pants = inv.getLeggings();
		// Damage if helmet
		if (helmet != null && helmet.getType().name().contains("HELMET") && damage(helmet)) {
			le.getWorld().playSound(le.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
			inv.setHelmet(null);
		}
		if (boots != null && damage(boots)) {
			le.getWorld().playSound(le.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
			inv.setBoots(null);
		}
		// Pants
		if (pants != null && damage(pants)) {
			le.getWorld().playSound(le.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
			inv.setLeggings(null);
		}
		// Chest plate
		if (chest != null && damage(chest)) {
			le.getWorld().playSound(le.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
			inv.setChestplate(null);
		}
		return red;
	}

	private static boolean damage(ItemStack item) {
		ItemMeta im = item.getItemMeta();

		if (im instanceof Damageable d && !im.isUnbreakable()) {
			d.setDamage(d.getDamage() + 1);
			item.setItemMeta(d);
			return d.getDamage() >= item.getType().getMaxDurability();
		}
		return false;
	}
}
