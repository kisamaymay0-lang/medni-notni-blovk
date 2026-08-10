package com.yourserver.adaptation;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;

public class CopperBlockListener implements Listener {

    private final JavaPlugin plugin;
    // Временное хранилище содержимого инвентаря для каждого блока по его уникальной локации
    private final HashMap<String, ItemStack[]> blockInventories = new HashMap<>();

    public CopperBlockListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private String getBlockKey(Block block) {
        return block.getWorld().getName() + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            
            if (block.getType() == Material.WAXED_CHISELED_COPPER) { 
                event.setCancelled(true);
                openCopperMenu(event.getPlayer(), block);
            }
        }
    }

    private void openCopperMenu(Player player, Block block) {
        Inventory gui = Bukkit.createInventory(new CopperHolder(block), 27, "§6Редактор Медного Блока");
        String key = getBlockKey(block);

        if (blockInventories.containsKey(key)) {
            gui.setContents(blockInventories.get(key));
        } else {
            // Инициализация пустого меню с кнопками-подсказами (белое стекло — пустые слоты)
            ItemStack emptySlot = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
            ItemMeta meta = emptySlot.getItemMeta();
            if (meta != null) { 
                meta.setDisplayName("§7Пустой слот ноты/времени"); 
                emptySlot.setItemMeta(meta); 
            }

            int[] noteSlots = {4, 5, 6, 7, 13, 14, 15, 16, 22, 23, 24, 25};
            for (int slot : noteSlots) {
                gui.setItem(slot, emptySlot);
            }

            // Отделяем крайний правый ряд черным стеклом, чтобы визуально отделить правое поле
            ItemStack separator = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta sMeta = separator.getItemMeta();
            if (sMeta != null) { 
                sMeta.setDisplayName(" "); 
                separator.setItemMeta(sMeta); 
            }
            gui.setItem(8, separator);
            gui.setItem(17, separator);
            gui.setItem(26, separator);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CopperHolder) {
            CopperHolder holder = (CopperHolder) event.getInventory().getHolder();
            int slot = event.getRawSlot();

            // Запрещаем кликать по черным стеклам разделителя
            if (slot == 8 || slot == 17 || slot == 26) {
                event.setCancelled(true);
                return;
            }

            // Автоматически сохраняем содержимое инвентаря блока сразу после клика игрока
            Bukkit.getScheduler().runTask(plugin, () -> {
                blockInventories.put(getBlockKey(holder.getBlock()), event.getInventory().getContents());
            });
        }
    }

    @EventHandler
    public void onBlockRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();

        if (block.getType() == Material.WAXED_CHISELED_COPPER) {
            if (event.getNewCurrent() > 0 && event.getOldCurrent() == 0) {
                
                long currentTime = System.currentTimeMillis();
                long lastUsed = 0;

                if (block.hasMetadata("last_copper_trigger")) {
                    lastUsed = block.getMetadata("last_copper_trigger").get(0).asLong();
                }

                // Кулдаун 0.4 секунды (400 мс)
                if (currentTime - lastUsed < 400) {
                    event.setNewCurrent(0); 
                    return;
                }

                block.setMetadata("last_copper_trigger", new FixedMetadataValue(plugin, currentTime));

                String key = getBlockKey(block);
                if (!blockInventories.containsKey(key)) return;

                ItemStack[] items = blockInventories.get(key);

                // Высчитываем задержку по количеству предметов в левой части
                int delayTicks = 10; 
                int leftItemsCount = 0;
                int[] leftSlots = {0,1,2,3,9,10,11,12,18,19,20,21};
                for (int slot : leftSlots) {
                    if (items[slot] != null && items[slot].getType() != Material.AIR) {
                        leftItemsCount += items[slot].getAmount();
                    }
                }
                if (leftItemsCount > 0) {
                    delayTicks = Math.min(leftItemsCount * 2, 40); 
                }

                // Запуск проигрывания 4-х столбцов СЛЕВА НАПРАВО
                final int finalDelay = delayTicks;
                new BukkitRunnable() {
                    int step = 0; 

                    @Override
                    public void run() {
                        if (step >= 4) {
                            this.cancel();
                            return;
                        }

                        int highSlot = 4 + step;
                        int midSlot = 13 + step;
                        int lowSlot = 22 + step;

                        // Если в слоте лежит ЛЮБОЙ предмет, кроме воздуха и белого стекла — играем ноту
                        if (isNoteItem(items[highSlot])) {
                            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1.5f, 1.6f);
                        }
                        else if (isNoteItem(items[midSlot])) {
                            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f, 1.0f);
                        }
                        else if (isNoteItem(items[lowSlot])) {
                            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.7f, 0.5f);
                        }

                        step++;
                    }
                }.runTaskTimer(plugin, 0L, finalDelay);
            }
        }
    }

    private boolean isNoteItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getType() != Material.WHITE_STAINED_GLASS_PANE;
    }

    private static class CopperHolder implements org.bukkit.inventory.InventoryHolder {
        private final Block block;
        public CopperHolder(Block block) { this.block = block; }
        public Block getBlock() { return block; }
        @Override public Inventory getInventory() { return null; }
    }
}
