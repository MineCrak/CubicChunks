/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package io.github.opencubicchunks.cubicchunks.api.world;

import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public interface IColumn {

    /**
     * Return Y position of the block directly above the top non-transparent block, or {@link Coords#NO_HEIGHT} + 1 if
     * there are no non-transparent blocks
     * <p>
     * CHECKED: 1.11-13.19.0.2148
     */
    int getHeight(BlockPos pos);

    /**
     * Return Y position of the block directly above the top non-transparent block, or {@link Coords#NO_HEIGHT} + 1 if
     * there are no non-transparent blocks
     * <p>
     * CHECKED: 1.11-13.19.0.2148
     */
    int getHeightValue(int localX, int localZ);


    /**
     * Check if this column needs to be ticked
     *
     * @return <code>true</code> if any cube in this column needs to be ticked, <code>false</code> otherwise
     */
    boolean shouldTick();

    /**
     * @return the height map of this column
     */
    IHeightMap getOpacityIndex();

    /**
     * Retrieve all cubes in this column that are currently loaded
     *
     * @return the cubes
     */
    Collection<? extends ICube> getLoadedCubes();

    /**
     * Iterate over all loaded cubes in this column in order. If {@code startY < endY}, order is bottom to top,
     * otherwise order is top to bottom.
     *
     * @param startY initial cube y position
     * @param endY last cube y position
     *
     * @return an iterator over all loaded cubes between {@code startY} and {@code endY}
     */
    Iterable<? extends ICube> getLoadedCubes(int startY, int endY);

    /**
     * Retrieve the cube at the specified location if it is loaded.
     *
     * @param cubeY cube y position
     *
     * @return the cube at that position, or <code>null</code> if it is not loaded
     */
    @Nullable ICube getLoadedCube(int cubeY);

    /**
     * Retrieve the cube at the specified location
     *
     * @param cubeY cube y position
     *
     * @return the cube at that position
     */
    ICube getCube(int cubeY);

    /**
     * Add a cube to this column
     *
     * @param cube the cube being added
     */
    void addCube(ICube cube);

    /**
     * Remove the cube at the specified height
     *
     * @param cubeY cube y position
     *
     * @return the removed cube if it existed, otherwise <code>null</code>
     */
    @Nullable ICube removeCube(int cubeY);

    /**
     * Check if there are any loaded cube in this column
     *
     * @return <code>true</code> if there is at least on loaded cube in this column, <code>false</code> otherwise
     */
    boolean hasLoadedCubes();

    /**
     * Note: this method is intended for internal use only.
     *
     * Make the chunk ready to use this cube for the next block operation.
     * This cube will be used only if the coordinates match.
     */
    void preCacheCube(ICube cube);

}
