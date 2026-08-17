/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.fabric.entity;

import dev.anirban.pegasus.common.Messages;
import dev.anirban.pegasus.common.OwnershipRecord;
import dev.anirban.pegasus.common.PegasusConfig;
import dev.anirban.pegasus.common.PegasusVariant;
import dev.anirban.pegasus.common.animation.AnimationResolver;
import dev.anirban.pegasus.common.animation.AnimationState;
import dev.anirban.pegasus.fabric.PegasusMod;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Fabric Pegasus: a real registered entity type with server-authoritative flight and
 * permanent first-tamer ownership stored in the entity's own NBT.
 *
 * <p>Extending {@link AbstractHorseEntity} deliberately reuses vanilla saddling, mounting,
 * inventory and breeding, all of which are already correctly synchronised to clients.
 */
public class PegasusEntity extends AbstractHorseEntity {
    private static final TrackedData<Integer> VARIANT =
            DataTracker.registerData(PegasusEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Synced so the client renderer can animate wings without guessing. */
    private static final TrackedData<Integer> ANIMATION =
            DataTracker.registerData(PegasusEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> FLYING =
            DataTracker.registerData(PegasusEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final String NBT_OWNER_UUID = "PegasusOwnerUuid";
    private static final String NBT_OWNER_NAME = "PegasusOwnerName";
    private static final String NBT_VERSION = "PegasusDataVersion";
    private static final String NBT_VARIANT = "PegasusVariant";

    private final AnimationResolver animations = new AnimationResolver();
    private OwnershipRecord ownership;
    private long lastTakeoffMillis;
    private boolean descendRequested;

    public PegasusEntity(EntityType<? extends PegasusEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(VARIANT, PegasusVariant.CLASSIC.ordinal());
        builder.add(ANIMATION, AnimationState.IDLE.ordinal());
        builder.add(FLYING, false);
    }

    // ------------------------------------------------------------------ variant

    public PegasusVariant variant() {
        int ordinal = this.dataTracker.get(VARIANT);
        PegasusVariant[] values = PegasusVariant.values();
        // Guard against out-of-range synced data rather than throwing during render.
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PegasusVariant.CLASSIC;
    }

    public void setVariant(PegasusVariant variant) {
        this.dataTracker.set(VARIANT, (variant == null ? PegasusVariant.CLASSIC : variant).ordinal());
    }

    public AnimationState animationState() {
        int ordinal = this.dataTracker.get(ANIMATION);
        AnimationState[] values = AnimationState.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : AnimationState.IDLE;
    }

    public boolean isFlyingPegasus() {
        return this.dataTracker.get(FLYING);
    }

    // ------------------------------------------------------------------ ownership

    public Optional<OwnershipRecord> ownership() {
        return Optional.ofNullable(ownership);
    }

    /**
     * Attempts to become the permanent owner.
     *
     * <p>Entity interaction is processed on the server thread, and this method only assigns when
     * {@code ownership} is still null, so the first successful tamer wins even if two players click
     * in the same tick.
     */
    public synchronized boolean tryClaimOwnership(PlayerEntity player) {
        if (ownership != null) {
            return false;
        }
        ownership = OwnershipRecord.of(player.getUuid(), player.getGameProfile().getName());
        setTame(true);
        setOwnerUuid(player.getUuid());
        return true;
    }

    public synchronized void assignOwnership(UUID ownerId, String ownerName) {
        ownership = OwnershipRecord.of(ownerId, ownerName);
        setTame(true);
        setOwnerUuid(ownerId);
    }

    public synchronized void clearOwnership() {
        ownership = null;
        setTame(false);
        setOwnerUuid(null);
    }

    public boolean isOwnedBy(PlayerEntity player) {
        return ownership != null && ownership.ownerId().equals(player.getUuid());
    }

    /** Owner-only gate; administrators are allowed through by the caller. */
    public boolean canManage(PlayerEntity player, boolean administratorOverride) {
        return administratorOverride || isOwnedBy(player);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString(NBT_VARIANT, variant().id());
        nbt.putInt(NBT_VERSION, OwnershipRecord.CURRENT_VERSION);
        if (ownership != null) {
            nbt.putString(NBT_OWNER_UUID, ownership.ownerId().toString());
            nbt.putString(NBT_OWNER_NAME, ownership.lastKnownName());
        }
    }

    /**
     * Restores persisted state.
     *
     * <p>Uses the shared, version-tolerant {@link OwnershipRecord#deserialize(Map)} so a malformed
     * UUID or a record written by an older build is discarded safely instead of propagating a broken
     * owner that no one could ever clear.
     */
    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setVariant(PegasusVariant.parseOrDefault(nbt.getString(NBT_VARIANT)));

        Map<String, String> raw = new HashMap<>();
        raw.put("owner-uuid", nbt.getString(NBT_OWNER_UUID));
        raw.put("owner-name", nbt.getString(NBT_OWNER_NAME));
        if (nbt.contains(NBT_VERSION)) {
            raw.put("version", Integer.toString(nbt.getInt(NBT_VERSION)));
        }
        Optional<OwnershipRecord> restored = OwnershipRecord.deserialize(raw);
        if (restored.isPresent()) {
            ownership = restored.get();
            setTame(true);
            setOwnerUuid(ownership.ownerId());
        } else {
            ownership = null;
            if (!nbt.getString(NBT_OWNER_UUID).isBlank()) {
                PegasusMod.logger().warn("Discarded invalid Pegasus ownership data on entity {}", getUuid());
            }
        }
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        PegasusConfig config = PegasusMod.config();

        if (getWorld().isClient()) {
            // Let the client predict only when the interaction is going to be accepted.
            return ownership == null || isOwnedBy(player) ? ActionResult.SUCCESS : ActionResult.FAIL;
        }

        if (ownership == null) {
            return attemptTame(player, held, config);
        }
        if (!isOwnedBy(player) && !isOperator(player)) {
            player.sendMessage(Text.literal(config.messages()
                    .format(Messages.NOT_OWNER, "owner", ownership.lastKnownName())), true);
            return ActionResult.FAIL;
        }
        // Keep the stored display name current if the owner renamed their account.
        if (isOwnedBy(player) && !ownership.lastKnownName().equals(player.getGameProfile().getName())) {
            ownership = OwnershipRecord.of(ownership.ownerId(), player.getGameProfile().getName());
        }

        if (held.isOf(Items.SADDLE) && !isSaddled()) {
            return super.interactMob(player, hand);
        }
        if (config.taming().requireSaddleToRide() && !isSaddled()) {
            player.sendMessage(Text.literal(config.messages().get(Messages.NEEDS_SADDLE)), true);
            return ActionResult.FAIL;
        }
        return super.interactMob(player, hand);
    }

    private ActionResult attemptTame(PlayerEntity player, ItemStack held, PegasusConfig config) {
        if (!isConfiguredTameItem(held, config)) {
            return ActionResult.FAIL;
        }
        if (!player.isCreative()) {
            held.decrement(1);
        }
        if (this.random.nextInt(100) >= config.taming().chancePercent()) {
            player.sendMessage(Text.literal(config.messages().get(Messages.TAME_FAILED)), true);
            return ActionResult.CONSUME;
        }
        if (!tryClaimOwnership(player)) {
            player.sendMessage(Text.literal(config.messages()
                    .format(Messages.ALREADY_OWNED, "owner", ownership.lastKnownName())), true);
            return ActionResult.FAIL;
        }
        player.sendMessage(Text.literal(config.messages().get(Messages.TAME_SUCCESS)), true);
        PegasusMod.debug(() -> "Pegasus " + getUuid() + " claimed by " + player.getGameProfile().getName());
        return ActionResult.SUCCESS;
    }

    private static boolean isConfiguredTameItem(ItemStack stack, PegasusConfig config) {
        if (stack.isEmpty()) {
            return false;
        }
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        String bare = Registries.ITEM.getId(stack.getItem()).getPath();
        for (String configured : config.taming().items()) {
            String normalised = configured.strip().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
            if (id.equals(normalised) || bare.equals(normalised)
                    || ("minecraft:" + bare).equals(normalised)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOperator(PlayerEntity player) {
        return player.hasPermissionLevel(2);
    }

    // ------------------------------------------------------------------ flight

    /**
     * Server-side flight.
     *
     * <p>Steering comes from the rider's look direction, matching the Paper module and requiring no
     * client-side input packets. Velocity is set on the server so all observers stay in sync.
     */
    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient()) {
            return;
        }

        PegasusConfig config = PegasusMod.config();
        PlayerEntity rider = getControllingPlayer();
        boolean flying = false;

        if (rider != null && !isOnGround()) {
            flying = true;
            applyFlight(rider, config);
        }
        if (isOnGround()) {
            descendRequested = false;
        }
        this.dataTracker.set(FLYING, flying);

        Vec3d velocity = getVelocity();
        AnimationState state = animations.resolve(new AnimationResolver.Input(
                isDead() || getHealth() <= 0.0f,
                hurtTime > 0,
                false,
                isOnGround(),
                flying,
                Math.hypot(velocity.x, velocity.z),
                velocity.y,
                velocity.y > 0.08), System.currentTimeMillis());
        this.dataTracker.set(ANIMATION, state.ordinal());

        if (config.flight().preventFallDamage() && (flying || isFlyingPegasus())) {
            this.fallDistance = 0.0f;
        }
    }

    private void applyFlight(PlayerEntity rider, PegasusConfig config) {
        Vec3d look = rider.getRotationVec(1.0f).normalize();
        double speed = config.flight().horizontalSpeed();
        if (rider.isSprinting()) {
            speed *= config.flight().sprintMultiplier();
        }

        double horizontalLength = Math.hypot(look.x, look.z);
        double x = horizontalLength > 1.0E-6 ? (look.x / horizontalLength) * speed : 0.0;
        double z = horizontalLength > 1.0E-6 ? (look.z / horizontalLength) * speed : 0.0;

        double y = look.y * config.flight().verticalSpeed() * 0.9;
        if (descendRequested || rider.isSneaking()) {
            y = -config.flight().verticalSpeed();
        } else if (y < -0.055) {
            y = -0.055; // Gentle hover floor so a level Pegasus glides instead of dropping.
        }

        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return; // Never assign a non-finite velocity; it corrupts entity position.
        }
        setVelocity(x, y, z);
        velocityDirty = true;
    }

    /** Jump input becomes a takeoff impulse, rate-limited by the configured cooldown. */
    @Override
    public void setJumpStrength(int strength) {
        super.setJumpStrength(strength);
        if (strength <= 0 || getWorld().isClient() || getControllingPlayer() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        PegasusConfig config = PegasusMod.config();
        if (now - lastTakeoffMillis < config.flight().takeoffCooldown().toMillis()) {
            return;
        }
        lastTakeoffMillis = now;
        Vec3d velocity = getVelocity();
        setVelocity(velocity.x, Math.max(velocity.y, config.flight().verticalSpeed() * 1.4), velocity.z);
        velocityDirty = true;
    }

    public void setDescendRequested(boolean value) {
        this.descendRequested = value;
    }

    private PlayerEntity getControllingPlayer() {
        return getControllingPassenger() instanceof PlayerEntity player ? player : null;
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return false; // Pegasus do not breed directly; they are born from prepared Unicorns.
    }

    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity mate) {
        return null;
    }

    @Override
    public boolean canBeSaddled() {
        return true;
    }

    @Override
    public void saddle(ItemStack stack, SoundCategory category) {
        super.saddle(stack, category);
    }
}
