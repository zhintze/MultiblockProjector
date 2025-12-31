package com.multiblockprojector.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Extended interface for multiblocks that have positions where multiple block types are acceptable.
 * These positions cycle through the acceptable blocks in preview/projection rendering.
 *
 * Example: Blood Magic altar rune positions can accept any rune type (speed, sacrifice, capacity, etc.)
 */
public interface ICyclingBlockMultiblock extends IUniversalMultiblock {

    /**
     * Get all acceptable block states for a position that should cycle.
     * The renderer will cycle through these blocks in the preview.
     *
     * @param structurePos The position within the structure (relative to origin)
     * @return List of acceptable block states, or empty list if position doesn't cycle
     */
    List<BlockState> getAcceptableBlocks(BlockPos structurePos);

    /**
     * Check if a position has cycling/multiple acceptable blocks.
     *
     * @param structurePos The position within the structure (relative to origin)
     * @return true if this position accepts multiple block types
     */
    boolean hasCyclingBlocks(BlockPos structurePos);

    /**
     * Get the default block state for a cycling position.
     * Used by creative auto-build to place a specific default block.
     *
     * @param structurePos The position within the structure (relative to origin)
     * @return The default block state (e.g., blank rune), or the normal block if not cycling
     */
    BlockState getDefaultBlock(BlockPos structurePos);
}
