package com.multiblockprojector.api.adapters;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.api.ICyclingBlockMultiblock;
import com.multiblockprojector.api.IUniversalMultiblock;
import com.multiblockprojector.api.UniversalMultiblockHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Adapter for Blood Magic altar tiers with cycling rune support.
 * Blood Magic uses a tiered altar system with runes, pillars, and capstones.
 * Rune positions can accept any type of rune, which cycle in the preview.
 */
public class BloodMagicMultiblockAdapter {

    // Block states loaded via reflection from Blood Magic
    private static BlockState bloodAltarBlock;
    private static BlockState bloodstoneBlock;
    private static BlockState bloodstoneBrickBlock;
    private static BlockState hellforgedBlock;
    private static BlockState crystalClusterBlock;

    // All rune block states - basic runes (11 types)
    private static final List<BlockState> BASIC_RUNES = new ArrayList<>();
    private static final List<BlockState> ALL_RUNES = new ArrayList<>(); // Basic + Tier-2 (21 types)

    // Default rune (blank) for creative auto-build
    private static BlockState defaultRuneBlock;

    // Fallback blocks if reflection fails
    private static final BlockState FALLBACK_ALTAR = Blocks.OBSIDIAN.defaultBlockState();
    private static final BlockState FALLBACK_RUNE = Blocks.NETHER_BRICKS.defaultBlockState();
    private static final BlockState FALLBACK_PILLAR = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState FALLBACK_GLOWSTONE = Blocks.GLOWSTONE.defaultBlockState();
    private static final BlockState FALLBACK_BLOODSTONE = Blocks.RED_NETHER_BRICKS.defaultBlockState();
    private static final BlockState FALLBACK_HELLFORGED = Blocks.NETHERITE_BLOCK.defaultBlockState();
    private static final BlockState FALLBACK_CRYSTAL = Blocks.AMETHYST_BLOCK.defaultBlockState();

    // Fallback runes for when Blood Magic isn't loaded
    private static final List<BlockState> FALLBACK_BASIC_RUNES = List.of(
        Blocks.NETHER_BRICKS.defaultBlockState(),
        Blocks.RED_NETHER_BRICKS.defaultBlockState(),
        Blocks.CHISELED_NETHER_BRICKS.defaultBlockState(),
        Blocks.CRACKED_NETHER_BRICKS.defaultBlockState(),
        Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
    );

    private static boolean blocksLoaded = false;

    // Basic rune field names (11 types)
    private static final String[] BASIC_RUNE_NAMES = {
        "RUNE_BLANK", "RUNE_SPEED", "RUNE_SACRIFICE", "RUNE_SELF_SACRIFICE",
        "RUNE_CAPACITY", "RUNE_CAPACITY_AUGMENTED", "RUNE_CHARGING",
        "RUNE_ACCELERATION", "RUNE_DISLOCATION", "RUNE_ORB", "RUNE_EFFICIENCY"
    };

    // Tier-2 rune field names (10 types - no RUNE_2_BLANK)
    private static final String[] TIER2_RUNE_NAMES = {
        "RUNE_2_SPEED", "RUNE_2_SACRIFICE", "RUNE_2_SELF_SACRIFICE",
        "RUNE_2_CAPACITY", "RUNE_2_CAPACITY_AUGMENTED", "RUNE_2_CHARGING",
        "RUNE_2_ACCELERATION", "RUNE_2_DISLOCATION", "RUNE_2_ORB", "RUNE_2_EFFICIENCY"
    };

    /**
     * Load Blood Magic blocks via reflection
     */
    private static void loadBlocks() {
        if (blocksLoaded) return;
        blocksLoaded = true;

        try {
            Class<?> bmBlocksClass = Class.forName("wayoftime.bloodmagic.common.block.BMBlocks");

            bloodAltarBlock = getBlockStateFromHolder(bmBlocksClass, "BLOOD_ALTAR");
            bloodstoneBlock = getBlockStateFromHolder(bmBlocksClass, "BLOODSTONE");
            bloodstoneBrickBlock = getBlockStateFromHolder(bmBlocksClass, "BLOODSTONE_BRICK");
            hellforgedBlock = getBlockStateFromHolder(bmBlocksClass, "HELLFORGED_BLOCK");
            crystalClusterBlock = getBlockStateFromHolder(bmBlocksClass, "CRYSTAL_CLUSTER");

            // Load all basic runes
            for (String runeName : BASIC_RUNE_NAMES) {
                BlockState runeState = getBlockStateFromHolder(bmBlocksClass, runeName);
                if (runeState != null) {
                    BASIC_RUNES.add(runeState);
                    ALL_RUNES.add(runeState);
                    if (runeName.equals("RUNE_BLANK")) {
                        defaultRuneBlock = runeState;
                    }
                }
            }

            // Load all tier-2 runes
            for (String runeName : TIER2_RUNE_NAMES) {
                BlockState runeState = getBlockStateFromHolder(bmBlocksClass, runeName);
                if (runeState != null) {
                    ALL_RUNES.add(runeState);
                }
            }

            UniversalProjector.LOGGER.info("Successfully loaded Blood Magic blocks: {} basic runes, {} total runes",
                BASIC_RUNES.size(), ALL_RUNES.size());
        } catch (Exception e) {
            UniversalProjector.LOGGER.warn("Failed to load Blood Magic blocks via reflection, using fallbacks: {}", e.getMessage());
            bloodAltarBlock = FALLBACK_ALTAR;
            bloodstoneBlock = FALLBACK_BLOODSTONE;
            bloodstoneBrickBlock = FALLBACK_PILLAR;
            hellforgedBlock = FALLBACK_HELLFORGED;
            crystalClusterBlock = FALLBACK_CRYSTAL;

            // Use fallback runes
            BASIC_RUNES.addAll(FALLBACK_BASIC_RUNES);
            ALL_RUNES.addAll(FALLBACK_BASIC_RUNES);
            defaultRuneBlock = FALLBACK_RUNE;
        }

        // Ensure we have at least one rune
        if (BASIC_RUNES.isEmpty()) {
            BASIC_RUNES.add(FALLBACK_RUNE);
            ALL_RUNES.add(FALLBACK_RUNE);
        }
        if (defaultRuneBlock == null) {
            defaultRuneBlock = BASIC_RUNES.get(0);
        }
    }

    /**
     * Get block state from a BlockWithItemHolder field
     */
    private static BlockState getBlockStateFromHolder(Class<?> bmBlocksClass, String fieldName) {
        try {
            Field field = bmBlocksClass.getField(fieldName);
            Object holder = field.get(null);

            // BlockWithItemHolder has a block() method that returns DeferredHolder<Block, ?>
            Method blockMethod = holder.getClass().getMethod("block");
            Object deferredHolder = blockMethod.invoke(holder);

            // DeferredHolder has a get() method
            Method getMethod = deferredHolder.getClass().getMethod("get");
            Block block = (Block) getMethod.invoke(deferredHolder);

            return block.defaultBlockState();
        } catch (Exception e) {
            UniversalProjector.LOGGER.debug("Failed to load Blood Magic block {}: {}", fieldName, e.getMessage());
            return null;
        }
    }

    /**
     * Register all Blood Magic altar tiers
     */
    public static void registerAllMultiblocks() {
        loadBlocks();

        UniversalProjector.LOGGER.info("Registering Blood Magic altar tiers...");

        // Register all 6 altar tiers
        UniversalMultiblockHandler.registerMultiblock(new AltarTier1Multiblock());
        UniversalMultiblockHandler.registerMultiblock(new AltarTier2Multiblock());
        UniversalMultiblockHandler.registerMultiblock(new AltarTier3Multiblock());
        UniversalMultiblockHandler.registerMultiblock(new AltarTier4Multiblock());
        UniversalMultiblockHandler.registerMultiblock(new AltarTier5Multiblock());
        UniversalMultiblockHandler.registerMultiblock(new AltarTier6Multiblock());

        UniversalProjector.LOGGER.info("Registered 6 Blood Magic altar tiers");
    }

    // Helper methods to get the effective block states
    private static BlockState getAltarBlock() {
        return bloodAltarBlock != null ? bloodAltarBlock : FALLBACK_ALTAR;
    }

    private static BlockState getRuneBlock() {
        return defaultRuneBlock != null ? defaultRuneBlock : FALLBACK_RUNE;
    }

    private static BlockState getPillarBlock() {
        return bloodstoneBrickBlock != null ? bloodstoneBrickBlock : FALLBACK_PILLAR;
    }

    private static BlockState getT3Capstone() {
        return FALLBACK_GLOWSTONE; // Glowstone is always available
    }

    private static BlockState getT4Capstone() {
        return bloodstoneBlock != null ? bloodstoneBlock : FALLBACK_BLOODSTONE;
    }

    private static BlockState getT5Capstone() {
        return hellforgedBlock != null ? hellforgedBlock : FALLBACK_HELLFORGED;
    }

    private static BlockState getT6Capstone() {
        return crystalClusterBlock != null ? crystalClusterBlock : FALLBACK_CRYSTAL;
    }

    /**
     * Get runes for a specific altar tier.
     * Tier 2: Basic runes only (11)
     * Tier 3+: All runes (21)
     */
    private static List<BlockState> getRunesForTier(int tier) {
        if (tier <= 2) {
            return new ArrayList<>(BASIC_RUNES);
        } else {
            return new ArrayList<>(ALL_RUNES);
        }
    }

    // ============================================
    // Base class for altar tiers with cycling support
    // ============================================
    private static abstract class BaseAltarMultiblock implements ICyclingBlockMultiblock {
        protected final Set<BlockPos> runePositions = new HashSet<>();
        protected final int tier;

        protected BaseAltarMultiblock(int tier) {
            this.tier = tier;
        }

        @Override
        public List<BlockState> getAcceptableBlocks(BlockPos structurePos) {
            if (runePositions.contains(structurePos)) {
                return getRunesForTier(tier);
            }
            return List.of();
        }

        @Override
        public boolean hasCyclingBlocks(BlockPos structurePos) {
            return runePositions.contains(structurePos);
        }

        @Override
        public BlockState getDefaultBlock(BlockPos structurePos) {
            if (runePositions.contains(structurePos)) {
                return getRuneBlock(); // Blank rune for creative auto-build
            }
            // For non-rune positions, return null to indicate use normal block
            return null;
        }

        @Override
        public String getModId() { return "bloodmagic"; }

        @Override
        public String getCategory() { return "altar"; }

        protected void addRunePosition(BlockPos pos) {
            runePositions.add(pos.immutable());
        }
    }

    // ============================================
    // Tier 1: Weak - Just the Blood Altar (no cycling)
    // ============================================
    private static class AltarTier1Multiblock implements IUniversalMultiblock {
        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("bloodmagic", "altar_tier_1");
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Blood Altar - Tier 1 (Weak)");
        }

        @Override
        public Vec3i getSize(@Nonnull Level world) {
            return new Vec3i(1, 1, 1);
        }

        @Override
        public List<StructureBlockInfo> getStructure(@Nonnull Level world) {
            List<StructureBlockInfo> blocks = new ArrayList<>();
            blocks.add(new StructureBlockInfo(BlockPos.ZERO, getAltarBlock(), null));
            return blocks;
        }

        @Override
        public float getManualScale() { return 2.0f; }

        @Override
        public String getModId() { return "bloodmagic"; }

        @Override
        public String getCategory() { return "altar"; }
    }

    // ============================================
    // Tier 2: Apprentice - Altar + 8 runes in 3x3 below
    // ============================================
    private static class AltarTier2Multiblock extends BaseAltarMultiblock {
        public AltarTier2Multiblock() {
            super(2);
        }

        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("bloodmagic", "altar_tier_2");
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Blood Altar - Tier 2 (Apprentice)");
        }

        @Override
        public Vec3i getSize(@Nonnull Level world) {
            return new Vec3i(3, 2, 3);
        }

        @Override
        public List<StructureBlockInfo> getStructure(@Nonnull Level world) {
            List<StructureBlockInfo> blocks = new ArrayList<>();
            runePositions.clear();

            // Altar at center, y=1
            blocks.add(new StructureBlockInfo(new BlockPos(1, 1, 1), getAltarBlock(), null));

            // 8 runes in 3x3 pattern below altar (y=0), excluding center
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    if (x == 1 && z == 1) continue; // Skip center (below altar)
                    BlockPos pos = new BlockPos(x, 0, z);
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            return blocks;
        }

        @Override
        public float getManualScale() { return 1.5f; }
    }

    // ============================================
    // Tier 3: Mage - Larger rune ring + 4 pillars with glowstone caps
    // ============================================
    private static class AltarTier3Multiblock extends BaseAltarMultiblock {
        public AltarTier3Multiblock() {
            super(3);
        }

        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("bloodmagic", "altar_tier_3");
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Blood Altar - Tier 3 (Mage)");
        }

        @Override
        public Vec3i getSize(@Nonnull Level world) {
            return new Vec3i(7, 4, 7);
        }

        @Override
        public List<StructureBlockInfo> getStructure(@Nonnull Level world) {
            List<StructureBlockInfo> blocks = new ArrayList<>();
            runePositions.clear();
            int centerX = 3, centerZ = 3, altarY = 2;

            // Altar at center
            blocks.add(new StructureBlockInfo(new BlockPos(centerX, altarY, centerZ), getAltarBlock(), null));

            // 3x3 runes below altar (y=1)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos pos = new BlockPos(centerX + dx, altarY - 1, centerZ + dz);
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            // Outer rune ring at y=0, distance 3
            for (int i = -2; i <= 2; i++) {
                BlockPos[] positions = {
                    new BlockPos(centerX + i, altarY - 2, centerZ + 3),
                    new BlockPos(centerX + i, altarY - 2, centerZ - 3),
                    new BlockPos(centerX + 3, altarY - 2, centerZ + i),
                    new BlockPos(centerX - 3, altarY - 2, centerZ + i)
                };
                for (BlockPos pos : positions) {
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            // 4 pillars at corners with glowstone caps
            int[][] pillarPositions = {{3, 3}, {3, -3}, {-3, 3}, {-3, -3}};
            for (int[] pos : pillarPositions) {
                for (int dy = -1; dy <= 0; dy++) {
                    blocks.add(new StructureBlockInfo(
                        new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                        getPillarBlock(), null));
                }
                // Glowstone cap on top
                blocks.add(new StructureBlockInfo(
                    new BlockPos(centerX + pos[0], altarY + 1, centerZ + pos[1]),
                    getT3Capstone(), null));
            }

            return blocks;
        }

        @Override
        public float getManualScale() { return 0.7f; }
    }

    // ============================================
    // Tier 4: Master - Even larger with outer pillars
    // ============================================
    private static class AltarTier4Multiblock extends BaseAltarMultiblock {
        public AltarTier4Multiblock() {
            super(4);
        }

        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("bloodmagic", "altar_tier_4");
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Blood Altar - Tier 4 (Master)");
        }

        @Override
        public Vec3i getSize(@Nonnull Level world) {
            return new Vec3i(11, 6, 11);
        }

        @Override
        public List<StructureBlockInfo> getStructure(@Nonnull Level world) {
            List<StructureBlockInfo> blocks = new ArrayList<>();
            runePositions.clear();
            int centerX = 5, centerZ = 5, altarY = 3;

            // Altar at center
            blocks.add(new StructureBlockInfo(new BlockPos(centerX, altarY, centerZ), getAltarBlock(), null));

            // Tier 2 runes (3x3 below altar)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos pos = new BlockPos(centerX + dx, altarY - 1, centerZ + dz);
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            // Tier 3 outer rune ring at distance 3
            for (int i = -2; i <= 2; i++) {
                BlockPos[] positions = {
                    new BlockPos(centerX + i, altarY - 2, centerZ + 3),
                    new BlockPos(centerX + i, altarY - 2, centerZ - 3),
                    new BlockPos(centerX + 3, altarY - 2, centerZ + i),
                    new BlockPos(centerX - 3, altarY - 2, centerZ + i)
                };
                for (BlockPos pos : positions) {
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            // Tier 3 inner pillars with glowstone
            int[][] innerPillars = {{3, 3}, {3, -3}, {-3, 3}, {-3, -3}};
            for (int[] pos : innerPillars) {
                for (int dy = -1; dy <= 0; dy++) {
                    blocks.add(new StructureBlockInfo(
                        new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                        getPillarBlock(), null));
                }
                blocks.add(new StructureBlockInfo(
                    new BlockPos(centerX + pos[0], altarY + 1, centerZ + pos[1]),
                    getT3Capstone(), null));
            }

            // Tier 4 outer rune ring at distance 5
            for (int i = -3; i <= 3; i++) {
                BlockPos[] positions = {
                    new BlockPos(centerX + i, altarY - 3, centerZ + 5),
                    new BlockPos(centerX + i, altarY - 3, centerZ - 5),
                    new BlockPos(centerX + 5, altarY - 3, centerZ + i),
                    new BlockPos(centerX - 5, altarY - 3, centerZ + i)
                };
                for (BlockPos pos : positions) {
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            // Tier 4 outer pillars at distance 5
            int[][] outerPillars = {{5, 5}, {5, -5}, {-5, 5}, {-5, -5}};
            for (int[] pos : outerPillars) {
                for (int dy = -2; dy <= 1; dy++) {
                    blocks.add(new StructureBlockInfo(
                        new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                        getPillarBlock(), null));
                }
                // Bloodstone cap
                blocks.add(new StructureBlockInfo(
                    new BlockPos(centerX + pos[0], altarY + 2, centerZ + pos[1]),
                    getT4Capstone(), null));
            }

            return blocks;
        }

        @Override
        public float getManualScale() { return 0.5f; }
    }

    // ============================================
    // Tier 5: Archmage - Large ring with hellforged caps
    // ============================================
    private static class AltarTier5Multiblock extends BaseAltarMultiblock {
        public AltarTier5Multiblock() {
            super(5);
        }

        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("bloodmagic", "altar_tier_5");
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Blood Altar - Tier 5 (Archmage)");
        }

        @Override
        public Vec3i getSize(@Nonnull Level world) {
            return new Vec3i(17, 7, 17);
        }

        @Override
        public List<StructureBlockInfo> getStructure(@Nonnull Level world) {
            List<StructureBlockInfo> blocks = new ArrayList<>();
            runePositions.clear();
            int centerX = 8, centerZ = 8, altarY = 4;

            // Altar at center
            blocks.add(new StructureBlockInfo(new BlockPos(centerX, altarY, centerZ), getAltarBlock(), null));

            // Tier 2 runes (3x3 below altar)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos pos = new BlockPos(centerX + dx, altarY - 1, centerZ + dz);
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            // Tier 3 rune ring at distance 3
            for (int i = -2; i <= 2; i++) {
                BlockPos[] positions = {
                    new BlockPos(centerX + i, altarY - 2, centerZ + 3),
                    new BlockPos(centerX + i, altarY - 2, centerZ - 3),
                    new BlockPos(centerX + 3, altarY - 2, centerZ + i),
                    new BlockPos(centerX - 3, altarY - 2, centerZ + i)
                };
                for (BlockPos pos : positions) {
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            // Tier 3 pillars with glowstone
            int[][] tier3Pillars = {{3, 3}, {3, -3}, {-3, 3}, {-3, -3}};
            for (int[] pos : tier3Pillars) {
                for (int dy = -1; dy <= 0; dy++) {
                    blocks.add(new StructureBlockInfo(
                        new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                        getPillarBlock(), null));
                }
                blocks.add(new StructureBlockInfo(
                    new BlockPos(centerX + pos[0], altarY + 1, centerZ + pos[1]),
                    getT3Capstone(), null));
            }

            // Tier 4 rune ring at distance 5
            for (int i = -3; i <= 3; i++) {
                BlockPos[] positions = {
                    new BlockPos(centerX + i, altarY - 3, centerZ + 5),
                    new BlockPos(centerX + i, altarY - 3, centerZ - 5),
                    new BlockPos(centerX + 5, altarY - 3, centerZ + i),
                    new BlockPos(centerX - 5, altarY - 3, centerZ + i)
                };
                for (BlockPos pos : positions) {
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            // Tier 4 pillars with bloodstone
            int[][] tier4Pillars = {{5, 5}, {5, -5}, {-5, 5}, {-5, -5}};
            for (int[] pos : tier4Pillars) {
                for (int dy = -2; dy <= 1; dy++) {
                    blocks.add(new StructureBlockInfo(
                        new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                        getPillarBlock(), null));
                }
                blocks.add(new StructureBlockInfo(
                    new BlockPos(centerX + pos[0], altarY + 2, centerZ + pos[1]),
                    getT4Capstone(), null));
            }

            // Tier 5 rune ring at distance 8
            for (int i = -6; i <= 6; i++) {
                BlockPos[] positions = {
                    new BlockPos(centerX + i, altarY - 4, centerZ + 8),
                    new BlockPos(centerX + i, altarY - 4, centerZ - 8),
                    new BlockPos(centerX + 8, altarY - 4, centerZ + i),
                    new BlockPos(centerX - 8, altarY - 4, centerZ + i)
                };
                for (BlockPos pos : positions) {
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            // Tier 5 corner capstones (hellforged)
            int[][] tier5Caps = {{8, 8}, {8, -8}, {-8, 8}, {-8, -8}};
            for (int[] pos : tier5Caps) {
                blocks.add(new StructureBlockInfo(
                    new BlockPos(centerX + pos[0], altarY - 4, centerZ + pos[1]),
                    getT5Capstone(), null));
            }

            return blocks;
        }

        @Override
        public float getManualScale() { return 0.35f; }
    }

    // ============================================
    // Tier 6: Transcendent - Largest altar with crystal caps
    // ============================================
    private static class AltarTier6Multiblock extends BaseAltarMultiblock {
        public AltarTier6Multiblock() {
            super(6);
        }

        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("bloodmagic", "altar_tier_6");
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Blood Altar - Tier 6 (Transcendent)");
        }

        @Override
        public Vec3i getSize(@Nonnull Level world) {
            return new Vec3i(23, 9, 23);
        }

        @Override
        public List<StructureBlockInfo> getStructure(@Nonnull Level world) {
            List<StructureBlockInfo> blocks = new ArrayList<>();
            runePositions.clear();
            int centerX = 11, centerZ = 11, altarY = 5;

            // Altar at center
            blocks.add(new StructureBlockInfo(new BlockPos(centerX, altarY, centerZ), getAltarBlock(), null));

            // Tier 2 runes (3x3 below altar)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos pos = new BlockPos(centerX + dx, altarY - 1, centerZ + dz);
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            // Tier 3 rune ring at distance 3
            for (int i = -2; i <= 2; i++) {
                BlockPos[] positions = {
                    new BlockPos(centerX + i, altarY - 2, centerZ + 3),
                    new BlockPos(centerX + i, altarY - 2, centerZ - 3),
                    new BlockPos(centerX + 3, altarY - 2, centerZ + i),
                    new BlockPos(centerX - 3, altarY - 2, centerZ + i)
                };
                for (BlockPos pos : positions) {
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            // Tier 3 pillars with glowstone
            int[][] tier3Pillars = {{3, 3}, {3, -3}, {-3, 3}, {-3, -3}};
            for (int[] pos : tier3Pillars) {
                for (int dy = -1; dy <= 0; dy++) {
                    blocks.add(new StructureBlockInfo(
                        new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                        getPillarBlock(), null));
                }
                blocks.add(new StructureBlockInfo(
                    new BlockPos(centerX + pos[0], altarY + 1, centerZ + pos[1]),
                    getT3Capstone(), null));
            }

            // Tier 4 rune ring at distance 5
            for (int i = -3; i <= 3; i++) {
                BlockPos[] positions = {
                    new BlockPos(centerX + i, altarY - 3, centerZ + 5),
                    new BlockPos(centerX + i, altarY - 3, centerZ - 5),
                    new BlockPos(centerX + 5, altarY - 3, centerZ + i),
                    new BlockPos(centerX - 5, altarY - 3, centerZ + i)
                };
                for (BlockPos pos : positions) {
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            // Tier 4 pillars with bloodstone
            int[][] tier4Pillars = {{5, 5}, {5, -5}, {-5, 5}, {-5, -5}};
            for (int[] pos : tier4Pillars) {
                for (int dy = -2; dy <= 1; dy++) {
                    blocks.add(new StructureBlockInfo(
                        new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                        getPillarBlock(), null));
                }
                blocks.add(new StructureBlockInfo(
                    new BlockPos(centerX + pos[0], altarY + 2, centerZ + pos[1]),
                    getT4Capstone(), null));
            }

            // Tier 5 rune ring at distance 8
            for (int i = -6; i <= 6; i++) {
                BlockPos[] positions = {
                    new BlockPos(centerX + i, altarY - 4, centerZ + 8),
                    new BlockPos(centerX + i, altarY - 4, centerZ - 8),
                    new BlockPos(centerX + 8, altarY - 4, centerZ + i),
                    new BlockPos(centerX - 8, altarY - 4, centerZ + i)
                };
                for (BlockPos pos : positions) {
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            // Tier 5 corner capstones (hellforged)
            int[][] tier5Caps = {{8, 8}, {8, -8}, {-8, 8}, {-8, -8}};
            for (int[] pos : tier5Caps) {
                blocks.add(new StructureBlockInfo(
                    new BlockPos(centerX + pos[0], altarY - 4, centerZ + pos[1]),
                    getT5Capstone(), null));
            }

            // Tier 6 rune ring at distance 11
            for (int i = -9; i <= 9; i++) {
                BlockPos[] positions = {
                    new BlockPos(centerX + i, altarY - 5, centerZ + 11),
                    new BlockPos(centerX + i, altarY - 5, centerZ - 11),
                    new BlockPos(centerX + 11, altarY - 5, centerZ + i),
                    new BlockPos(centerX - 11, altarY - 5, centerZ + i)
                };
                for (BlockPos pos : positions) {
                    blocks.add(new StructureBlockInfo(pos, getRuneBlock(), null));
                    addRunePosition(pos);
                }
            }

            // Tier 6 tall pillars at distance 11
            int[][] tier6Pillars = {{11, 11}, {11, -11}, {-11, 11}, {-11, -11}};
            for (int[] pos : tier6Pillars) {
                for (int dy = -4; dy <= 2; dy++) {
                    blocks.add(new StructureBlockInfo(
                        new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                        getPillarBlock(), null));
                }
                // Crystal cluster cap on top
                blocks.add(new StructureBlockInfo(
                    new BlockPos(centerX + pos[0], altarY + 3, centerZ + pos[1]),
                    getT6Capstone(), null));
            }

            return blocks;
        }

        @Override
        public float getManualScale() { return 0.25f; }
    }
}
