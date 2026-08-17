/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.paper;

import dev.anirban.pegasus.common.BreedingRules;
import dev.anirban.pegasus.common.BreedingService;
import dev.anirban.pegasus.common.CooldownTracker;
import dev.anirban.pegasus.common.Messages;
import dev.anirban.pegasus.common.OwnershipRecord;
import dev.anirban.pegasus.common.PegasusConfig;
import dev.anirban.pegasus.common.PegasusVariant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.HorseJumpEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * All Paper gameplay wiring: taming, ownership enforcement, riding, flight input, breeding,
 * spawn eggs and cleanup.
 */
public final class PegasusListener implements Listener {
    public static final String PERMISSION_ADMIN = "pegasus.admin";
    public static final String PERMISSION_RIDE = "pegasus.ride";
    public static final String PERMISSION_TAME = "pegasus.tame";
    public static final String PERMISSION_BREED = "pegasus.breed";

    private final PegasusPlugin plugin;
    private final PegasusEntities entities;
    private final PaperOwnershipStore ownership;
    private final SpawnEggService eggs;
    private final BreedingService breeding;
    private final Random random;
    private final CooldownTracker tameCooldown;

    public PegasusListener(PegasusPlugin plugin, PegasusEntities entities, PaperOwnershipStore ownership,
                           SpawnEggService eggs, BreedingService breeding, Random random) {
        this.plugin = plugin;
        this.entities = entities;
        this.ownership = ownership;
        this.eggs = eggs;
        this.breeding = breeding;
        this.random = random;
        this.tameCooldown = new CooldownTracker(plugin.config().taming().cooldown());
    }

    private PegasusConfig config() {
        return plugin.config();
    }

    private Messages messages() {
        return config().messages();
    }

    private void send(Player player, String key, Map<String, String> placeholders) {
        player.sendMessage(Component.text(messages().format(key, placeholders)).color(NamedTextColor.GRAY));
    }

    // ------------------------------------------------------------------ interaction

    /**
     * Single entry point for right-clicking a Pegasus or Unicorn.
     *
     * <p>Handles spawn-egg use, taming, Nether Star preparation and the Golden Carrot trigger.
     * Runs at {@code NORMAL} and cancels rather than letting vanilla apply a conflicting action.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // Ignore the off-hand copy of this event so actions never run twice.
        }
        Entity target = event.getRightClicked();
        Player player = event.getPlayer();

        if (entities.isUnicorn(target) && target instanceof Horse unicorn) {
            handleUnicornInteraction(event, player, unicorn);
            return;
        }
        if (!entities.isPegasus(target) || !(target instanceof Horse pegasus)) {
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        Optional<OwnershipRecord> owner = ownership.owner(pegasus);

        if (owner.isEmpty()) {
            event.setCancelled(true);
            attemptTame(player, pegasus, held);
            return;
        }

        OwnershipRecord record = owner.get();
        boolean admin = player.hasPermission(PERMISSION_ADMIN);
        if (!record.ownerId().equals(player.getUniqueId()) && !admin) {
            event.setCancelled(true);
            send(player, Messages.NOT_OWNER, Map.of("owner", record.lastKnownName()));
            return;
        }
        ownership.refreshName(pegasus, player);

        if (config().taming().requireSaddleToRide() && !hasSaddle(pegasus)
                && held.getType() != Material.SADDLE) {
            event.setCancelled(true);
            send(player, Messages.NEEDS_SADDLE, Map.of());
        }
    }

    private void attemptTame(Player player, Horse pegasus, ItemStack held) {
        if (!player.hasPermission(PERMISSION_TAME)) {
            send(player, Messages.NO_PERMISSION, Map.of());
            return;
        }
        if (!isTameItem(held)) {
            return;
        }
        if (!tameCooldown.tryUse(player.getUniqueId(), System.currentTimeMillis())) {
            return;
        }

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }
        pegasus.getWorld().spawnParticle(org.bukkit.Particle.HEART, pegasus.getEyeLocation(), 3, 0.4, 0.4, 0.4);

        if (random.nextInt(100) >= config().taming().chancePercent()) {
            send(player, Messages.TAME_FAILED, Map.of());
            return;
        }

        // First successful attempt wins permanently; the store makes this atomic.
        if (!ownership.tryClaim(pegasus, player)) {
            ownership.owner(pegasus).ifPresent(record ->
                    send(player, Messages.ALREADY_OWNED, Map.of("owner", record.lastKnownName())));
            return;
        }
        pegasus.setTamed(true);
        pegasus.setOwner(player);
        pegasus.setCustomNameVisible(false);
        send(player, Messages.TAME_SUCCESS, Map.of());
        plugin.debug(() -> "Pegasus " + pegasus.getUniqueId() + " claimed by " + player.getName());
    }

    private void handleUnicornInteraction(PlayerInteractEntityEvent event, Player player, Horse unicorn) {
        ItemStack held = player.getInventory().getItemInMainHand();
        PegasusConfig current = config();

        if (matches(held, current.breedingItems().preparationItem())) {
            if (!player.hasPermission(PERMISSION_BREED)) {
                event.setCancelled(true);
                send(player, Messages.NO_PERMISSION, Map.of());
                return;
            }
            event.setCancelled(true);
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                held.setAmount(held.getAmount() - 1);
            }
            breeding.prepare(unicorn.getUniqueId(), System.currentTimeMillis());
            unicorn.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, unicorn.getEyeLocation(), 12, 0.5, 0.5, 0.5);
            send(player, Messages.UNICORN_PREPARED, Map.of());
            plugin.debug(() -> "Unicorn " + unicorn.getUniqueId() + " prepared by " + player.getName());
        }
        // The Golden Carrot is intentionally NOT cancelled: vanilla love mode starts the breed,
        // and EntityBreedEvent then decides whether the foal becomes a Pegasus.
    }

    // ------------------------------------------------------------------ breeding

    /**
     * Converts a Unicorn foal into a Pegasus when at least one parent was prepared.
     *
     * <p>Chance comes from the shared rules: both parents prepared gives 100%, one gives 50%, none
     * gives 0%. Preparations are consumed here so a single Nether Star cannot be reused.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getEntity() instanceof Horse child)) {
            return;
        }
        if (!entities.isUnicorn(event.getMother()) || !entities.isUnicorn(event.getFather())) {
            return;
        }

        PegasusConfig current = config();
        int chance = breeding.consumeForBreeding(event.getMother().getUniqueId(),
                event.getFather().getUniqueId(), System.currentTimeMillis(), current.breeding());

        Player breeder = event.getBreeder() instanceof Player player ? player : null;
        if (chance <= 0 || !BreedingRules.succeeds(chance, random.nextInt(100) + 1)) {
            if (breeder != null) {
                send(breeder, Messages.BREEDING_FAILED, Map.of());
            }
            return;
        }

        // Blue-eyed foal is the documented variant for Pegasus born from Unicorns.
        entities.initialisePegasus(child, PegasusVariant.BLUE_EYE, current, true);
        child.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, child.getLocation(), 20, 0.6, 0.6, 0.6);
        if (breeder != null) {
            send(breeder, Messages.BREEDING_SUCCESS, Map.of());
        }
        plugin.debug(() -> "Pegasus foal born with chance " + chance + "%");
    }

    // ------------------------------------------------------------------ riding and flight

    /** Blocks non-owners from mounting before the client ever shows them seated. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!entities.isPegasus(event.getMount()) || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Horse pegasus = (Horse) event.getMount();
        boolean admin = player.hasPermission(PERMISSION_ADMIN);

        Optional<OwnershipRecord> owner = ownership.owner(pegasus);
        if (owner.isEmpty()) {
            event.setCancelled(true);
            send(player, Messages.OWNER_NONE, Map.of());
            return;
        }
        if (!owner.get().ownerId().equals(player.getUniqueId()) && !admin) {
            event.setCancelled(true);
            send(player, Messages.NOT_OWNER, Map.of("owner", owner.get().lastKnownName()));
            return;
        }
        if (!player.hasPermission(PERMISSION_RIDE)) {
            event.setCancelled(true);
            send(player, Messages.NO_PERMISSION, Map.of());
            return;
        }
        if (config().taming().requireSaddleToRide() && !hasSaddle(pegasus)) {
            event.setCancelled(true);
            send(player, Messages.NEEDS_SADDLE, Map.of());
        }
    }

    /** Jump becomes a takeoff; the controller enforces its own cooldown. */
    @EventHandler(ignoreCancelled = true)
    public void onHorseJump(HorseJumpEvent event) {
        if (!entities.isPegasus(event.getEntity()) || !(event.getEntity() instanceof Horse pegasus)) {
            return;
        }
        if (pegasus.getPassengers().isEmpty()) {
            return;
        }
        plugin.flight().requestTakeoff(pegasus, System.currentTimeMillis());
    }

    /** Sneak toggles controlled descent while flying. */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Entity vehicle = event.getPlayer().getVehicle();
        if (vehicle != null && entities.isPegasus(vehicle)) {
            plugin.flight().setDescending(vehicle.getUniqueId(), event.isSneaking());
        }
    }

    /** Clears flight state on dismount so a parked Pegasus is not left in flight mode. */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (entities.isPegasus(event.getDismounted())) {
            plugin.flight().forget(event.getDismounted().getUniqueId());
            plugin.effects().forget(event.getDismounted().getUniqueId());
        }
    }

    /** Honours the configurable fall-damage protection for flying Pegasus and their riders. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        Entity entity = event.getEntity();
        if (entities.isPegasus(entity) && plugin.flight().shouldCancelFallDamage(entity.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        // A rider who dismounts mid-air should not be punished for the Pegasus' altitude.
        if (entity instanceof Player player) {
            Entity vehicle = player.getVehicle();
            if (vehicle != null && entities.isPegasus(vehicle)
                    && plugin.flight().shouldCancelFallDamage(vehicle.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    // ------------------------------------------------------------------ lifecycle

    /** Releases per-entity state and cached ownership when a Pegasus dies. */
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (entities.isPegasus(dead) || entities.isUnicorn(dead)) {
            plugin.flight().forget(dead.getUniqueId());
            plugin.effects().forget(dead.getUniqueId());
            ownership.forget(dead.getUniqueId());
            breeding.forget(dead.getUniqueId());
            plugin.debug(() -> "Cleaned up state for dead entity " + dead.getUniqueId());
        }
    }

    /** Applies our data when a Pegasus is spawned from one of our eggs. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || !(event.getEntity() instanceof Horse horse)) {
            return;
        }
        PegasusVariant pending = plugin.consumePendingEggVariant(horse.getLocation());
        if (pending != null) {
            entities.initialisePegasus(horse, pending, config(), false);
            plugin.debug(() -> "Spawned " + pending.id() + " Pegasus from egg");
        }
    }

    /** Records the variant of an egg as it is used, and enforces the egg permission. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEggUse(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        Optional<PegasusVariant> variant = eggs.readVariant(item);
        if (variant.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (config().eggs().requirePermission()
                && !player.hasPermission(SpawnEggService.permissionFor(variant.get()))
                && !player.hasPermission(PERMISSION_ADMIN)) {
            event.setCancelled(true);
            send(player, Messages.NO_PERMISSION, Map.of());
            return;
        }
        if (event.getClickedBlock() != null) {
            plugin.rememberPendingEggVariant(
                    event.getClickedBlock().getRelative(event.getBlockFace()).getLocation(), variant.get());
        }
    }

    /** Prevents non-owners renaming a Pegasus with a name tag. */
    @EventHandler(ignoreCancelled = true)
    public void onNameTag(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (held.getType() != Material.NAME_TAG || !entities.isPegasus(event.getRightClicked())) {
            return;
        }
        Player player = event.getPlayer();
        if (!ownership.canManage(event.getRightClicked(), player.getUniqueId(), player.hasPermission(PERMISSION_ADMIN))) {
            event.setCancelled(true);
            ownership.owner(event.getRightClicked()).ifPresent(record ->
                    send(player, Messages.NOT_OWNER, Map.of("owner", record.lastKnownName())));
        }
    }

    // ------------------------------------------------------------------ helpers

    private boolean hasSaddle(Horse horse) {
        ItemStack saddle = horse.getInventory().getSaddle();
        return saddle != null && saddle.getType() == Material.SADDLE;
    }

    private boolean isTameItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        for (String name : config().taming().items()) {
            if (item.getType().name().equalsIgnoreCase(name.strip().replace(' ', '_'))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(ItemStack item, String materialName) {
        return item != null
                && item.getType().name().equals(materialName.strip().toUpperCase(Locale.ROOT).replace(' ', '_'));
    }
}
