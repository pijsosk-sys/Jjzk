package com.roothack.overlord.process;

import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;

import com.roothack.overlord.combat.CombatModule;
import com.roothack.overlord.evasion.SpectatorWatch;
import com.roothack.overlord.stages.Stage;
import com.roothack.overlord.util.BlockPlacer;
import com.roothack.overlord.util.CraftingHelper;
import com.roothack.overlord.util.EnchantHelper;
import com.roothack.overlord.util.Humanizer;
import com.roothack.overlord.util.InventoryHelper;
import com.roothack.overlord.util.SmeltingHelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * A Baritone process that walks the {@link Stage} ladder from an empty
 * inventory to a fully enchanted netherite loadout.
 *
 * <p>Control model: on each tick the process looks at the current stage. Mining
 * stages delegate to Baritone's own mine process (via {@code mineByName}) and
 * return {@link PathingCommandType#DEFER} so the lower-priority mine process
 * drives movement; the stage advances once the inventory target is met and the
 * mine process is cancelled. Crafting / smelting / enchanting stages act on
 * whatever screen handler is open and advance on the resulting inventory state.
 *
 * <p>Interaction-heavy stages (placing and opening a table, furnace, portal or
 * enchanting table) require the player to be aimed at valid ground; the process
 * aims straight down before issuing the use so the common flat-ground case
 * works unattended.
 */
public class ProgressionProcess implements IBaritoneProcess {

    private IBaritone baritone;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final Humanizer human = new Humanizer();
    private final CombatModule combat = new CombatModule(human);
    private final SpectatorWatch spectator = new SpectatorWatch(human);

    private boolean enabled = false;
    private Stage stage = Stage.GATHER_WOOD;
    private int cooldown = 0; // ticks to wait after a screen action

    public ProgressionProcess() {
    }

    /** Bound once Baritone's primary instance exists. */
    public void attach(IBaritone baritone) {
        this.baritone = baritone;
        applyFastHumanSettings();
    }

    /**
     * Snappy decisions that still look human: fast path recalculation and
     * sprinting on, with smoothed looking and anti-cheat-safe movement so the
     * speed never reads as teleport-straight botting.
     */
    private void applyFastHumanSettings() {
        var s = baritone.settings();
        s.allowSprint.value = true;
        s.sprintInWater.value = true;
        s.allowParkour.value = true;
        s.allowParkourPlace.value = true;
        s.smoothLook.value = true;          // eased camera, not snap turns
        s.randomLooking.value = true;       // small human-looking variance
        s.antiCheatCompatibility.value = true;
        s.blockBreakSpeed.value = 100;      // legit break speed, never faster
        s.planningTickLookahead.value = 200; // think further ahead each tick
        s.movementTimeoutTicks.value = 100;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCamouflage() {
        return spectator.isCamouflage();
    }

    /**
     * Fight and camouflage pass. Driven every client tick by the entry point so
     * mobs are answered and spectators are handled even between Baritone's own
     * process ticks. Returns true when it has taken over the view this tick.
     */
    public boolean defendTick() {
        if (!enabled || baritone == null || mc.player == null) return false;
        spectator.tick(mc, baritone);
        return combat.tick(mc);
    }

    public boolean isAttached() {
        return baritone != null;
    }

    public void start() {
        if (baritone == null) return;
        enabled = true;
        stage = firstUnmetStage();
    }

    public void stop() {
        enabled = false;
        if (baritone == null) return;
        baritone.getMineProcess().cancel();
        baritone.getCustomGoalProcess().onLostControl();
    }

    public Stage currentStage() {
        return stage;
    }

    @Override
    public boolean isActive() {
        return enabled && baritone != null && stage != Stage.DONE && mc.player != null;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (mc.player == null) {
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        // Fight first. If a hostile is in melee reach the combat module has the
        // view; hold position and swing rather than pathing away mid-fight.
        if (combat.currentTarget() != null
                && mc.player.squaredDistanceTo(combat.currentTarget()) <= 9.0) {
            baritone.getMineProcess().cancel();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Under a spectator's eye, stop the blatant tells: let the bot breathe
        // between actions and drift like a person instead of grinding visibly.
        if (spectator.isCamouflage()) {
            human.idleTwitch(mc, 0.2);
            if (spectator.spectatorNearby() && human.rng().nextInt(3) != 0) {
                return defer(); // mostly idle while actively observed up close
            }
        }

        if (cooldown > 0) {
            cooldown--;
            return defer();
        }

        return switch (stage) {
            case GATHER_WOOD           -> mineUntil(4, "oak_log", "birch_log", "spruce_log",
                                                       "jungle_log", "acacia_log", "dark_oak_log");
            case CRAFT_BASICS          -> craftBasics();
            case GATHER_STONE          -> mineUntil(11, "stone", "cobblestone");
            case CRAFT_STONE           -> craftStoneTier();
            case GATHER_IRON           -> mineUntil(7, "iron_ore", "deepslate_iron_ore");
            case SMELT_IRON            -> smelt(Items.RAW_IRON, Items.IRON_INGOT, 7);
            case CRAFT_IRON            -> craftIronTier();
            case GATHER_DIAMONDS       -> mineDeepUntil(-54, 10, "diamond_ore", "deepslate_diamond_ore");
            case CRAFT_DIAMOND         -> craftDiamondTier();
            case BUILD_PORTAL          -> mineUntil(10, "obsidian");
            case ENTER_NETHER          -> advanceWhen(mc.player.getWorld()
                                               .getRegistryKey().getValue().getPath().contains("nether"));
            case GATHER_ANCIENT_DEBRIS -> mineDeepUntil(15, 4, "ancient_debris");
            case SMELT_NETHERITE       -> smelt(Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP, 4);
            case CRAFT_NETHERITE       -> craftNetheriteTier();
            case GATHER_XP             -> advanceWhen(mc.player.experienceLevel >= 30);
            case SETUP_ENCHANTING      -> advanceWhen(InventoryHelper.has(mc, Items.ENCHANTING_TABLE, 1)
                                               && InventoryHelper.has(mc, Items.BOOKSHELF, 15));
            case ENCHANT_GEAR          -> enchantAll();
            case DONE                  -> stop2();
        };
    }

    // --- stage primitives -------------------------------------------------

    private PathingCommand mineUntil(int quantity, String... blocks) {
        Item target = reward(blocks[0]);
        if (InventoryHelper.count(mc, target) >= quantity) {
            baritone.getMineProcess().cancel();
            return advance();
        }
        if (!baritone.getMineProcess().isActive()) {
            baritone.getMineProcess().mineByName(quantity, blocks);
        }
        return defer();
    }

    /** Drop to a y-level first, then strip-mine the target ore. */
    private PathingCommand mineDeepUntil(int y, int quantity, String... blocks) {
        Item target = reward(blocks[0]);
        if (InventoryHelper.count(mc, target) >= quantity) {
            baritone.getMineProcess().cancel();
            return advance();
        }
        if (mc.player.getBlockY() > y + 2) {
            return new PathingCommand(new GoalYLevel(y), PathingCommandType.SET_GOAL_AND_PATH);
        }
        if (!baritone.getMineProcess().isActive()) {
            baritone.getMineProcess().mineByName(quantity, blocks);
        }
        return defer();
    }

    private PathingCommand smelt(Item input, Item output, int quantity) {
        if (InventoryHelper.count(mc, output) >= quantity) {
            if (mc.currentScreen != null) mc.player.closeHandledScreen();
            return advance();
        }
        if (!placeAndOpen(Items.FURNACE)) return defer();
        SmeltingHelper.load(mc, firstFuel(), input);
        SmeltingHelper.collect(mc);
        cooldown = 20; // let the furnace tick
        return defer();
    }

    private PathingCommand craftBasics() {
        craftInInventory("minecraft:oak_planks");
        craftInInventory("minecraft:crafting_table");
        if (!placeAndOpen(Items.CRAFTING_TABLE)) return defer();
        CraftingHelper.craftAll(mc, "minecraft:stick", 0);
        CraftingHelper.craftAll(mc, "minecraft:wooden_pickaxe", 0);
        if (InventoryHelper.has(mc, Items.WOODEN_PICKAXE, 1)) {
            mc.player.closeHandledScreen();
            return advance();
        }
        return defer();
    }

    private PathingCommand craftStoneTier() {
        if (!placeAndOpen(Items.CRAFTING_TABLE)) return defer();
        CraftingHelper.craftAll(mc, "minecraft:stone_pickaxe", 0);
        CraftingHelper.craftAll(mc, "minecraft:stone_sword", 0);
        CraftingHelper.craftAll(mc, "minecraft:furnace", 0);
        if (InventoryHelper.has(mc, Items.STONE_PICKAXE, 1)
                && InventoryHelper.has(mc, Items.FURNACE, 1)) {
            mc.player.closeHandledScreen();
            return advance();
        }
        return defer();
    }

    private PathingCommand craftIronTier() {
        if (!placeAndOpen(Items.CRAFTING_TABLE)) return defer();
        for (String r : new String[]{
                "minecraft:iron_pickaxe", "minecraft:iron_sword",
                "minecraft:iron_helmet", "minecraft:iron_chestplate",
                "minecraft:iron_leggings", "minecraft:iron_boots"}) {
            CraftingHelper.craftAll(mc, r, 0);
        }
        if (InventoryHelper.has(mc, Items.IRON_CHESTPLATE, 1)) {
            mc.player.closeHandledScreen();
            return advance();
        }
        return defer();
    }

    private PathingCommand craftDiamondTier() {
        if (!placeAndOpen(Items.CRAFTING_TABLE)) return defer();
        for (String r : new String[]{
                "minecraft:diamond_pickaxe", "minecraft:diamond_sword",
                "minecraft:diamond_helmet", "minecraft:diamond_chestplate",
                "minecraft:diamond_leggings", "minecraft:diamond_boots"}) {
            CraftingHelper.craftAll(mc, r, 0);
        }
        if (InventoryHelper.has(mc, Items.DIAMOND_CHESTPLATE, 1)) {
            mc.player.closeHandledScreen();
            return advance();
        }
        return defer();
    }

    private PathingCommand craftNetheriteTier() {
        if (!placeAndOpen(Items.CRAFTING_TABLE)) return defer();
        // Netherite ingot = 4 scrap + 4 gold ingot; upgrades need a smithing
        // table, driven separately once ingots exist.
        CraftingHelper.craftAll(mc, "minecraft:netherite_ingot", 0);
        if (InventoryHelper.has(mc, Items.NETHERITE_INGOT, 1)) {
            upgradeAllToNetherite();
        }
        if (InventoryHelper.has(mc, Items.NETHERITE_CHESTPLATE, 1)) {
            mc.player.closeHandledScreen();
            return advance();
        }
        return defer();
    }

    private void upgradeAllToNetherite() {
        if (!placeAndOpen(Items.SMITHING_TABLE)) return;
        // Smithing: slot 0 template, 1 base gear, 2 netherite ingot, 3 result.
        for (String r : new String[]{
                "minecraft:netherite_pickaxe_smithing", "minecraft:netherite_sword_smithing",
                "minecraft:netherite_helmet_smithing", "minecraft:netherite_chestplate_smithing",
                "minecraft:netherite_leggings_smithing", "minecraft:netherite_boots_smithing"}) {
            CraftingHelper.craftAll(mc, r, 3);
        }
    }

    private PathingCommand enchantAll() {
        if (!placeAndOpen(Items.ENCHANTING_TABLE)) return defer();
        Item[] gear = {
                Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE,
                Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
                Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
        for (Item g : gear) {
            if (mc.player.experienceLevel >= 30 && InventoryHelper.has(mc, g, 1)) {
                EnchantHelper.enchantTopTier(mc, g);
                cooldown = 10;
            }
        }
        if (mc.player.experienceLevel < 3) {
            mc.player.closeHandledScreen();
            return advance(); // out of levels; loadout is as enchanted as XP allowed
        }
        return defer();
    }

    // --- helpers ----------------------------------------------------------

    /** Aim down, place the block if needed, and open its screen. */
    private boolean placeAndOpen(Item block) {
        if (mc.currentScreen != null) return true; // already open
        if (!InventoryHelper.has(mc, block, 1)) return false;
        BlockPlacer.selectInHotbar(mc, block);
        // Point straight down at the block we stand on.
        mc.player.setPitch(90f);
        BlockPlacer.placeOnLookedAt(mc);
        cooldown = 4;
        return mc.currentScreen != null;
    }

    private void craftInInventory(String recipeId) {
        // The 2x2 player grid is always available without a screen.
        CraftingHelper.craftAll(mc, recipeId, 0);
    }

    private PathingCommand advance() {
        stage = stage.next();
        cooldown = 2;
        return defer();
    }

    private PathingCommand advanceWhen(boolean condition) {
        return condition ? advance() : defer();
    }

    private PathingCommand stop2() {
        stop();
        return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
    }

    private PathingCommand defer() {
        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    private Stage firstUnmetStage() {
        // Resume sensibly after a death/relog by jumping to the first stage
        // whose output we do not yet hold.
        if (!InventoryHelper.has(mc, Items.NETHERITE_CHESTPLATE, 1)) {
            if (InventoryHelper.has(mc, Items.NETHERITE_INGOT, 1)) return Stage.CRAFT_NETHERITE;
            if (InventoryHelper.has(mc, Items.DIAMOND_CHESTPLATE, 1)) return Stage.GATHER_ANCIENT_DEBRIS;
            if (InventoryHelper.has(mc, Items.IRON_CHESTPLATE, 1)) return Stage.GATHER_DIAMONDS;
            if (InventoryHelper.has(mc, Items.STONE_PICKAXE, 1)) return Stage.GATHER_IRON;
            if (InventoryHelper.has(mc, Items.WOODEN_PICKAXE, 1)) return Stage.GATHER_STONE;
        }
        return Stage.GATHER_WOOD;
    }

    /** Map a mined block id to the item it yields, for counting. */
    private Item reward(String blockId) {
        return switch (blockId) {
            case "iron_ore", "deepslate_iron_ore"       -> Items.RAW_IRON;
            case "diamond_ore", "deepslate_diamond_ore"  -> Items.DIAMOND;
            case "ancient_debris"                         -> Items.ANCIENT_DEBRIS;
            case "stone", "cobblestone"                   -> Items.COBBLESTONE;
            case "obsidian"                               -> Items.OBSIDIAN;
            default                                       -> Items.OAK_LOG;
        };
    }

    private Item firstFuel() {
        if (InventoryHelper.has(mc, Items.COAL, 1)) return Items.COAL;
        if (InventoryHelper.has(mc, Items.CHARCOAL, 1)) return Items.CHARCOAL;
        return Items.OAK_PLANKS;
    }

    // --- IBaritoneProcess boilerplate ------------------------------------

    @Override
    public void onLostControl() {
        baritone.getMineProcess().cancel();
    }

    @Override
    public double priority() {
        return 10.0; // above the default mine process so we steer it
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public String displayName0() {
        return "Overlord: " + stage.name() + " — " + stage.description;
    }
}
