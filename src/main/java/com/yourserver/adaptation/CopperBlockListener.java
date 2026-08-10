package com.yourserver.adaptation;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
    private final HashMap<String, ItemStack[]> blockInventories = new HashMap<>();
    private static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

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
            // Изначально меню полностью пустое и чистое для игрока!
            // Ставим только центральный вертикальный разделитель (4-й столбец, слоты 4, 13, 22)
            ItemStack separator = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta sMeta = separator.getItemMeta();
            if (sMeta != null) { 
                sMeta.setDisplayName("§7Разделитель: Лево-Время | Право-Ноты"); 
                separator.setItemMeta(sMeta); 
            }
            gui.setItem(4, separator);
            gui.setItem(13, separator);
            gui.setItem(22, separator);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CopperHolder) {
            CopperHolder holder = (CopperHolder) event.getInventory().getHolder();
            int slot = event.getRawSlot();

            // Запрещаем игроку забирать или ломать центральную линию-разделитель
            if (slot == 4 || slot == 13 || slot == 22) {
                event.setCancelled(true);
                return;
            }

            // Мгновенно сохраняем любые предметы, которые игрок перетащил, положил или убрал
            Bukkit.getScheduler().runTask(plugin, () -> {
                blockInventories.put(getBlockKey(holder.getBlock()), event.getInventory().getContents());
            });
        }
    }

    @EventHandler
    public void onBlockRedstone(BlockRedstoneEvent event) {
        Block wireBlock = event.getBlock();
        
        for (BlockFace face : FACES) {
            Block targetBlock = wireBlock.getRelative(face);
            
            if (targetBlock.getType() == Material.WAXED_CHISELED_COPPER) {
                if (event.getNewCurrent() > 0 && event.getOldCurrent() == 0) {
                    triggerCopperBlock(targetBlock);
                }
            }
        }
    }

    private void triggerCopperBlock(Block block) {
        long currentTime = System.currentTimeMillis();
        long lastUsed = 0;

        if (block.hasMetadata("last_copper_trigger")) {
            lastUsed = block.getMetadata("last_copper_trigger").get(0).asLong();
        }

        if (currentTime - lastUsed < 400) {
            return;
        }

        block.setMetadata("last_copper_trigger", new FixedMetadataValue(plugin, currentTime));

        String key = getBlockKey(block);
        if (!blockInventories.containsKey(key)) return;

        ItemStack[] items = blockInventories.get(key);

        // 1. Считаем задержку по предметам в ЛЕВОЙ части (слот 0..3, 9..12, 18..21)
        int leftItemsCount = 0;
        int[] leftSlots = {0,1,2,3, 9,10,11,12, 18,19,20,21};
        for (int slot : leftSlots) {
            if (items[slot] != null && items[slot].getType() != Material.AIR) {
                leftItemsCount += items[slot].getAmount();
            }
        }
        
        // Каждые 2 предмета слева увеличивают паузу на 1 тик (0.05 сек). Базово — 4 тика.
        int delayTicks = 4 + (leftItemsCount / 2);
        delayTicks = Math.clamp(delayTicks, 2, 40);

        // 2. Воспроизведение нот в ПРАВОЙ части (4 столбца читаются слева направо)
        // Столбец 1: слоты 5, 14, 23 | Столбец 2: слоты 6, 15, 24
        // Столбец 3: слоты 7, 16, 25 | Столбец 4: слоты 8, 17, 26
        final int finalDelay = delayTicks;
        new BukkitRunnable() {
            int step = 0; // Шаг от 0 до 3 (наши 4 столбца)

            @Override
            public void run() {
                if (step >= 4) {
                    this.cancel();
                    return;
                }

                // Высчитываем точные слоты для текущего шага-столбца в правой части
                int highSlot = 5 + step;
                int midSlot = 14 + step;
                int lowSlot = 23 + step;

                // Проверяем 1 ряд (Высокий звук) — если игрок положил туда вещь
                if (items[highSlot] != null && items[highSlot].getType() != Material.AIR) {
                    block.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1.5f, 1.6f);
                } 
                // Проверяем 2 ряд (Средний звук)
                else if (items[midSlot] != null && items[midSlot].getType() != Material.AIR) {
                    block.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f, 1.0f);
                } 
                // Проверяем 3 ряд (Низкий звук)
                else if (items[lowSlot] != null && items[lowSlot].getType() != Material.AIR) {
                    block.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.7f, 0.5f);
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, finalDelay);
    }

    private static class CopperHolder implements org.bukkit.inventory.InventoryHolder {
        private final Block block;
        public CopperHolder(Block block) { this.block = block; }
        public Block getBlock() { return block; }
        @Override public Inventory getInventory() { return null; }
    }
}
