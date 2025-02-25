package codechicken.nei;

import codechicken.lib.inventory.InventoryRange;
import codechicken.lib.inventory.InventoryUtils;
import codechicken.lib.util.LangProxy;
import codechicken.nei.api.GuiInfo;
import codechicken.nei.api.IInfiniteItemHandler;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.ItemInfo;
import com.google.common.collect.Iterables;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.*;

import static codechicken.nei.NEIClientConfig.*;

public class NEIClientUtils extends NEIServerUtils
{
    public static LangProxy lang = new LangProxy("nei");

    public static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    public static String translate(String key, Object... params) {
        return lang.format(key, params);
    }

    public static void printChatMessage(IChatComponent msg) {
        if (mc().ingameGUI != null)
            mc().ingameGUI.getChatGUI().printChatMessage(msg);
    }

    public static void deleteHeldItem() {
        deleteSlotStack(-999);
    }

    public static void dropHeldItem() {
        mc().playerController.windowClick(((GuiContainer) mc().currentScreen).inventorySlots.windowId, -999, 0, 0, mc().thePlayer);
    }

    public static void deleteSlotStack(int slotNumber) {
        setSlotContents(slotNumber, null, true);
    }

    public static void decreaseSlotStack(int slotNumber) {
        ItemStack stack = slotNumber == -999 ? getHeldItem() : mc().thePlayer.openContainer.getSlot(slotNumber).getStack();
        if (stack == null)
            return;

        if (stack.stackSize == 1)
            deleteSlotStack(slotNumber);
        else {
            stack = stack.copy();
            stack.stackSize--;
            setSlotContents(slotNumber, stack, true);
        }
    }

    public static void deleteEverything() {
        NEICPH.sendDeleteAllItems();
    }

    public static void deleteItemsOfType(ItemStack type) {
        Container c = getGuiContainer().inventorySlots;
        for (int i = 0; i < c.inventorySlots.size(); i++) {
            Slot slot = c.getSlot(i);
            if (slot == null)
                continue;

            ItemStack stack = slot.getStack();
            if (stack != null && stack.getItem() == type.getItem() && stack.getItemDamage() == type.getItemDamage()) {
                setSlotContents(i, null, true);
                slot.putStack(null);
            }
        }
    }

    public static ItemStack getHeldItem() {
        return mc().thePlayer.inventory.getItemStack();
    }

    public static void setSlotContents(int slot, ItemStack item, boolean containerInv) {
        NEICPH.sendSetSlot(slot, item, containerInv);

        if (slot == -999)
            mc().thePlayer.inventory.setItemStack(item);
    }

    /**
     * @param mode      -1 = normal cheats, 0 = no infinites, 1 = replenish stack
     */
    public static void cheatItem(ItemStack stack, int button, int mode) {
        if (!canCheatItem(stack))
            return;

        if (mode == -1 && button == 0 && shiftKey() && NEIClientConfig.hasSMPCounterPart()) {
            for (IInfiniteItemHandler handler : ItemInfo.infiniteHandlers) {
                if (!handler.canHandleItem(stack))
                    continue;

                ItemStack inf = handler.getInfiniteItem(stack);
                if (inf != null) {
                    giveStack(inf, inf.stackSize, true);
                    return;
                }
            }
            cheatItem(stack, button, 0);
        } else if (button == 1) {
            giveStack(stack, 1);
        } else {
            if (mode == 1 && stack.stackSize < stack.getMaxStackSize()) {
                giveStack(stack, stack.getMaxStackSize() - stack.stackSize);
            } else {
                int amount = getItemQuantity();
                if (amount == 0)
                    amount = stack.getMaxStackSize();
                giveStack(stack, amount);
            }
        }
    }

    public static void giveStack(ItemStack itemstack) {
        giveStack(itemstack, itemstack.stackSize);
    }

    public static void giveStack(ItemStack itemstack, int i) {
        giveStack(itemstack, i, false);
    }

    public static void giveStack(ItemStack base, int i, boolean infinite) {
        ItemStack stack = copyStack(base, i);
        if (hasSMPCounterPart()) {
            ItemStack typestack = copyStack(stack, 1);
            if (!infinite && !canItemFitInInventory(mc().thePlayer, stack) && (mc().currentScreen instanceof GuiContainer)) {
                GuiContainer gui = getGuiContainer();
                List<Iterable<Integer>> handlerSlots = new LinkedList<Iterable<Integer>>();
                for(INEIGuiHandler handler : GuiInfo.guiHandlers)
                    handlerSlots.add(handler.getItemSpawnSlots(gui, typestack));

                int increment = typestack.getMaxStackSize();
                int given = 0;
                for(int slotNo : Iterables.concat(handlerSlots)) {
                    Slot slot = gui.inventorySlots.getSlot(slotNo);
                    if(!slot.isItemValid(typestack) || !InventoryUtils.canStack(slot.getStack(), typestack))
                        continue;

                    int qty = Math.min(stack.stackSize - given, increment);
                    int current = slot.getHasStack() ? slot.getStack().stackSize : 0;
                    qty = Math.min(qty, slot.getSlotStackLimit() - current);

                    ItemStack newStack = copyStack(typestack, qty + current);
                    slot.putStack(newStack);
                    setSlotContents(slotNo, newStack, true);
                    given += qty;
                    if(given >= stack.stackSize)
                        break;
                }
                if(given > 0)
                    NEICPH.sendGiveItem(copyStack(typestack, given), false, false);
            } else
                NEICPH.sendGiveItem(stack, infinite, true);
        } else {
            for (int given = 0; given < stack.stackSize; ) {
                int qty = Math.min(stack.stackSize - given, stack.getMaxStackSize());
                sendCommand(getStringSetting("command.item"),
                        mc().thePlayer.getCommandSenderName(),
                        Item.getIdFromItem(stack.getItem()),
                        qty,
                        stack.getItemDamage(),
                        stack.hasTagCompound() ? stack.getTagCompound().toString() : "",
                        Item.itemRegistry.getNameForObject(stack.getItem()));
                given += qty;
            }
        }
    }

    public static boolean canItemFitInInventory(EntityPlayer player, ItemStack itemstack) {
        return InventoryUtils.getInsertibleQuantity(new InventoryRange(player.inventory, 0, 36), itemstack) > 0;
    }

    public static boolean shiftKey() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    public static boolean controlKey() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    public static void sendCommand(String command, Object... args) {
        if (command.length() == 0)
            return;

        NumberFormat numberformat = NumberFormat.getIntegerInstance();
        numberformat.setGroupingUsed(false);
        MessageFormat messageformat = new MessageFormat(command);
        for (int i = 0; i < args.length; i++)
            if (args[i] instanceof Integer || args[i] instanceof Long)
                messageformat.setFormatByArgumentIndex(i, numberformat);

        mc().thePlayer.sendChatMessage(messageformat.format(args));
    }

    public static ArrayList<int[]> concatIntegersToRanges(List<Integer> damages) {
        ArrayList<int[]> ranges = new ArrayList<int[]>();
        if (damages.size() == 0) return ranges;

        Collections.sort(damages);
        int start = -1;
        int next = 0;
        for (Integer i : damages) {
            if (start == -1) {
                start = next = i;
                continue;
            }
            if (next + 1 != i) {
                ranges.add(new int[]{start, next});
                start = next = i;
                continue;
            }
            next = i;
        }
        ranges.add(new int[]{start, next});
        return ranges;
    }

    public static ArrayList<int[]> addIntegersToRanges(List<int[]> ranges, List<Integer> damages) {
        for (int[] range : ranges)
            for (int integer = range[0]; integer <= range[1]; integer++)
                damages.add(integer);

        return concatIntegersToRanges(damages);
    }

    public static boolean safeKeyDown(int keyCode) {
        try {
            return Keyboard.isKeyDown(keyCode);
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    public static void setItemQuantity(int i) {
        world.nbt.setInteger("quantity", i);
        world.saveNBT();
        LayoutManager.quantity.setText(Integer.toString(i));
    }

    public static GuiContainer getGuiContainer() {
        if (mc().currentScreen instanceof GuiContainer)
            return (GuiContainer) mc().currentScreen;

        return null;
    }

    public static boolean altKey() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }

    public static void playClickSound() {
        mc().getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }
}
