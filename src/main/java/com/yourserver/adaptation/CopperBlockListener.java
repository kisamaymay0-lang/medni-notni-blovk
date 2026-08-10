package com.yourserver.adaptation;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.metadata.FixedMetadataValue;

public class CopperBlockListener implements Listener {

    private final JavaPlugin plugin;

    public CopperBlockListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // 1. Открытие GUI по клику ПКМ на Медный блок (взяли за основу Вощёный резной медный блок)
    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            
            // Проверяем, что это медный блок (можно заменить на любой другой тип меди)
            if (block.getType() == Material.WAXED_CHISELED_COPPER) { 
                event.setCancelled(true);
                openCopperMenu(event.getPlayer());
            }
        }
    }

    // Кастомное меню без ресурспака на кнопках-панелях
    private void openCopperMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, "§6Настройка медного блока");

        for (int i = 0; i < 4; i++) {
            ItemStack noteItem = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
            ItemMeta meta = noteItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§aНота #" + (i + 1));
                noteItem.setItemMeta(meta);
            }
            gui.setItem(i, noteItem);
        }

        ItemStack delayItem = new ItemStack(Material.CLOCK);
        ItemMeta delayMeta = delayItem.getItemMeta();
        if (delayMeta != null) {
            delayMeta.setDisplayName("§eЗадержка между нотами: 1 сек");
            delayItem.setItemMeta(delayMeta);
        }
        gui.setItem(8, delayItem);

        player.openInventory(gui);
    }

    // 2. Обработка редстоуна и кулдауна 0.4 секунды (400 мс)
    @EventHandler
    public void onBlockRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();

        if (block.getType() == Material.WAXED_CHISELED_COPPER) {
            if (event.getNewPower() > 0 && event.getOldPower() == 0) {
                
                long currentTime = System.currentTimeMillis();
                long lastUsed = 0;

                // Защита от спама и лаг-машин через метаданные блока
                if (block.hasMetadata("last_copper_trigger")) {
                    lastUsed = block.getMetadata("last_copper_trigger").get(0).asLong();
                }

                if (currentTime - lastUsed < 400) {
                    event.setNewPower(0); // Отменяем сигнал, если прошло меньше 0.4 сек
                    return;
                }

                // Записываем новое время срабатывания
                block.setMetadata("last_copper_trigger", new FixedMetadataValue(plugin, currentTime));

                // Воспроизведение 4 нот по очереди раз в секунду (20 тиков)
                new BukkitRunnable() {
                    int step = 0;
                    @Override
                    public void run() {
                        if (step >= 4) {
                            this.cancel();
                            return;
                        }
                        // Массив питчей (тональностей) для нот
                        float[] pitches = {0.6f, 0.8f, 1.0f, 1.4f}; 
                        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f, pitches[step]);
                        step++;
                    }
                }.runTaskTimer(plugin, 0L, 20L);
            }
        }
    }
}
