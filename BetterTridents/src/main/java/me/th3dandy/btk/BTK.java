package me.th3dandy.btk;

import org.bukkit.plugin.java.JavaPlugin;

public class BTK extends JavaPlugin {

    @Override
    public void onEnable() {
        // Register the TridentListener
        getServer().getPluginManager().registerEvents(new TridentListener(), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}