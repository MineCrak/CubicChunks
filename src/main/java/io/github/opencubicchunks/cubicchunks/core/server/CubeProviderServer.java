/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015-2019 OpenCubicChunks
 *  Copyright (c) 2015-2019 contributors
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
package io.github.opencubicchunks.cubicchunks.core.server;

import io.github.opencubicchunks.cubicchunks.core.CubicChunksConfig;
import io.github.opencubicchunks.cubicchunks.core.lighting.LightingManager;
import io.github.opencubicchunks.cubicchunks.core.server.chunkio.ICubeIO;
import io.github.opencubicchunks.cubicchunks.core.server.chunkio.RegionCubeIO;
import io.github.opencubicchunks.cubicchunks.core.server.chunkio.async.forge.AsyncWorldIOExecutor;
import io.github.opencubicchunks.cubicchunks.api.worldgen.CubePrimer;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.worldgen.ICubeGenerator;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer;
import io.github.opencubicchunks.cubicchunks.core.asm.CubicChunksMixinConfig;
import io.github.opencubicchunks.cubicchunks.api.util.Box;
import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.util.XYZMap;
import io.github.opencubicchunks.cubicchunks.core.world.ICubeProviderInternal;
import io.github.opencubicchunks.cubicchunks.core.asm.mixin.ICubicWorldInternal;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.core.world.cube.Cube;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.annotation.Detainted;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * This is CubicChunks equivalent of ChunkProviderServer, it loads and unloads Cubes and Columns.
 * <p>
 * There are a few necessary changes to the way vanilla methods work:
 * * Because loading a Chunk (Column) doesn't make much sense with CubicChunks,
 * all methods that load Chunks, actually load  an empry column with no blocks in it
 * (there may be some entities that are not in any Cube yet).
 * * dropChunk method is not supported. Columns are unloaded automatically when the last cube is unloaded
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class CubeProviderServer extends ChunkProviderServer implements ICubeProviderServer, ICubeProviderInternal.Server {

    @Nonnull private WorldServer worldServer;
    @Nonnull private ICubeIO cubeIO;

    // TODO: Use a better hash map!
    @Nonnull private XYZMap<Cube> cubeMap = new XYZMap<>(0.7f, 8000);

    @Nonnull private ICubeGenerator cubeGen;
    @Nonnull private Profiler profiler;

    public CubeProviderServer(WorldServer worldServer, ICubeGenerator cubeGen) {
        super(worldServer,
                worldServer.getSaveHandler().getChunkLoader(worldServer.provider), // forge uses this in
                worldServer.provider.createChunkGenerator()); // let's create the chunk generator, for now the vanilla one may be enough

        this.cubeGen = cubeGen;
        this.worldServer = worldServer;
        this.profiler = worldServer.profiler;
        try {
            this.cubeIO = new RegionCubeIO(worldServer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    @Detainted
    public void queueUnload(Chunk chunk) {
        //ignore, ChunkGc unloads cubes
    }

    @Override
    @Detainted
    public void queueUnloadAll() {
        //ignore, ChunkGc unloads cubes
    }

    /**
     * Vanilla method, returns a Chunk (Column) only of it's already loaded.
     */
    @Nullable @Override
    public Chunk getLoadedColumn(int columnX, int columnZ) {
        return this.loadedChunks.get(ChunkPos.asLong(columnX, columnZ));
    }

    @Nullable
    @Override
    @Deprecated
    public Chunk getLoadedChunk(int columnX, int columnZ) {
        return (Chunk) getLoadedColumn(columnX, columnZ);
    }

    /**
     * Loads Chunk (Column) if it can be loaded from disk, or returns already loaded one.
     * Doesn't generate new Columns.
     */
    @Nullable
    @Override
    @Deprecated
    public Chunk loadChunk(int columnX, int columnZ) {
        return this.loadChunk(columnX, columnZ, null);
    }

    /**
     * Load chunk asynchronously. Currently CubicChunks only loads synchronously.
     */
    @Nullable
    @Override
    @Deprecated
    public Chunk loadChunk(int columnX, int columnZ, @Nullable Runnable runnable) {
        // TODO: Set this to LOAD when PlayerCubeMap works
        if (runnable == null) {
            return getColumn(columnX, columnZ, /*Requirement.LOAD*/Requirement.LIGHT);
        }

        // TODO here too
        asyncGetColumn(columnX, columnZ, Requirement.LIGHT, col -> runnable.run());
        return null;
    }

    /**
     * If this Column is already loaded - returns it.
     * Loads from disk if possible, otherwise generates new Column.
     */
    @Override
    public Chunk provideColumn(int cubeX, int cubeZ) {
        return getColumn(cubeX, cubeZ, Requirement.GENERATE);
    }

    @Override
    @Deprecated
    public Chunk provideChunk(int cubeX, int cubeZ) {
        return (Chunk) provideColumn(cubeX, cubeZ);
    }

    @Override
    public boolean saveChunks(boolean alwaysTrue) {
        for (Cube cube : cubeMap) { // save cubes
            if (cube.needsSaving()) {
                this.cubeIO.saveCube(cube);
            }
        }
        for (Chunk chunk : loadedChunks.values()) { // save columns
            // save the column
            if (chunk.needsSaving(alwaysTrue)) {
                this.cubeIO.saveColumn(chunk);
            }
        }

        return true;
    }

    @Override
    public boolean tick() {
        // NOTE: the return value is completely ignored
        profiler.startSection("providerTick");
        long i = System.currentTimeMillis();
        Random rand = this.world.rand;
        PlayerCubeMap playerCubeMap = ((PlayerCubeMap) this.world.getPlayerChunkMap());
        Iterator<Cube> watchersIterator = playerCubeMap.getCubeIterator();
        BooleanSupplier tickFaster = () -> System.currentTimeMillis() - i > 40;
        while (watchersIterator.hasNext()) {
            watchersIterator.next().tickCubeServer(tickFaster, rand);
        }
        profiler.endSection();
        return false;
    }

    @Override
    public String makeString() {
        return "CubeProviderServer: " + this.loadedChunks.size() + " columns, "
                + this.cubeMap.getSize() + " cubes";
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(final EnumCreatureType type, final BlockPos pos) {
        return cubeGen.getPossibleCreatures(type, pos);
    }

    @Nullable @Override
    public BlockPos getNearestStructurePos(World worldIn, String name, BlockPos pos, boolean findUnexplored) {
        return cubeGen.getClosestStructure(name, pos, findUnexplored);
    }

    // getLoadedChunkCount() in ChunkProviderServer is fine - CHECKED: 1.10.2-12.18.1.2092

    @Override
    public boolean chunkExists(int cubeX, int cubeZ) {
        return this.loadedChunks.get(ChunkPos.asLong(cubeX, cubeZ)) != null;
    }

    @Override // TODO: What it does? implement it
    public boolean isInsideStructure(World p_193413_1_, String p_193413_2_, BlockPos p_193413_3_) {
        return false;
    }

    //==============================
    //=====CubicChunks methods======
    //==============================

    @Override
    public Cube getCube(int cubeX, int cubeY, int cubeZ) {
        return getCube(cubeX, cubeY, cubeZ, Requirement.GENERATE);
    }

    @Override
    public Cube getCube(CubePos coords) {
        return getCube(coords.getX(), coords.getY(), coords.getZ());
    }

    @Nullable @Override
    public Cube getLoadedCube(int cubeX, int cubeY, int cubeZ) {
        return cubeMap.get(cubeX, cubeY, cubeZ);
    }

    @Nullable @Override
    public Cube getLoadedCube(CubePos coords) {
        return getLoadedCube(coords.getX(), coords.getY(), coords.getZ());
    }

    /**
     * Load a cube, asynchronously. The work done to retrieve the column is specified by the
     * {@link Requirement} <code>req</code>
     *
     * @param cubeX Cube x position
     * @param cubeY Cube y position
     * @param cubeZ Cube z position
     * @param req Work done to retrieve the column
     * @param callback Callback to be called when the load finishes. Note that <code>null</code> can be passed to the
     * callback if the work specified by <code>req</code> is not sufficient to provide a cube
     *
     * @see #getCube(int, int, int, Requirement) for the synchronous equivalent to this method
     */
    public void asyncGetCube(int cubeX, int cubeY, int cubeZ, Requirement req, Consumer<Cube> callback) {
        Cube cube = getLoadedCube(cubeX, cubeY, cubeZ);
        if (req == Requirement.GET_CACHED || (cube != null && req.compareTo(Requirement.GENERATE) <= 0)) {
            callback.accept(cube);
            return;
        }

        if (cube == null) {
            AsyncWorldIOExecutor.queueCubeLoad(worldServer, cubeIO, this, cubeX, cubeY, cubeZ, loaded -> {
                Chunk col = getLoadedColumn(cubeX, cubeZ);
                if (col != null) {
                    onCubeLoaded(loaded, col);
                    loaded = postCubeLoadAttempt(cubeX, cubeY, cubeZ, loaded, col, req);
                }
                callback.accept(loaded);
            });
        }
    }

    @Nullable @Override
    public Cube getCube(int cubeX, int cubeY, int cubeZ, Requirement req) {
        Cube cube = getLoadedCube(cubeX, cubeY, cubeZ);
        if (req == Requirement.GET_CACHED ||
                (cube != null && req.compareTo(Requirement.GENERATE) <= 0)) {
            return cube;
        }

        // try to get the Column
        Chunk column = getColumn(cubeX, cubeZ, req);
        if (column == null) {
            return cube; // Column did not reach req, so Cube also does not
        }

        if (cube == null) {
            // a little hack to fix StackOverflowError when loading TileEntities, as Cube methods are now redirected into IColumn
            // Column needs cube to be loaded to add TileEntity, so make CubeProvider contain it already
            cube = AsyncWorldIOExecutor.syncCubeLoad(worldServer, cubeIO, this, cubeX, cubeY, cubeZ);
            onCubeLoaded(cube, column);
        }

        return postCubeLoadAttempt(cubeX, cubeY, cubeZ, cube, column, req);
    }

    @Override public boolean isCubeGenerated(int cubeX, int cubeY, int cubeZ) {
        return getLoadedCube(cubeX, cubeY, cubeZ) != null || cubeIO.cubeExists(cubeX, cubeY, cubeZ);
    }

    /**
     * After successfully loading a cube, add it to it's column and the lookup table
     *
     * @param cube The cube that was loaded
     * @param column The column of the cube
     */
    private void onCubeLoaded(@Nullable Cube cube, Chunk column) {
        if (cube != null) {
            cubeMap.put(cube); // cache the Cube
            //synchronous loading may cause it to be called twice when async loading has been already queued
            //because AsyncWorldIOExecutor only executes one task for one cube and because only saving a cube
            //can modify one that is being loaded, it's impossible to end up with 2 versions of the same cube
            //This is only to prevents multiple callbacks for the same queued load from adding the same cube twice.
            if (!((IColumn) column).getLoadedCubes().contains(cube)) {
                ((IColumn) column).addCube(cube);
                cube.onLoad(); // init the Cube
            }
        }
    }

    /**
     * Process a recently loaded cube as per the specified effort level.
     *
     * @param cubeX Cube x position
     * @param cubeY Cube y position
     * @param cubeZ Cube z positon
     * @param cube The loaded cube, if loaded, else <code>null</code>
     * @param column The column of the cube
     * @param req Work done on the cube
     *
     * @return The processed cube, or <code>null</code> if the effort level is not sufficient to provide a cube
     */
    @Nullable
    private Cube postCubeLoadAttempt(int cubeX, int cubeY, int cubeZ, @Nullable Cube cube, Chunk column, Requirement req) {
        // when async load+generate request is immediately followed by sync request,  the async one will generate the cube in callback, but it won't
        // change the async load request result, so the cube here will still be null. Just to make sure, get the cube here
        // otherwise we may end up generating the same cube twice
        if (cube == null) {
            cube = getLoadedCube(cubeX, cubeY, cubeZ);
        }
        // Fast path - Nothing to do here
        if (req == Requirement.LOAD) {
            return cube;
        }
        if (req == Requirement.GENERATE && cube != null) {
            return cube;
        }
        if (cube == null) {
            // generate the Cube
            cube = generateCube(cubeX, cubeY, cubeZ, column);
            if (req == Requirement.GENERATE) {
                return cube;
            }
        }

        if (!cube.isFullyPopulated()) {
            // forced full population of this cube
            populateCube(cube);
            if (req == Requirement.POPULATE) {
                return cube;
            }
        }

        //TODO: Direct skylight might have changed and even Cubes that have there
        //      initial light done, there might be work to do for a cube that just loaded
        if (!cube.isInitialLightingDone()) {
            calculateDiffuseSkylight(cube);
        }

        return cube;
    }


    /**
     * Generate a cube at the specified position
     *
     * @param cubeX Cube x position
     * @param cubeY Cube y position
     * @param cubeZ Cube z position
     * @param column Column of the cube
     *
     * @return The generated cube
     */
    private Cube generateCube(int cubeX, int cubeY, int cubeZ, Chunk column) {
        CubePrimer primer = cubeGen.generateCube(cubeX, cubeY, cubeZ);
        Cube cube = new Cube(column, cubeY, primer);

        onCubeLoaded(cube, column);

        ((ICubicWorldInternal.Server) this.worldServer).getFirstLightProcessor()
                .initializeSkylight(cube); // init sky light, (does not require any other cubes, just ServerHeightMap)

        return cube;
    }

    /**
     * Populate a cube at the specified position, generating surrounding cubes as necessary
     *
     * @param cube The cube to populate
     */
    private void populateCube(Cube cube) {
        int cubeX = cube.getX();
        int cubeY = cube.getY();
        int cubeZ = cube.getZ();

        // for all cubes needed for full population - generate their population requirements
        Box fullPopulation = cubeGen.getFullPopulationRequirements(cube);
        if (CubicChunksConfig.useVanillaChunkWorldGenerators) {
            if (cube.getY() >= 0 && cube.getY() < 16) {
                fullPopulation = new Box(
                        0, -cube.getY(), 0,
                        0, 16 - cube.getY() - 1, 0
                ).add(fullPopulation);
            }
        }
        fullPopulation.forEachPoint((x, y, z) -> {
            // this also generates the cube
            Cube fullPopulationCube = getCube(x + cubeX, y + cubeY, z + cubeZ);
            Box newBox = cubeGen.getPopulationPregenerationRequirements(fullPopulationCube);
            if (CubicChunksConfig.useVanillaChunkWorldGenerators) {
                if (cube.getY() >= 0 && cube.getY() < 16) {
                    newBox = new Box(
                            0, -cube.getY(), 0,
                            0, 16 - cube.getY() - 1, 0
                    ).add(newBox);
                }
            }
            newBox.forEachPoint((nx, ny, nz) -> {
                int genX = cubeX + x + nx;
                int genY = cubeY + y + ny;
                int genZ = cubeZ + z + nz;
                getCube(genX, genY, genZ);
            });
            // a check for populators that populate more than one cube (vanilla compatibility generator)
            if (!fullPopulationCube.isPopulated()) {
                cubeGen.populate(fullPopulationCube);
                fullPopulationCube.setPopulated(true);
            }
        });
        if (CubicChunksConfig.useVanillaChunkWorldGenerators) {
            Box.Mutable box = fullPopulation.asMutable();
            box.setY1(0);
            box.setY2(0);
            box.forEachPoint((x, y, z) -> {
                GameRegistry.generateWorld(cube.getX() + x, cube.getZ() + z, world, chunkGenerator, world.getChunkProvider());
            });
        }
        cube.setFullyPopulated(true);
    }

    /**
     * Initialize skylight for the cube at the specified position, generating surrounding cubes as needed.
     *
     * @param cube The cube to light up
     */
    private void calculateDiffuseSkylight(Cube cube) {
        if (LightingManager.NO_SUNLIGHT_PROPAGATION) {
            cube.setInitialLightingDone(true);
            return;
        }
        int cubeX = cube.getX();
        int cubeY = cube.getY();
        int cubeZ = cube.getZ();

        // TODO: remove this loop; Does it break anything?
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 2; y >= -2; y--) {
                    if (x != 0 || y != 0 || z != 0) {
                        getCube(x + cubeX, y + cubeY, z + cubeZ);
                    }
                }
            }
        }
        ((ICubicWorldInternal.Server) this.worldServer).getFirstLightProcessor().diffuseSkylight(cube);
    }


    /**
     * Retrieve a column, asynchronously. The work done to retrieve the column is specified by the
     * {@link Requirement} <code>req</code>
     *
     * @param columnX Column x position
     * @param columnZ Column z position
     * @param req Work done to retrieve the column
     * @param callback Callback to be called when the column has finished loading. Note that the returned column is not
     * guaranteed to be non-null
     *
     * @see CubeProviderServer#getColumn(int, int, Requirement) for the synchronous variant of this method
     */
    public void asyncGetColumn(int columnX, int columnZ, Requirement req, Consumer<Chunk> callback) {
        Chunk column = getLoadedColumn(columnX, columnZ);
        if (column != null || req == Requirement.GET_CACHED) {
            callback.accept(column);
            return;
        }

        AsyncWorldIOExecutor.queueColumnLoad(worldServer, cubeIO, columnX, columnZ, col -> {
            col = postProcessColumn(columnX, columnZ, col, req);
            callback.accept(col);
        });
    }

    @Nullable @Override
    public Chunk getColumn(int columnX, int columnZ, Requirement req) {
        Chunk column = getLoadedColumn(columnX, columnZ);
        if (column != null || req == Requirement.GET_CACHED) {
            return column;
        }

        column = AsyncWorldIOExecutor.syncColumnLoad(worldServer, cubeIO, columnX, columnZ);
        column = postProcessColumn(columnX, columnZ, column, req);

        return column;
    }

    /**
     * After loading a column, do work on it, where the work required is specified by <code>req</code>
     *
     * @param columnX X position of the column
     * @param columnZ Z position of the column
     * @param column The loaded column, or <code>null</code> if the column couldn't be loaded
     * @param req The amount of work to be done on the cube
     *
     * @return The postprocessed column, or <code>null</code>
     */
    @Nullable
    private Chunk postProcessColumn(int columnX, int columnZ, @Nullable Chunk column, Requirement req) {
        Chunk loaded = getLoadedColumn(columnX, columnZ);
        if (loaded != null) {
            if (column != null && loaded != column) {
                throw new IllegalStateException("Duplicate column at " + columnX + ", " + columnZ + "!");
            }
            return loaded;
        }
        if (column != null) {
            loadedChunks.put(ChunkPos.asLong(columnX, columnZ), (Chunk) column);
            column.setLastSaveTime(this.worldServer.getTotalWorldTime()); // the column was just loaded
            column.onLoad();
            return column;
        } else if (req == Requirement.LOAD) {
            return null;
        }

        column = (Chunk) new Chunk((World) worldServer, columnX, columnZ);
        cubeGen.generateColumn(column);

        loadedChunks.put(ChunkPos.asLong(columnX, columnZ), (Chunk) column);
        column.setLastSaveTime(this.worldServer.getTotalWorldTime()); // the column was just generated
        column.onLoad();
        return column;
    }

    public String dumpLoadedCubes() {
        StringBuilder sb = new StringBuilder(10000).append("\n");
        for (Chunk chunk : this.loadedChunks.values()) {
            if (chunk == null) {
                sb.append("column = null\n");
                continue;
            }
            sb.append("Column[").append(chunk.x).append(", ").append(chunk.z).append("] {");
            boolean isFirst = true;
            for (ICube cube : ((IColumn) chunk).getLoadedCubes()) {
                if (!isFirst) {
                    sb.append(", ");
                }
                isFirst = false;
                if (cube == null) {
                    sb.append("cube = null");
                    continue;
                }
                sb.append("Cube[").append(cube.getY()).append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override @Nonnull public ICubeIO getCubeIO() {
        return cubeIO;
    }

    Iterator<Cube> cubesIterator() {
        return cubeMap.iterator();
    }

    @SuppressWarnings("unchecked")
    Iterator<Chunk> columnsIterator() {
        return loadedChunks.values().iterator();
    }

    boolean tryUnloadCube(Cube cube) {
        if (ForgeChunkManager.getPersistentChunksFor(world).containsKey(cube.getColumn().getPos())) {
            return false; // it will be unloaded later by ChunkGC
        }
        if (!cube.getTickets().canUnload()) {
            return false; // There are tickets
        }

        // unload the Cube!
        cube.onUnload();

        if (cube.needsSaving()) { // save the Cube, if it needs saving
            this.cubeIO.saveCube(cube);
        }

        if (cube.getColumn().removeCube(cube.getY()) == null) {
            throw new RuntimeException();
        }
        return true;
    }

    boolean tryUnloadColumn(Chunk column) {
        if (ForgeChunkManager.getPersistentChunksFor(world).containsKey(column.getPos())) {
            return false; // it will be unloaded later by ChunkGC
        }
        if (((IColumn) column).hasLoadedCubes()) {
            return false; // It has loaded Cubes in it
            // (Cubes are to Columns, as tickets are to Cubes... in a way)
        }
        // ask async loader if there are currently any cubes being loaded for this column
        // this should prevent hard to debug issues with columns being unloaded while cubes have reference to them
        if (!AsyncWorldIOExecutor.canDropColumn(worldServer, column.x, column.z)) {
            return false;
        }
        column.unloadQueued = true;

        // unload the Column!
        column.onUnload();

        if (column.needsSaving(true)) { // save the Column, if it needs saving
            this.cubeIO.saveColumn(column);
        }
        return true;
    }

    public ICubeGenerator getCubeGenerator() {
        return cubeGen;
    }

    public int getLoadedCubeCount() {
        return cubeMap.getSize();
    }
}
