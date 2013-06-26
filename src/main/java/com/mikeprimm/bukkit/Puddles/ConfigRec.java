package com.mikeprimm.bukkit.Puddles;

import java.util.BitSet;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

public class ConfigRec {
    public int maxPoolSize;
    public BitSet borderBlocks;
    public boolean checkFillDuringRain;
    public boolean checkEmptyDuringNoRain;
    public int chanceToFillPool;
    public int chanceToEmptyPool;
    public static final ConfigRec globalDefaults = new ConfigRec();
    
    private ConfigRec() {
        maxPoolSize = 8;
        borderBlocks = new BitSet();
        borderBlocks.set(Material.DIRT.getId());
        borderBlocks.set(Material.GRASS.getId());
        borderBlocks.set(Material.SAND.getId());
        checkFillDuringRain = true;
        checkEmptyDuringNoRain = true;
        chanceToFillPool = 20;
        chanceToEmptyPool = 0;
    }
    
    public ConfigRec(ConfigurationSection cs, ConfigRec def) {
        if (def == null) def = globalDefaults;
        maxPoolSize = cs.getInt("max-pool-size", def.maxPoolSize);
        borderBlocks = new BitSet();
        List<Integer> lst = cs.getIntegerList("border-blocks");
        if (lst != null) {
            for (Integer blk : lst) {
                borderBlocks.set(blk.intValue());
            }
        }
        else {
            borderBlocks.or(def.borderBlocks);
        }
        checkFillDuringRain = cs.getBoolean("check-for-fill-during-rain", def.checkFillDuringRain);
        checkEmptyDuringNoRain = cs.getBoolean("check-for-empty-during-norain", def.checkEmptyDuringNoRain);
        chanceToFillPool = cs.getInt("chance-to-fill-pool", def.chanceToFillPool);
        chanceToEmptyPool = cs.getInt("chance-to-empty-pool", def.chanceToEmptyPool);
    }
}
