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
    private final Set<Integer> blockedSlots = new HashSet<>();

    public CopperBlockListener(JavaPlugin plugin) {
        this.plugin = plugin;
        
        // Столбец 1
        blockedSlots.add(0); blockedSlots.add(9); blockedSlots.add(18);
        // Столбец 2 (слот 10 открыт для времени)
        blockedSlots.add(1); blockedSlots.add(19);
        // Столбец 3
        blockedSlots.add(2); blockedSlots.add(11); blockedSlots.add(20);
        // Столбец 4
        blockedSlots.add(3); blockedSlots.add(12); blockedSlots.add(21);
        // Столбец 9 (крайний правый)
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
        // Установлено точное название меню без лишних слов
        Inventory gui = Bukkit.createInventory(new CopperHolder(block), 27, "§6Медный нотный блок");
        String key = getBlockKey(block);

        if (blockInventories.containsKey(key)) {
            gui.setContents(blockInventories.get(key));
        } else {
            ItemStack separator = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta sMeta = separator.getItemMeta();
            if (sMeta != null) { 
                sMeta.setDisplayName(" "); 
                separator.setItemMeta(sMeta); 
            }

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

            if (blockedSlots.contains(slot)) {
                event.setCancelled(true);
                return;
            }

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
        if (block.hasMetadata("copper_playing")) {
            return; 
        }

        long currentTime = System.currentTimeMillis();
        long lastUsed = 0;

        if (block.hasMetadata("last_copper_trigger")) {
            lastUsed = block.getMetadata("last_copper_trigger").get(0).asLong();
        }

        if (currentTime - lastUsed < 400) {
            return;
        }

        block.setMetadata("last_copper_trigger", new FixedMetadataValue(plugin, currentTime));
        block.setMetadata("copper_playing", new FixedMetadataValue(plugin, true));

        String key = getBlockKey(block);
        if (!blockInventories.containsKey(key)) {
            block.removeMetadata("copper_playing", plugin);
            return;
        }

        ItemStack[] items = blockInventories.get(key);

        // Исправлено чтение количества предметов в слоте времени (слот 10)
        int itemsInTimeSlot = 0;
        if (items[10] != null && items[10].getType() != Material.AIR) {
            itemsInTimeSlot = items[10].getAmount();
        }

        int delayTicks = (itemsInTimeSlot > 0) ? (itemsInTimeSlot * 2) : 4;
        delayTicks = Math.clamp(delayTicks, 2, 100);

        final int finalDelay = delayTicks;
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (step >= 4) {
                    block.removeMetadata("copper_playing", plugin);
                    this.cancel();
                    return;
                }

                int highSlot = 4 + step;       
                int midSlot = 13 + step;      
                int lowSlot = 22 + step;      

                // Воспроизведение с уменьшенным и сбалансированным разбросом питчей
                if (items[highSlot] != null && items[highSlot].getType() != Material.AIR) {
                    Sound sound = getInstrumentByMaterial(items[highSlot].getType());
                    block.getWorld().playSound(block.getLocation(), sound, 1.2f, 1.3f); // Верхний ряд: мягкий высокий (1.3)
                } 
                
                if (items[midSlot] != null && items[midSlot].getType() != Material.AIR) {
                    Sound sound = getInstrumentByMaterial(items[midSlot].getType());
                    block.getWorld().playSound(block.getLocation(), sound, 1.0f, 1.0f); // Средний ряд: эталонный (1.0)
                } 
                
                if (items[lowSlot] != null && items[lowSlot].getType() != Material.AIR) {
                    Sound sound = getInstrumentByMaterial(items[lowSlot].getType());
                    block.getWorld().playSound(block.getLocation(), sound, 0.8f, 0.6f); // Нижний ряд: приятный низкий (0.6)
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, finalDelay);
    }

    private Sound getInstrumentByMaterial(Material material) {
        String name = material.name();
        
        if (name.contains("BONE_BLOCK")) return Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;
        if (name.contains("GOLD_BLOCK")) return Sound.BLOCK_NOTE_BLOCK_BELL;
        if (name.contains("CLAY")) return Sound.BLOCK_NOTE_BLOCK_FLUTE;
        if (name.contains("PACKED_ICE")) return Sound.BLOCK_NOTE_BLOCK_CHIME;
        if (name.contains("WOOL") || name.contains("CARPET")) return Sound.BLOCK_NOTE_BLOCK_GUITAR;
        if (name.contains("IRON_BLOCK")) return Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;
        if (name.contains("SOUL_SAND")) return Sound.BLOCK_NOTE_BLOCK_COW_BELL;
        if (name.contains("PUMPKIN")) return Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;
        if (name.contains("EMERALD_BLOCK")) return Sound.BLOCK_NOTE_BLOCK_BIT;
        if (name.contains("HAY_BLOCK")) return Sound.BLOCK_NOTE_BLOCK_BANJO;
        if (name.contains("GLOWSTONE")) return Sound.BLOCK_NOTE_BLOCK_PLING;
        if (name.contains("AMETHYST")) return Sound.BLOCK_NOTE_BLOCK_CHIME;
        if (name.contains("COPPER")) return Sound.BLOCK_NOTE_BLOCK_BASS;
        if (name.contains("WOOD") || name.contains("LOG") || name.contains("PLANKS")) return Sound.BLOCK_NOTE_BLOCK_BASS;
        if (name.contains("STONE") || name.contains("COBBLESTONE") || name.contains("OBSIDIAN") || name.contains("ORE")) return Sound.BLOCK_NOTE_BLOCK_BASEDRUM;
        if (name.contains("SAND") || name.contains("GRAVEL")) return Sound.BLOCK_NOTE_BLOCK_SNARE;
        if (name.contains("GLASS")) return Sound.BLOCK_NOTE_BLOCK_HAT;

        return Sound.BLOCK_NOTE_BLOCK_HARP;
    }

    private static class CopperHolder implements org.bukkit.inventory.InventoryHolder {
        private final Block block;
        public CopperHolder(Block block) { this.block = block; }
        public Block getBlock() { return block; }
        @Override public Inventory getInventory() { return null; }
    }
}
