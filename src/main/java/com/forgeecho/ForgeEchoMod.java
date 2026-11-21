package com.forgeecho;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.Collection;

public class ForgeEchoMod implements ModInitializer {
    private static final String TAG = "forge_echo";
    /** Cooldown in ticks (20 ticks = 1 second). */
    private static final int COOLDOWN_TICKS = 100;

    @Override
    public void onInitialize() {
        // /Forge abilities give Echo <players>
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("Forge")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("abilities")
                    .then(Commands.literal("give")
                        .then(Commands.literal("Echo")
                            .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> {
                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                    for (ServerPlayer p : players) {
                                        ItemStack stack = createEcho();
                                        if (!p.getInventory().add(stack)) {
                                            p.drop(stack, false);
                                        }
                                    }
                                    ctx.getSource().sendSuccess(
                                        () -> Component.literal("Gave The Forge's Echo to " + players.size() + " player(s)"),
                                        true);
                                    return players.size();
                                })))))));

        // Right click
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!isForgeEcho(stack)) return InteractionResult.PASS;

            if (world instanceof ServerLevel level && player instanceof ServerPlayer sp) {
                if (!sp.getCooldowns().isOnCooldown(stack)) {
                    BeamAbility.fire(level, sp);
                    sp.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
                }
            }
            return InteractionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> BlastTask.tickAll());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> BlastTask.clear());
    }

    public static ItemStack createEcho() {
        ItemStack stack = new ItemStack(Items.ECHO_SHARD);
        stack.set(DataComponents.CUSTOM_NAME, buildName());
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static boolean isForgeEcho(ItemStack stack) {
        if (!stack.is(Items.ECHO_SHARD)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(TAG);
    }

    private static MutableComponent buildName() {
        Style base = Style.EMPTY.withBold(true).withItalic(true).withUnderlined(true);
        MutableComponent the = Component.literal("The ").withStyle(base.withColor(0x2233CC));      // dark blue
        MutableComponent forge = Component.literal("Forge's").withStyle(base.withColor(0xFF8800)); // orange
        MutableComponent echo = Component.literal(" Echo's").withStyle(base.withColor(0x00E5FF));  // cyan
        return the.append(forge).append(echo);
    }
}
