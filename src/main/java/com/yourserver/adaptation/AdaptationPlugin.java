package com.yourserver.adaptation;

import org.bukkit.plugin.java.JavaPlugin;

public final class AdaptationPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Регистрируем наш обработчик медных блоков и редстоуна
        getServer().getPluginManager().registerEvents(new CopperBlockListener(this), this);
        getLogger().info("Плагин адаптации под Медные Блоки успешно запущен!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Плагин адаптации выключен.");
    }
}
