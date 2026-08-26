package com.cetori.privates;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CetoriPrivates implements ModInitializer {
    public static final String MOD_ID = "cetori_privates";
    public static ClaimManager CLAIMS;

    @Override
    public void onInitialize() {
        CLAIMS = new ClaimManager();
        ServerLifecycleEvents.SERVER_STARTED.register(CLAIMS::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(CLAIMS::save);
        registerCommands();
        registerProtection();
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("rg")
                .then(CommandManager.literal("info")
                    .executes(ctx -> info(ctx, null))
                    .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                        .executes(ctx -> info(ctx, IntegerArgumentType.getInteger(ctx, "id")))))
                .then(CommandManager.literal("addmember")
                    .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(ctx -> addMember(ctx,
                                IntegerArgumentType.getInteger(ctx, "id"),
                                EntityArgumentType.getPlayer(ctx, "player"))))))
                .then(CommandManager.literal("removemember")
                    .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> removeMember(ctx,
                                IntegerArgumentType.getInteger(ctx, "id"),
                                StringArgumentType.getString(ctx, "player"))))))
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                        .executes(ctx -> removeClaim(ctx, IntegerArgumentType.getInteger(ctx, "id")))))
                .then(CommandManager.literal("list").executes(CetoriPrivates::listClaims))
                .then(CommandManager.literal("trust")
                    .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(ctx -> addMember(ctx,
                                IntegerArgumentType.getInteger(ctx, "id"),
                                EntityArgumentType.getPlayer(ctx, "player"))))))
                .then(CommandManager.literal("untrust")
                    .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> removeMember(ctx,
                                IntegerArgumentType.getInteger(ctx, "id"),
                                StringArgumentType.getString(ctx, "player"))))))
                .then(CommandManager.literal("admin")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                            .executes(ctx -> adminRemove(ctx,
                                IntegerArgumentType.getInteger(ctx, "id")))))
            ));
        });
    }

    private static int info(CommandContext<ServerCommandSource> ctx, Integer id) {
        ServerCommandSource source = ctx.getSource();
        Claim claim;
        if (id != null) {
            claim = CLAIMS.get(id);
        } else if (source.getEntity() instanceof ServerPlayerEntity player) {
            claim = CLAIMS.findAt(player.getServerWorld(), player.getBlockPos());
        } else {
            source.sendError(Text.literal("Використай /rg info <id> з консолі."));
            return 0;
        }

        if (claim == null) {
            source.sendError(Text.literal("§cПриват не знайдено."));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("§6=== Приват #" + claim.id + " ==="), false);
        source.sendFeedback(() -> Text.literal("§7Власник: §f" + claim.ownerName), false);
        source.sendFeedback(() -> Text.literal("§7Розмір: §f" + claim.size + "x" + claim.size), false);
        source.sendFeedback(() -> Text.literal("§7Центр: §f" + claim.x + " " + claim.y + " " + claim.z), false);
        source.sendFeedback(() -> Text.literal("§7Світ: §f" + claim.dimension), false);
        source.sendFeedback(() -> Text.literal("§7Учасники: §f" +
            (claim.members.isEmpty() ? "немає" : String.join(", ", claim.members))), false);
        return 1;
    }

    private static int addMember(CommandContext<ServerCommandSource> ctx, int id, ServerPlayerEntity target) {
        Claim claim = CLAIMS.get(id);
        if (claim == null) {
            ctx.getSource().sendError(Text.literal("§cПриват не знайдено."));
            return 0;
        }

        ServerPlayerEntity owner = ctx.getSource().getPlayer();
        if (!isOwnerOrAdmin(owner, claim)) {
            ctx.getSource().sendError(Text.literal("§cТільки власник може додавати учасників."));
            return 0;
        }

        String name = target.getName().getString();
        if (target.getUuid().equals(claim.owner) || claim.members.stream().anyMatch(n -> n.equalsIgnoreCase(name))) {
            ctx.getSource().sendError(Text.literal("§eЦей гравець вже має доступ."));
            return 0;
        }

        claim.members.add(name);
        CLAIMS.save(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.literal("§a" + name + " доданий до привату #" + id), false);
        return 1;
    }

    private static int removeMember(CommandContext<ServerCommandSource> ctx, int id, String playerName) {
        Claim claim = CLAIMS.get(id);
        if (claim == null) {
            ctx.getSource().sendError(Text.literal("§cПриват не знайдено."));
            return 0;
        }

        ServerPlayerEntity owner = ctx.getSource().getPlayer();
        if (!isOwnerOrAdmin(owner, claim)) {
            ctx.getSource().sendError(Text.literal("§cТільки власник може видаляти учасників."));
            return 0;
        }

        boolean removed = claim.members.removeIf(n -> n.equalsIgnoreCase(playerName));
        if (!removed) {
            ctx.getSource().sendError(Text.literal("§eГравця немає у списку."));
            return 0;
        }

        CLAIMS.save(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.literal("§a" + playerName + " видалений з привату #" + id), false);
        return 1;
    }

    private static int removeClaim(CommandContext<ServerCommandSource> ctx, int id) {
        Claim claim = CLAIMS.get(id);
        if (claim == null) {
            ctx.getSource().sendError(Text.literal("§cПриват не знайдено."));
            return 0;
        }

        ServerPlayerEntity owner = ctx.getSource().getPlayer();
        if (!isOwnerOrAdmin(owner, claim)) {
            ctx.getSource().sendError(Text.literal("§cТільки власник може видалити приват."));
            return 0;
        }

        CLAIMS.remove(id);
        CLAIMS.save(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.literal("§aПриват #" + id + " видалено."), false);
        return 1;
    }

    private static int adminRemove(CommandContext<ServerCommandSource> ctx, int id) {
        if (CLAIMS.remove(id) == null) {
            ctx.getSource().sendError(Text.literal("§cПриват не знайдено."));
            return 0;
        }
        CLAIMS.save(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.literal("§aАдмін видалив приват #" + id + "."), false);
        return 1;
    }

    private static int listClaims(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        List<Claim> list = CLAIMS.byOwner(player.getUuid());
        ctx.getSource().sendFeedback(() -> Text.literal("§6Твої привати: §f" + list.size()), false);
        for (Claim c : list) {
            ctx.getSource().sendFeedback(() -> Text.literal("§e#" + c.id + " §7" +
                c.size + "x" + c.size + " §f" + c.dimension + " §7(" +
                c.x + ", " + c.y + ", " + c.z + ")"), false);
        }
        return 1;
    }

    private static boolean isOwnerOrAdmin(ServerPlayerEntity player, Claim claim) {
        return player != null && (player.getUuid().equals(claim.owner) || player.hasPermissionLevel(2));
    }

    private void registerProtection() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;

            Claim claim = CLAIMS.findAt(serverWorld, pos);
            if (claim == null) return ActionResult.PASS;

            if (pos.equals(claim.anchor())) {
                if (player.getUuid().equals(claim.owner) || player.hasPermissionLevel(2)) {
                    CLAIMS.remove(claim.id);
                    CLAIMS.save(serverWorld.getServer());
                    player.sendMessage(Text.literal("§aПриват #" + claim.id + " видалено."), true);
                    return ActionResult.PASS;
                }
                player.sendMessage(Text.literal("§cЦе блок привату. Лише власник може його зламати."), true);
                return ActionResult.FAIL;
            }

            if (!claim.canBuild(player)) {
                player.sendMessage(Text.literal("§cЦя територія захищена! §7Приват #" + claim.id), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;

            Block held = Block.getBlockFromItem(player.getStackInHand(hand).getItem());
            int size = Claim.sizeFor(held);

            if (size > 0) {
                BlockPos target = hit.getBlockPos().offset(hit.getSide());

                if (CLAIMS.findAt(serverWorld, target) != null || CLAIMS.overlaps(serverWorld, target, size, null)) {
                    player.sendMessage(Text.literal("§cТут не можна створити приват — територія перетинається з іншим."), true);
                    return ActionResult.FAIL;
                }

                if (CLAIMS.findAt(serverWorld, player.getBlockPos()) != null) {
                    player.sendMessage(Text.literal("§cТи вже знаходишся в захищеній території."), true);
                    return ActionResult.FAIL;
                }

                Claim claim = CLAIMS.create(player, serverWorld, target, size, held);
                if (claim == null) {
                    player.sendMessage(Text.literal("§cНе вдалося створити приват."), true);
                    return ActionResult.FAIL;
                }

                player.sendMessage(Text.literal("§aПриват #" + claim.id + " створено: §f" + size + "x" + size), true);
                player.sendMessage(Text.literal("§7Інформація: §f/rg info " + claim.id), false);
                return ActionResult.PASS;
            }

            Claim claim = CLAIMS.findAt(serverWorld, hit.getBlockPos());
            if (claim != null && !claim.canBuild(player)) {
                player.sendMessage(Text.literal("§cЦя територія захищена! §7Приват #" + claim.id), true);
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });
    }

    public static class Claim {
        public int id;
        public UUID owner;
        public String ownerName;
        public String dimension;
        public int x, y, z, size;
        public String block;
        public List<String> members = new ArrayList<>();

        public Claim() {}

        public Claim(int id, UUID owner, String ownerName, String dimension, BlockPos pos, int size, Block block) {
            this.id = id;
            this.owner = owner;
            this.ownerName = ownerName;
            this.dimension = dimension;
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
            this.size = size;
            this.block = net.minecraft.registry.Registries.BLOCK.getId(block).toString();
        }

        public BlockPos anchor() { return new BlockPos(x, y, z); }

        public boolean canBuild(net.minecraft.entity.player.Player player) {
            return player.getUuid().equals(owner) || player.hasPermissionLevel(2) ||
                members.stream().anyMatch(n -> n.equalsIgnoreCase(player.getName().getString()));
        }

        public boolean contains(BlockPos pos) {
            int halfLeft = size / 2;
            int halfRight = size - halfLeft - 1;
            return pos.getX() >= x - halfLeft && pos.getX() <= x + halfRight
                && pos.getZ() >= z - halfLeft && pos.getZ() <= z + halfRight;
        }

        public boolean overlaps(BlockPos center, int otherSize) {
            int aLeft = size / 2;
            int aRight = size - aLeft - 1;
            int bLeft = otherSize / 2;
            int bRight = otherSize - bLeft - 1;
            return x - aLeft <= center.getX() + bRight &&
                x + aRight >= center.getX() - bLeft &&
                z - aLeft <= center.getZ() + bRight &&
                z + aRight >= center.getZ() - bLeft;
        }

        public static int sizeFor(Block block) {
            if (block == Blocks.IRON_BLOCK) return 10;
            if (block == Blocks.GOLD_BLOCK) return 15;
            if (block == Blocks.DIAMOND_BLOCK) return 20;
            if (block == Blocks.EMERALD_BLOCK) return 25;
            if (block == Blocks.NETHERITE_BLOCK) return 30;
            return 0;
        }
    }
}
