package com.mikeprimm.bukkit.Puddles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Puddles extends JavaPlugin {
    public Logger log;

    public ConfigRec[] configByBiome;
    public int tick_period;
    public Map<String, WorldProcessingRec> worlds = new HashMap<String, WorldProcessingRec>();
    public World.Environment[] worldenv;
    public int chunksPerTick;
    public int blocksPerChunk;
    public int minTickInterval;

    public Puddles() {
    }
    
    /* On disable, stop doing our function */
    public void onDisable() {
        
    }

    public void onEnable() {
        log = this.getLogger();
        
        log.info("Puddles loaded");
        
        final FileConfiguration cfg = getConfig();
        cfg.options().copyDefaults(true);   /* Load defaults, if needed */
        this.saveConfig();  /* Save updates, if needed */

        // Load default biome settings
        ConfigurationSection cs = cfg.getConfigurationSection("biome.default");
        ConfigRec crdef = ConfigRec.globalDefaults;
        if (cs != null) {
            crdef = new ConfigRec(cs, null);
        }
        // Load biome specific settings
        Biome[] v = Biome.values();
        configByBiome = new ConfigRec[v.length];
        for (Biome b : v) {
            cs = cfg.getConfigurationSection("biome." + b.name());
            if (cs != null) {
                configByBiome[b.ordinal()] = new ConfigRec(cs, crdef);
            }
            else {
                configByBiome[b.ordinal()] = crdef;
            }
        }
        /* Get tick period */
        tick_period = cfg.getInt("general.tick-period", 1);
        if(tick_period < 1) tick_period = 1;
        /* Get processing rate defaults */
        chunksPerTick = cfg.getInt("general.chunks-per-tick", 1);
        if (chunksPerTick < 1) chunksPerTick = 1;
        blocksPerChunk = cfg.getInt("general.blocks-per-chunk", 1);
        if (blocksPerChunk < 1) blocksPerChunk = 1;
        minTickInterval = cfg.getInt("general.min-tick-interval", 1);
        if (minTickInterval < 1) minTickInterval = 1;
        /* Get world environment limits */
        List<String> env = cfg.getStringList("general.world-env");
        if (env == null) {
            worldenv = new World.Environment[] { World.Environment.NORMAL };
        }
        else {
            worldenv = new World.Environment[env.size()];
            for (int i = 0; i < env.size(); i++) {
                worldenv[i] = World.Environment.valueOf(env.get(i));
            }
        }
        /* Initialize loaded worlds */
        for (World w : this.getServer().getWorlds()) {
            if (isProcessedWorld(w)) {
                worlds.put(w.getName(), new WorldProcessingRec(Puddles.this, cfg.getConfigurationSection("worlds." + w.getName()), w));
            }
        }
        /* Add listener for world events */
        Listener pl = new Listener() {
            @EventHandler(priority=EventPriority.MONITOR)
            public void onWorldLoad(WorldLoadEvent evt) {
                World w = evt.getWorld();
                if (isProcessedWorld(w)) {
                    worlds.put(w.getName(), new WorldProcessingRec(Puddles.this, cfg.getConfigurationSection("worlds." + w.getName()), w));
                }
            }
            @EventHandler(priority=EventPriority.MONITOR)
            public void onWorldUnload(WorldUnloadEvent evt) {
                if (evt.isCancelled()) return;
                worlds.remove(evt.getWorld().getName());
            }
        };
        getServer().getPluginManager().registerEvents(pl, this);
        
        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, 
                new Runnable() {
                    public void run() {
                        processTick();
                    }
                },
                tick_period, tick_period);
    }
    
    private void processTick() {
        for (WorldProcessingRec rec : worlds.values()) {
            rec.processTick(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        return false;
    }
    
    private boolean isProcessedWorld(World w) {
        World.Environment we = w.getEnvironment();
        for (int i = 0; i < worldenv.length; i++) {
            if (we == worldenv[i]) {
                return true;
            }
        }
        return false;
    }
    
    public ConfigRec getConfigForCoord(World w, int x, int z) {
        Biome b = w.getBiome(x,  z);    // Get biome
        if (b == null) return null;
        return configByBiome[b.ordinal()];
    }
}
