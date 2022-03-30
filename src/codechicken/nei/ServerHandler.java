package codechicken.nei;

import codechicken.core.CommonUtils;
import codechicken.lib.packet.PacketCustom;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import java.util.List;

public class ServerHandler
{
    private static ServerHandler instance;

    public static void load() {
        instance = new ServerHandler();

        PacketCustom.assignHandler(NEICPH.channel, new NEISPH());
        FMLCommonHandler.instance().bus().register(instance);
        MinecraftForge.EVENT_BUS.register(instance);

        NEIActions.init();
    }

    @SubscribeEvent
    public void tickEvent(TickEvent.WorldTickEvent event) {
    }

    @SubscribeEvent
    public void loadEvent(WorldEvent.Load event) {
        if(!event.world.isRemote)
            NEIServerConfig.load(event.world);
    }

    @SubscribeEvent
    public void tickEvent(TickEvent.PlayerTickEvent event) {
        if (event.phase == Phase.START && event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            PlayerSave save = NEIServerConfig.forPlayer(player.getCommandSenderName());
            if (save == null)
                return;
            save.updateOpChange(player);
            save.save();
        }
    }

    @SubscribeEvent
    public void loginEvent(PlayerLoggedInEvent event) {
        NEIServerConfig.loadPlayer(event.player);
        NEISPH.sendHasServerSideTo((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void logoutEvent(PlayerLoggedOutEvent event) {
        NEIServerConfig.unloadPlayer(event.player);
    }

    @SubscribeEvent
    public void dimChangeEvent(PlayerChangedDimensionEvent event) {
        NEISPH.sendHasServerSideTo((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void loginEvent(PlayerRespawnEvent event) {
        NEISPH.sendHasServerSideTo((EntityPlayerMP) event.player);
    }
}
