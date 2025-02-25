package codechicken.nei;

import codechicken.core.CommonUtils;
import codechicken.core.IGuiPacketSender;
import codechicken.core.ServerUtils;
import codechicken.lib.inventory.SlotDummy;
import codechicken.lib.packet.PacketCustom;
import codechicken.lib.packet.PacketCustom.IServerPacketHandler;
import codechicken.lib.vec.BlockCoord;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.INetHandlerPlayServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.world.World;

import java.util.LinkedList;
import java.util.Set;

public class NEISPH implements IServerPacketHandler
{
    @Override
    public void handlePacket(PacketCustom packet, EntityPlayerMP sender, INetHandlerPlayServer netHandler) {
        if (!NEIServerConfig.authenticatePacket(sender, packet))
            return;

        switch (packet.getType()) {
            case 1:
                handleGiveItem(sender, packet);
                break;
            case 4:
                NEIServerUtils.deleteAllItems(sender);
                break;
            case 5:
                setInventorySlot(sender, packet);
                break;
            case 6:
                break;
            case 7:
                break;
            case 8:
                break;
            case 9:
                break;
            case 10:
                sendLoginState(sender);
                break;
            case 11:
                sender.sendContainerAndContentsToPlayer(sender.openContainer, sender.openContainer.getInventory());
                break;
            case 12:
                handlePropertyChange(sender, packet);
                break;
            case 13:
                break;
            case 14:
                NEIServerUtils.cycleCreativeInv(sender, packet.readInt());
                break;
            case 15:
                handleMobSpawnerID(sender.worldObj, packet.readCoord(), packet.readString());
                break;
            case 21:
                break;
            case 22:
                break;
            case 23:
                processCreativeInv(sender, packet.readBoolean());
                break;
            case 24:
                break;
            case 25:
                handleDummySlotSet(sender, packet);
                break;
        }
    }

    private void handleDummySlotSet(EntityPlayerMP sender, PacketCustom packet) {
        int slotNumber = packet.readShort();
        ItemStack stack = packet.readItemStack(true);

        Slot slot = sender.openContainer.getSlot(slotNumber);
        if (slot instanceof SlotDummy)
            slot.putStack(stack);
    }

    private void handleMobSpawnerID(World world, BlockCoord coord, String mobtype) {
        TileEntity tile = world.getTileEntity(coord.x, coord.y, coord.z);
        if (tile instanceof TileEntityMobSpawner) {
            ((TileEntityMobSpawner) tile).func_145881_a().setEntityName(mobtype);
            tile.markDirty();
            world.markBlockForUpdate(coord.x, coord.y, coord.z);
        }
    }

    private void handlePropertyChange(EntityPlayerMP sender, PacketCustom packet) {
        String name = packet.readString();
        if (NEIServerConfig.canPlayerPerformAction(sender.getCommandSenderName(), name))
            NEIServerConfig.disableAction(sender.dimension, name, packet.readBoolean());
    }

    public static void processCreativeInv(EntityPlayerMP sender, boolean open) {
        if (open) {
            ServerUtils.openSMPContainer(sender, new ContainerCreativeInv(sender, new ExtendedCreativeInv(NEIServerConfig.forPlayer(sender.getCommandSenderName()), Side.SERVER)), new IGuiPacketSender()
            {
                @Override
                public void sendPacket(EntityPlayerMP player, int windowId) {
                    PacketCustom packet = new PacketCustom(channel, 23);
                    packet.writeBoolean(true);
                    packet.writeByte(windowId);
                    packet.sendToPlayer(player);
                }
            });
        } else {
            sender.closeContainer();
            PacketCustom packet = new PacketCustom(channel, 23);
            packet.writeBoolean(false);
            packet.sendToPlayer(sender);
        }
    }

    private void handleGiveItem(EntityPlayerMP player, PacketCustom packet) {
        NEIServerUtils.givePlayerItem(player, packet.readItemStack(true), packet.readBoolean(), packet.readBoolean());
    }

    private void setInventorySlot(EntityPlayerMP player, PacketCustom packet) {
        boolean container = packet.readBoolean();
        int slot = packet.readShort();
        ItemStack item = packet.readItemStack();

        ItemStack old = NEIServerUtils.getSlotContents(player, slot, container);
        boolean deleting = item == null || old != null && NEIServerUtils.areStacksSameType(item, old) && item.stackSize < old.stackSize;
        if (NEIServerConfig.canPlayerPerformAction(player.getCommandSenderName(), deleting ? "delete" : "item"))
            NEIServerUtils.setSlotContents(player, slot, item, container);
    }

    public static void sendActionDisabled(int dim, String name, boolean disable) {
        new PacketCustom(channel, 11)
                .writeString(name)
                .writeBoolean(disable)
                .sendToDimension(dim);
    }

    public static void sendActionEnabled(EntityPlayerMP player, String name, boolean enable) {
        new PacketCustom(channel, 12)
                .writeString(name)
                .writeBoolean(enable)
                .sendToPlayer(player);
    }

    private void sendLoginState(EntityPlayerMP player) {
        LinkedList<String> actions = new LinkedList<String>();
        LinkedList<String> disabled = new LinkedList<String>();
        LinkedList<String> enabled = new LinkedList<String>();
        LinkedList<ItemStack> bannedItems = new LinkedList<ItemStack>();
        PlayerSave playerSave = NEIServerConfig.forPlayer(player.getCommandSenderName());

        for (String name : NEIActions.nameActionMap.keySet()) {
            if (NEIServerConfig.canPlayerPerformAction(player.getCommandSenderName(), name))
                actions.add(name);
            if (NEIServerConfig.isActionDisabled(player.dimension, name))
                disabled.add(name);
            if (playerSave.isActionEnabled(name))
                enabled.add(name);
        }
        for (ItemStackMap.Entry<Set<String>> entry : NEIServerConfig.bannedItems.entries())
            if (!NEIServerConfig.isPlayerInList(player.getCommandSenderName(), entry.value, true))
                bannedItems.add(entry.key);

        PacketCustom packet = new PacketCustom(channel, 10);

        packet.writeByte(actions.size());
        for (String s : actions)
            packet.writeString(s);

        packet.writeByte(disabled.size());
        for (String s : disabled)
            packet.writeString(s);

        packet.writeByte(enabled.size());
        for (String s : enabled)
            packet.writeString(s);

        packet.writeInt(bannedItems.size());
        for (ItemStack stack : bannedItems)
            packet.writeItemStack(stack);

        packet.sendToPlayer(player);
    }

    public static void sendHasServerSideTo(EntityPlayerMP player) {
        NEIServerConfig.logger.debug("Sending serverside check to: " + player.getCommandSenderName());
        PacketCustom packet = new PacketCustom(channel, 1);
        packet.writeByte(NEIActions.protocol);
        packet.writeString(CommonUtils.getWorldName(player.worldObj));

        packet.sendToPlayer(player);
    }

    public static final String channel = "NEI";
}
