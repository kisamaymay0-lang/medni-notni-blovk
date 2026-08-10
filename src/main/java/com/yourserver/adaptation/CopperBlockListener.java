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
import java.util.HashSet;
import java.util.Set;

public class CopperBlockListener implements Listener {

    private final JavaPlugin plugin;
    private final HashMap<String, ItemStack[]> blockInventories = new HashMap<>();
    private static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
    
    // Список слотов, которые будут намертво заблокированы черными панелями
    private final Set<Integer> blockedSlots = new HashSet<>();

    public CopperBlockListener(JavaPlugin plugin) {
        this.plugin = plugin;
        
        // Столбец 1
        blockedSlots.add(0); blockedSlots.add(9); blockedSlots.add(18);
        // Столбец 2 (только 1 и 3 ряд, средний слот 10 открыт для времени)
        blockedSlots.add(1); blockedSlots.add(19);
        // Столбец 3
        blockedSlots.add(2); blockedSlots.add(11); blockedSlots.add(20);
        // Столбец 8 (слоты индексов 7, 16, 25)
        blockedSlots.add(7); blockedSlots.add(16); blockedSlots.add(25);
        // Столбец 9 (слоты индексов 8, 17, 26)
        blockedSlots.add(8); blockedSlots.add(17); blockedSlots.add(26);
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
            // Создаем заглушку без имени
            ItemStack separator = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta sMeta = separator.getItemMeta();
            if (sMeta != null) { 
                sMeta.setDisplayName(" "); 
                separator.setItemMeta(sMeta); 
            }

            // Заполняем заблокированные слоты черным стеклом
            for (int slot : blockedSlots) {
                gui.setItem(slot, separator);
            }
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CopperHolder) {
            CopperHolder holder = (CopperHolder) event.getInventory().getHolder();
            int slot = event.getRawSlot();

            // Запрещаем брать предметы из заблокированных слотов
            if (blockedSlots.contains(slot)) {
                event.setCancelled(true);
                return;
            }

            // Сохраняем инвентарь блока
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

        // Расчет времени: 1 предмет в слоте 10 дает 0.1 сек (2 тика в Майнкрафте)
        int itemsInTimeSlot = 0;
        if (items[10] != null && items[10].getType() != Material.AIR) {
            itemsInTimeSlot = items[10].getAmount();
        }

        // 1 предмет = 2 тика (0.1с). Если пусто — базовая задержка 4 тика (0.2с)
        int delayTicks = (itemsInTimeSlot > 0) ? (itemsInTimeSlot * 2) : 4;
        delayTicks = Math.clamp(delayTicks, 2, 100); // Ограничение от 0.1 до 5 секунд

        final int finalDelay = delayTicks;
        new BukkitRunnable() {
            int step = 0; // 4 шага для столбцов 4, 5, 6, 7

            @Override
            public void run() {
                if (step >= 4) {
                    this.cancel();
                    return;
                }

                // Столбцы 4, 5, 6, 7 (индексы слотов в сетке)
                int highSlot = 3 + step;       // 1 ряд (Высокий)
                int midSlot = 12 + step;      // 2 ряд (Средний)
                int lowSlot = 21 + step;      // 3 ряд (Низкий)

                if (items[highSlot] != null && items[highSlot].getType() != Material.AIR) {
                    Sound sound = getInstrumentByMaterial(items[highSlot].getType());
                    block.getWorld().playSound(block.getLocation(), sound, 1.5f, 1.6f);
                } 
                else if (items[midSlot] != null && items[midSlot].getType() != Material.AIR) {
                    Sound sound = getInstrumentByMaterial(items[midSlot].getType());
                    block.getWorld().playSound(block.getLocation(), sound, 1.0f, 1.0f);
                } 
                else if (items[lowSlot] != null && items[lowSlot].getType() != Material.AIR) {
                    Sound sound = getInstrumentByMaterial(items[lowSlot].getType());
                    block.getWorld().playSound(block.getLocation(), sound, 0.7f, 0.5f);
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, finalDelay);
    }

    // Метод динамического определения звука по типу лежащего блока (Ванильная механика)
    private Sound getInstrumentByMaterial(Material material) {
        String name = material.name();
        
        if (name.contains("BONE_BLOCK")) return Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;
        if (name.contains("GOLD_BLOCK")) return Sound.BLOCK_NOTE_BLOCK_BELL;
        if (name.contains("CLAY")) return Sound.BLOCK_NOTE_BLOCK_FLUTE;
        if (name.contains("PACKED_ICE")) return Sound.BLOCK_NOTE_BLOCK_CHIME;
        if (name.contains("WOOL")) return Sound.BLOCK_NOTE_BLOCK_GUITAR;
        if (name.contains("IRON_BLOCK")) return Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;
        if (name.contains("SOUL_SAND")) return Sound.BLOCK_NOTE_BLOCK_COW_BELL;
        if (name.contains("PUMPKIN")) return Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;
        if (name.contains("EMERALD_BLOCK")) return Sound.BLOCK_NOTE_BLOCK_BIT;
        if (name.contains("HAY_BLOCK")) return Sound.BLOCK_NOTE_BLOCK_BANJO;
        if (name.contains("GLOWSTONE")) return Sound.BLOCK_NOTE_BLOCK_PLING;
        if (name.contains("WOOD") || name.contains("LOG") || name.contains("PLANKS")) return Sound.BLOCK_NOTE_BLOCK_BASS;
        if (name.contains("STONE") || name.contains("COBBLESTONE") || name.contains("OBSIDIAN") || name.contains("ORE")) return Sound.BLOCK_NOTE_BLOCK_BASEDRUM;
        if (name.contains("SAND") || name.contains("GRAVEL")) return Sound.BLOCK_NOTE_BLOCK_SNARE;
        if (name.contains("GLASS")) return Sound.BLOCK_NOTE_BLOCK_HAT;

        // По умолчанию играется классическая гарфа
        return Sound.BLOCK_NOTE_BLOCK_HARP;
    }

    private static class CopperHolder implements org.bukkit.inventory.InventoryHolder {
        private final Block block;
        public CopperHolder(Block block) { this.block = block; }
        public Block getBlock() { return block; }
        @Override public Inventory getInventory() { return null; }
    }
}
