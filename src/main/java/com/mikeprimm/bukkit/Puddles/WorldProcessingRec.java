package com.mikeprimm.bukkit.Puddles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;

public class WorldProcessingRec {
    private final World world;
    private final int maxy;
    private ArrayList<ChunkCoord> chunksToDo = new ArrayList<ChunkCoord>();
    private int index = 0;
    private int chunksPerTick;
    private int blocksPerChunk;
    private int minTickInterval;
    private int intervalCnt;
    
    private static final int BIGPRIME = 15485867;
    private Random rnd = new Random();

    private static class ChunkCoord {
        int x, z;
    }
    private static class BlockCoord {
        int x, y, z;
        BlockCoord(int x, int y, int z) {
            this.x = x; this.y = y; this.z = z;
        }
        @Override
        public int hashCode() {
            return x ^ (y << 8) ^ (z << 16);
        }
        @Override
        public boolean equals(Object o) {
            if (o instanceof BlockCoord) {
                BlockCoord c = (BlockCoord) o;
                return (this.x == c.x) && (this.y == c.y) && (this.z == c.z);  
            }
            return false;
        }
    }
        
    public WorldProcessingRec(Puddles puddles, ConfigurationSection cs, World w) {
        world = w;
        maxy = w.getMaxHeight();
        if (cs != null) {
            chunksPerTick = cs.getInt("chunks-per-tick", puddles.chunksPerTick);
            blocksPerChunk = cs.getInt("blocks-per-chunk", puddles.blocksPerChunk);
            minTickInterval = cs.getInt("min-tick-interval", puddles.minTickInterval);
        }
        else {
            chunksPerTick = puddles.chunksPerTick;
            blocksPerChunk = puddles.blocksPerChunk;
            minTickInterval = puddles.minTickInterval;
        }
    }
    
    public void processTick(Puddles drift) {
        intervalCnt--;
        for (int i = 0; i < chunksPerTick; i++) {
            if (index == 0) {   /* No chunks queued */
                if (intervalCnt > 0) { // Too soon for traversing same chunk set
                    return;
                }
                intervalCnt = minTickInterval; // reset interval
                Chunk[] chunks = world.getLoadedChunks();    /* Get list of loaded chunks */
                int cnt = chunks.length;
                if (cnt == 0) {
                    return;
                }
                chunksToDo.clear();
                int ord = (BIGPRIME + rnd.nextInt(cnt)) % cnt;
                for (int j = 0; j < chunks.length; j++) {
                    if (chunks[ord] != null) {
                        ChunkCoord cc = new ChunkCoord();
                        cc.x = chunks[ord].getX();
                        cc.z = chunks[ord].getZ();
                        chunksToDo.add(cc);
                        chunks[ord] = null;
                    }
                    ord = (ord + BIGPRIME) % cnt;
                }
                index = chunksToDo.size();
            }
            // Get next chunk coord
            index--;
            ChunkCoord coord = chunksToDo.get(index);
            // Get chunk to tick : confirm all neighbor are loaded
            boolean loaded = true;
            for (int xx = coord.x - 1; loaded && (xx <= coord.x + 1); xx++) {
                for (int zz = coord.z - 1; loaded && (zz <= coord.z + 1); zz++) {
                    if (world.isChunkLoaded(xx, zz) == false) {
                        loaded = false;
                    }
                }
            }
            if (loaded)
                tickChunk(drift, coord.x << 4, coord.z << 4);
        }
    }
    private void tickChunk(Puddles puddle, int x0, int z0) {
        for (int i = 0; i < blocksPerChunk; i++) {
            int x = rnd.nextInt(16) + x0;
            int z = rnd.nextInt(16) + z0;
            ConfigRec cr = puddle.getConfigForCoord(world, x, z);
            if (cr == null) continue;
            int y = world.getHighestBlockYAt(x, z); // Get highest block
            if ((y <= 0) || (y >= maxy)) continue;
            // See if block is air or still water
            Set<BlockCoord> poolblocks = null;
            int blkid = world.getBlockTypeIdAt(x,  y,  z);
            if (blkid == 0) {
                // If can fill any time, or is raining, and odds are hit
                if ((cr.chanceToFillPool > 0) && ((!cr.checkFillDuringRain) || world.hasStorm()) && (cr.chanceToFillPool < rnd.nextInt(100))) {
                    poolblocks = checkForPool(cr, x, y, z, blkid);
                    if (poolblocks != null) {   // Found pool
                        for (BlockCoord bc : poolblocks) {
                            Block blk = world.getBlockAt(bc.x, bc.y, bc.z);
                            if (blk != null) {
                                blk.setType(Material.STATIONARY_WATER);
                            }
                        }
                        puddle.log.info("Fill " + poolblocks.size() + " at " + world.getName() + "," + x + "," + y + "," + z);
                    }
                }
            }
            else if (blkid == Material.STATIONARY_WATER.getId()) { // If water
                // If can empty any time, or is not raining, and odds are hit
                if ((cr.chanceToEmptyPool > 0) && ((!cr.checkEmptyDuringNoRain) || (!world.hasStorm())) && (cr.chanceToEmptyPool < rnd.nextInt(100))) {
                    poolblocks = checkForPool(cr, x, y, z, blkid);
                    if (poolblocks != null) {   // Found pool
                        for (BlockCoord bc : poolblocks) {
                            Block blk = world.getBlockAt(bc.x, bc.y, bc.z);
                            if (blk != null) {
                                blk.setType(Material.AIR);
                            }
                        }
                        puddle.log.info("Empty " + poolblocks.size() + " at " + world.getName() + "," + x + "," + y + "," + z);
                    }
                }
            }
        }
    }
    private static final int xoff[] = { -1, 1, 0, 0 };
    private static final int zoff[] = { 0, 0, -1, 1 };
    private Set<BlockCoord> checkForPool(ConfigRec cr, int x, int y, int z, int blkid) {
        LinkedList<BlockCoord> blks_to_proc = new LinkedList<BlockCoord>();
        Set<BlockCoord> blocks_checked = new HashSet<BlockCoord>();
        // Push current block on to check list
        BlockCoord bc = new BlockCoord(x, y, z);
        blks_to_proc.add(bc);
        while (blks_to_proc.isEmpty() == false) {
            bc = blks_to_proc.pollFirst(); // Pop first off list
            // Check under it - is valid material?
            int bid = world.getBlockTypeIdAt(bc.x, bc.y - 1, bc.z);
            if (cr.borderBlocks.get(bid) == false) {    // If not valid, we're done
                return null;
            }
            blocks_checked.add(bc); // Bllock is checked - on to neighbors
            if (blocks_checked.size() > cr.maxPoolSize) {   // Too many?
                return null;
            }
            // Check 4 compass points
            for (int i = 0; i < xoff.length; i++) {
                bid = world.getBlockTypeIdAt(bc.x + xoff[i], bc.y, bc.z + zoff[i]);
                if (cr.borderBlocks.get(bid) == false) {    // If not valid, see if another block to expand
                    if (bid == blkid) {
                        BlockCoord bc2 = new BlockCoord(bc.x + xoff[i], bc.y, bc.z + zoff[i]);
                        if (blocks_checked.contains(bc2) == false) { // new block?
                            blks_to_proc.push(bc2);
                        }
                    }
                    else {  // Else, bad block - quit
                        return null;
                    }
                }
            }
        }
        return blocks_checked;
    }
}
