package gregtech.common;

import static gregtech.api.enums.GTValues.debugWorldGen;
import static gregtech.api.enums.GTValues.profileWorldGen;

import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.WorldEvent;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;

import cpw.mods.fml.common.IWorldGenerator;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import galacticgreg.api.ModDimensionDef;
import galacticgreg.api.enums.DimensionDef;
import gregtech.GTMod;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.StoneType;
import gregtech.api.events.VeinGenerateEvent;
import gregtech.api.net.GTPacketSendOregenPattern;
import gregtech.api.objects.XSTR;
import gregtech.api.util.GTLog;
import gregtech.api.world.GTWorldgen;
import gregtech.common.worldgen.WorldgenQuery;

public class GTWorldgenerator implements IWorldGenerator {

    private static final int MAX_VEIN_SIZE = 2; // in chunks
    public static OregenPattern oregenPattern = OregenPattern.AXISSYMMETRICAL;
    private static final List<WorldGenContainer> PENDING_TASKS = Collections.synchronizedList(new LinkedList<>());

    public static Hashtable<Long, OreVein> validOreVeins = new Hashtable<>(1024);
    public static OreVein noOresInVein = new OreVein();
    public boolean mIsGenerating = false;

    public GTWorldgenerator() {
        // The weight here is irrelevant since the code in GameRegistryMixin forces GTWorldgenerator to the end of the
        // list.
        GameRegistry.registerWorldGenerator(this, Integer.MAX_VALUE);
        if (debugWorldGen) {
            GTLog.out.println("GTWorldgenerator created");
        }
    }

    @Override
    public void generate(Random aRandom, int aX, int aZ, World aWorld, IChunkProvider aChunkGenerator,
        IChunkProvider aChunkProvider) {

        ModDimensionDef def = DimensionDef.getEffectiveDefForChunk(aWorld, aX, aZ);

        if (def == null || !def.generatesOre()) {
            return;
        }

        PENDING_TASKS.add(
            new WorldGenContainer(
                new XSTR(Math.abs(aRandom.nextInt()) + 1),
                aX,
                aZ,
                aWorld,
                aChunkGenerator,
                aChunkProvider,
                aWorld.getBiomeGenForCoords(aX * 16 + 8, aZ * 16 + 8).biomeName));
        if (debugWorldGen) GTLog.out.println(
            "ADD WorldSeed:" + aWorld.getSeed()
                + " DimName"
                + aWorld.provider.getDimensionName()
                + " chunk x:"
                + aX
                + " z:"
                + aZ
                + " SIZE: "
                + PENDING_TASKS.size());

        // Hack to prevent cascading worldgen lag
        if (!this.mIsGenerating) {
            this.mIsGenerating = true;

            // Run a maximum of 5 chunks at a time through worldgen. Extra chunks get done later.
            for (int i = 0; i < Math.min(PENDING_TASKS.size(), 5); i++) {
                WorldGenContainer task = PENDING_TASKS.remove(0);

                if (debugWorldGen) GTLog.out.println(
                    "RUN WorldSeed:" + aWorld.getSeed()
                        + " DimId"
                        + aWorld.provider.dimensionId
                        + " chunk x:"
                        + task.mX
                        + " z:"
                        + task.mZ
                        + " SIZE: "
                        + PENDING_TASKS.size()
                        + " i: "
                        + i);

                task.run();
            }
            this.mIsGenerating = false;
        }
    }

    public static boolean isOreChunk(int chunkX, int chunkZ) {
        if (oregenPattern == OregenPattern.EQUAL_SPACING) {
            return Math.floorMod(chunkX, 3) == 1 && Math.floorMod(chunkZ, 3) == 1;
        }
        // add next if statement here or convert to switch when expanding OregenPattern enum

        // AXISSYMMETRICAL
        return Math.abs(chunkX) % 3 == 1 && Math.abs(chunkZ) % 3 == 1;
    }

    public static class OregenPatternSavedData extends WorldSavedData {

        private static final String NAME = "GregTech_OregenPattern";
        private static final String KEY = "oregenPattern";

        public OregenPatternSavedData(String p_i2141_1_) {
            super(p_i2141_1_);
        }

        public static void loadData(World world) {
            if (world.getWorldInfo()
                .getWorldTotalTime() == 0L) {
                // The world has just been created -> use newest pattern
                oregenPattern = OregenPattern.values()[OregenPattern.values().length - 1];
            } else {
                // This is an old world. Use legacy pattern for now, readFromNBT may change this if
                // GregTech_OregenPattern.dat is present
                oregenPattern = OregenPattern.AXISSYMMETRICAL;
            }

            // load OregenPatternSavedData
            WorldSavedData instance = world.mapStorage
                .loadData(OregenPatternSavedData.class, OregenPatternSavedData.NAME);
            if (instance == null) {
                instance = new OregenPatternSavedData(NAME);
                world.mapStorage.setData(OregenPatternSavedData.NAME, instance);
            }
            instance.markDirty();
        }

        @SubscribeEvent
        public void onWorldLoad(WorldEvent.Load event) {
            final World world = event.world;
            if (!world.isRemote && world.provider.dimensionId == 0) {
                loadData(world);
            }
        }

        @SubscribeEvent
        public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.player instanceof EntityPlayerMP player) {
                GTValues.NW.sendToPlayer(new GTPacketSendOregenPattern(oregenPattern), player);
            }
        }

        @Override
        public void readFromNBT(NBTTagCompound p_76184_1_) {
            if (p_76184_1_.hasKey(KEY)) {
                int ordinal = p_76184_1_.getByte(KEY);
                ordinal = MathHelper.clamp_int(ordinal, 0, OregenPattern.values().length - 1);
                oregenPattern = OregenPattern.values()[ordinal];
            }
        }

        @Override
        public void writeToNBT(NBTTagCompound p_76187_1_) {
            // If we have so many different OregenPatterns that byte isn't good enough something is wrong
            p_76187_1_.setByte(KEY, (byte) oregenPattern.ordinal());
        }

    }

    public enum OregenPattern {
        // The last value is used when creating a new world
        AXISSYMMETRICAL,
        EQUAL_SPACING
    }

    public static class WorldGenContainer implements Runnable {

        public final Random mRandom;
        public final int mX;
        public final int mZ;
        public final World mWorld;
        public final IChunkProvider mChunkGenerator;
        public final IChunkProvider mChunkProvider;
        public final String mBiome;
        // Used for outputting orevein weights and bins
        // static int test=0;

        // aX and aZ are now the by-chunk X and Z for the chunk of interest
        public WorldGenContainer(Random aRandom, int aX, int aZ, World aWorld, IChunkProvider aChunkGenerator,
            IChunkProvider aChunkProvider, String aBiome) {
            this.mRandom = aRandom;
            this.mX = aX;
            this.mZ = aZ;
            this.mWorld = aWorld;
            this.mChunkGenerator = aChunkGenerator;
            this.mChunkProvider = aChunkProvider;
            this.mBiome = aBiome;
        }

        /*
         * How to evaluate oregen distribution
         * - Enable debugOreveins
         * - Fly around for a while, or teleport jumping ~320 blocks at a time, with
         * a 15-30s pause for worldgen to catch up
         * - Do this across a large area, at least 2000x2000 blocks for good numbers
         * - Open logs\gregtech.log
         * - Using notepad++, do a Search | Find - enter "Added" for the search term
         * - Select Find All In Current Document
         * - In the Search window, right-click and Select All
         * - Copy and paste to a new file
         * - Delete extraneous stuff at top, and blank line at bottom. Line count is
         * # of total oreveins
         * - For simple spot checks, use Find All in Current Document for specific
         * oremixes, ie ore.mix.diamond, to check how many appear in the list.
         * - For more complex work, import file into Excel, and sort based on oremix
         * column. Drag select the oremix names, in the bottom right will be how many
         * entries to add in a separate tab to calculate %ages.
         * When using the ore weights, discount or remove the high altitude veins since
         * their high weight are offset by their rareness. I usually just use zero for them.
         * Actual spawn rates will vary based upon the average height of the stone layers
         * in the dimension. For example veins that range above and below the average height
         * will be less, and veins that are completely above the average height will be much less.
         */

        public void generateVein(int oreSeedX, int oreSeedZ) {
            /*
             * Explanation of oreveinseed implementation.
             * (long)this.mWorld.getSeed()<<16) Deep Dark does two oregen passes, one with getSeed set to +1 the
             * original world seed. This pushes that +1 off the low bits of oreSeedZ, so that the hashes are far apart
             * for the two passes.
             * ((this.mWorld.provider.dimensionId & 0xffL)<<56) Puts the dimension in the top bits of the hash, to
             * make sure to get unique hashes per dimension
             * ((long)oreSeedX & 0x000000000fffffffL) << 28) Puts the chunk X in the bits 29-55. Cuts off the top few
             * bits of the chunk so we have bits for dimension.
             * ( (long)oreSeedZ & 0x000000000fffffffL )) Puts the chunk Z in the bits 0-27. Cuts off the top few bits
             * of the chunk so we have bits for dimension.
             */
            long oreVeinSeed = (this.mWorld.getSeed() << 16)
                ^ (((this.mWorld.provider.dimensionId & 0xffL) << 56) | (((long) oreSeedX & 0x000000000fffffffL) << 28)
                    | ((long) oreSeedZ & 0x000000000fffffffL)); // Use an RNG that is identical every time it is
                                                                // called for this oreseed.

            String dimensionName = DimensionDef.getDimensionName(this.mWorld);

            XSTR oreVeinRNG = new XSTR(oreVeinSeed);
            int oreVeinPercentageRoll = oreVeinRNG.nextInt(100); // Roll the dice, see if we get an orevein here at all

            if (validOreVeins.containsKey(oreVeinSeed)) { // Oreseed is located in the previously processed table
                validOreVeins.get(oreVeinSeed)
                    .executeOreGen(mWorld, oreVeinRNG, mBiome, mX, mZ, oreVeinSeed, mChunkGenerator, mChunkProvider);
                return;
            }

            ModDimensionDef dimensionDef = DimensionDef.getDefForWorld(mWorld);

            if (oreVeinPercentageRoll >= dimensionDef.getOreVeinChance()) {
                validOreVeins.put(oreVeinSeed, noOresInVein);
                return;
            } // Empty ore vein

            /*
             * if( test==0 ) { test = 1; GTLog.out.println( "sWeight = " + GT_Worldgen_GT_Ore_Layer.sWeight );
             * for (GT_Worldgen_GT_Ore_Layer tWorldGen : GT_Worldgen_GT_Ore_Layer.sList) { GTLog.out.println( (
             * tWorldGen).mWorldGenName + " mWeight = " + ( tWorldGen).mWeight + " mSize = " + (tWorldGen).mSize
             * ); } }
             */ // Used for outputting orevein weights and bins
            boolean oreVeinFound = false;
            int placementAttempts = 0, i1 = 0;
            XSTR veinRNG = new XSTR(0);

            if (!dimensionDef.respectsOreVeinHeights()) {
                long seed = Fnv1a64.initialState();
                seed = Fnv1a64.hashStep(seed, oreVeinSeed);
                seed = Fnv1a64.hashStep(seed, i1);
                veinRNG.setSeed(seed);
                Chunk chunk = mWorld.getChunkFromChunkCoords(oreSeedX, oreSeedZ);
                WorldgenGTOreLayer oreLayer = WorldgenQuery.veins()
                    .inDimension(dimensionName)
                    .findRandom(veinRNG);

                int minY = mWorld.getActualHeight();
                int maxY = 0;

                // Most EBS's will be empty in the end, so instead of doing a naive 0-256 scan we can check each one
                // separately.
                // This is also faster than World.getBlock since there are fewer lookups needed to get a block.
                for (ExtendedBlockStorage ebs : chunk.getBlockStorageArray()) {
                    if (ebs == null) continue;

                    for (int y = 0; y < 16; y++) {
                        Block block = ebs.getBlockByExtId(7, y, 7);

                        int realY = y + ebs.getYLocation();

                        if (block
                            .isBlockSolid(mWorld, oreSeedX + 7, realY, oreSeedX + 7, ForgeDirection.UP.ordinal())) {
                            minY = Math.min(minY, realY);
                            maxY = Math.max(maxY, realY);
                        }
                    }
                }
                validOreVeins.put(
                    oreVeinSeed,
                    generateVeinSize(oreLayer, veinRNG, oreSeedX, oreSeedZ, minY + veinRNG.nextInt(maxY - minY + 1)));
                generateVein(oreSeedX, oreSeedZ);
                return;
            }

            for (i1 = 0; i1 < 256 && placementAttempts < 256 && !oreVeinFound; i1++) {
                long seed = Fnv1a64.initialState();
                seed = Fnv1a64.hashStep(seed, oreVeinSeed);
                seed = Fnv1a64.hashStep(seed, i1);
                veinRNG.setSeed(seed);

                WorldgenGTOreLayer oreLayer = WorldgenQuery.veins()
                    .inDimension(dimensionName)
                    .findRandom(veinRNG);
                if (oreLayer == null) break; // No veins in this dimension

                int veinY = veinHeight(oreLayer, veinRNG, oreSeedX, oreSeedZ);
                placementAttempts++;
                if (veinY == -1) continue;

                validOreVeins.put(oreVeinSeed, generateVeinSize(oreLayer, veinRNG, oreSeedX, oreSeedZ, veinY));
                generateVein(oreSeedX, oreSeedZ);
                return;
            }

            // Ore gen failed to find valid vein
            if (debugWorldGen) GTLog.out.format("Vein at (%d %d) failed to generate", oreSeedX * 16, oreSeedZ * 16);

            // Forcing vein to generate at y5
            int height = 64, i2;
            WorldgenGTOreLayer oreLayer = null;

            for (i2 = 0; i2 < 256 && height > 10; i2++) {
                oreLayer = WorldgenQuery.veins()
                    .inDimension(dimensionName)
                    .findRandom(veinRNG);
                height = oreLayer.getMinY(); // oreLayer can't be null here
            }

            if (height <= 10) {
                validOreVeins.put(oreVeinSeed, generateVeinSize(oreLayer, veinRNG, oreSeedX, oreSeedZ, 5));
                generateVein(oreSeedX, oreSeedZ);
            } else {
                validOreVeins.put(oreVeinSeed, noOresInVein);
            }
        }

        public int veinHeight(WorldgenGTOreLayer oreLayer, Random rng, int oreSeedX, int oreSeedZ) {
            int veinY = oreLayer.mMinY + rng.nextInt(oreLayer.mMaxY - oreLayer.mMinY - 5);
            oreSeedX *= 16;
            oreSeedZ *= 16;

            int airCount = 0;
            for (int i1 = veinY; i1 < veinY + 9; i1 += 2) {
                if (StoneType.findStoneType(mWorld, oreSeedX, i1, oreSeedZ) == null) airCount++;
                if (StoneType.findStoneType(mWorld, oreSeedX + 15, i1, oreSeedZ) == null) airCount++;
                if (StoneType.findStoneType(mWorld, oreSeedX, i1, oreSeedZ + 15) == null) airCount++;
                if (StoneType.findStoneType(mWorld, oreSeedX + 15, i1, oreSeedZ + 15) == null) airCount++;
            }

            return airCount < 6 ? veinY : -1;
        }

        public OreVein generateVeinSize(WorldgenGTOreLayer oreLayer, Random rng, int oreSeedX, int oreSeedZ,
            int veinY) {
            short size = oreLayer.mSize;

            oreSeedX *= 16;
            oreSeedZ *= 16;
            int veinWestX = oreSeedX - rng.nextInt(size);
            int veinEastX = oreSeedX + 16 + rng.nextInt(size);
            int veinNorthZ = oreSeedZ - rng.nextInt(size);
            int veinSouthZ = oreSeedZ + 16 + rng.nextInt(size);

            return new OreVein(oreLayer, veinWestX, veinEastX, veinNorthZ, veinSouthZ, veinY, oreSeedX, oreSeedZ);
        }

        @Override
        public void run() {
            long startTime = System.nanoTime();
            Chunk tChunk = this.mWorld.getChunkFromChunkCoords(this.mX, this.mZ);

            // Do GT_Stones and GT_small_ores oregen for this chunk
            try {
                for (GTWorldgen tWorldGen : GregTechAPI.sWorldgenList) {
                    /*
                     * if (debugWorldGen) GTLog.out.println( "tWorldGen.mWorldGenName="+tWorldGen.mWorldGenName );
                     */
                    tWorldGen.executeWorldgen(
                        this.mWorld,
                        this.mRandom,
                        this.mBiome,
                        this.mX * 16,
                        this.mZ * 16,
                        this.mChunkGenerator,
                        this.mChunkProvider);
                }
            } catch (Exception e) {
                e.printStackTrace(GTLog.err);
            }

            long stonegenTime = System.nanoTime();

            int chunkMinX = this.mX - MAX_VEIN_SIZE;
            int chunkMaxX = this.mX + MAX_VEIN_SIZE + 1; // Need to add 1 since it is compared using a <
            int chunkMinZ = this.mZ - MAX_VEIN_SIZE;
            int chunkMaxZ = this.mZ + MAX_VEIN_SIZE + 1;

            // Search for orevein seeds and add to the list;
            for (int x = chunkMinX; x < chunkMaxX; x++) {
                for (int z = chunkMinZ; z < chunkMaxZ; z++) {
                    // Determine if this X/Z is an orevein seed
                    if (isOreChunk(x, z)) {
                        if (debugWorldGen) GTLog.out.println("Processing seed x=" + x + " z=" + z);
                        generateVein(x, z);
                    }
                }
            }

            long oregenTime = System.nanoTime();

            if (tChunk != null) {
                tChunk.isModified = true;
            }

            long endTime = System.nanoTime();

            if (debugWorldGen || profileWorldGen) {
                GTMod.GT_FML_LOGGER.info(
                    " Oregen took " + (oregenTime - stonegenTime) / 1e3
                        + "us Stonegen took "
                        + (stonegenTime - startTime) / 1e3
                        + "us Worldgen took "
                        + (endTime - startTime) / 1e3
                        + "us");
            }
        }
    }

    @Desugar
    private record OreVein(WorldgenGTOreLayer ore, int veinWestX, int veinEastX, int veinNorthZ, int veinSouthZ,
        int veinMinY, int seedX, int seedZ) {

        public OreVein() {
            this(null, 0, 0, 0, 0, 0, 0, 0);
        }

        public WorldgenGTOreLayer getOreLayer() {
            return this.ore;
        }

        public void executeOreGen(World world, Random rng, String biome, int x, int z, long oreVeinSeed,
            IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {
            if (ore == null) return;

            rng.setSeed(oreVeinSeed ^ ore.mPrimary.getId());
            int placementResult = ore.executeWorldgenChunkified(
                world,
                rng,
                biome,
                x * 16,
                z * 16,
                seedX,
                seedZ,
                veinWestX,
                veinEastX,
                veinNorthZ,
                veinSouthZ,
                veinMinY,
                chunkGenerator,
                chunkProvider);

            VeinGenerateEvent event = new VeinGenerateEvent(world, x, z, seedX >> 4, seedZ >> 4, ore, placementResult);
            MinecraftForge.EVENT_BUS.post(event);
        }
    }
}
