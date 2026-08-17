/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.fabric.entity;

import dev.anirban.pegasus.common.BreedingRules;
import dev.anirban.pegasus.common.Messages;
import dev.anirban.pegasus.common.PegasusConfig;
import dev.anirban.pegasus.common.PegasusVariant;
import dev.anirban.pegasus.fabric.PegasusMod;
import dev.anirban.pegasus.fabric.PegasusRegistry;
import java.util.Locale;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * A custom Unicorn, implemented in this project so the Pegasus breeding chain is self-contained
 * (no external mod is required or assumed).
 *
 * <p>Breeding flow: a Nether Star primes a Unicorn, then a Golden Carrot starts vanilla love mode.
 * When two Unicorns breed, the primed state decides whether the foal is a Pegasus.
 */
public class UnicornEntity extends AbstractHorseEntity {
    private static final String NBT_PREPARED_AT = "UnicornPreparedAt";

    public UnicornEntity(EntityType<? extends UnicornEntity> type, World world) {
        super(type, world);
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        PegasusConfig config = PegasusMod.config();

        if (getWorld().isClient()) {
            return ActionResult.SUCCESS;
        }

        // Nether Star primes this Unicorn; the state is transient and expires.
        if (matches(held, config.breedingItems().preparationItem())) {
            if (!player.isCreative()) {
                held.decrement(1);
            }
            PegasusMod.breeding().prepare(getUuid(), System.currentTimeMillis());
            player.sendMessage(Text.literal(config.messages().get(Messages.UNICORN_PREPARED)), true);
            PegasusMod.debug(() -> "Unicorn " + getUuid() + " primed by " + player.getGameProfile().getName());
            return ActionResult.SUCCESS;
        }
        return super.interactMob(player, hand);
    }

    /** Golden Carrot is the breeding trigger, replacing the vanilla horse food set. */
    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return matches(stack, PegasusMod.config().breedingItems().triggerItem());
    }

    /**
     * Produces the foal.
     *
     * <p>Both parents primed gives a 100% Pegasus foal, one primed gives 50%, neither gives an
     * ordinary Unicorn. Priming is consumed here so one Nether Star cannot be reused.
     */
    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity mate) {
        PegasusConfig config = PegasusMod.config();
        java.util.UUID partnerId = mate instanceof UnicornEntity unicorn ? unicorn.getUuid() : null;

        int chance = PegasusMod.breeding().consumeForBreeding(
                getUuid(), partnerId, System.currentTimeMillis(), config.breeding());

        if (chance > 0 && BreedingRules.succeeds(chance, this.random.nextInt(100) + 1)) {
            PegasusEntity foal = PegasusRegistry.PEGASUS.create(world);
            if (foal != null) {
                // Blue-eyed is the documented variant for a Pegasus born from Unicorns.
                foal.setVariant(PegasusVariant.BLUE_EYE);
                foal.setBaby(true);
                PegasusMod.debug(() -> "Pegasus foal produced at chance " + chance + "%");
                return foal;
            }
        }
        UnicornEntity child = PegasusRegistry.UNICORN.create(world);
        if (child != null) {
            child.setBaby(true);
        }
        return child;
    }

    /**
     * Primed state is intentionally not persisted: it is short-lived, and reviving a stale star
     * across a restart would let players bank preparations indefinitely.
     */
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean(NBT_PREPARED_AT, false);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
    }

    @Override
    public boolean canBeSaddled() {
        return true;
    }

    private static boolean matches(ItemStack stack, String configuredId) {
        if (stack.isEmpty()) {
            return false;
        }
        String normalised = configuredId.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        String full = Registries.ITEM.getId(stack.getItem()).toString();
        return full.equals(normalised) || path.equals(normalised) || ("minecraft:" + path).equals(normalised);
    }
}
