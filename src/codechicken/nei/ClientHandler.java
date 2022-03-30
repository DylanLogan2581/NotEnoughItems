package codechicken.nei;

import codechicken.core.ClientUtils;
import codechicken.core.GuiModListScroll;
import codechicken.lib.packet.PacketCustom;
import codechicken.nei.api.API;
import codechicken.nei.api.ItemInfo;
import cpw.mods.fml.client.CustomModLoadingErrorDisplayException;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ClientHandler
{
    private static ClientHandler instance;

    private World lastworld;
    private GuiScreen lastGui;

    public static void preInit() {
        ItemInfo.preInit();
    }

    public static void load() {
        instance = new ClientHandler();

        GuiModListScroll.register("NotEnoughItems");
        PacketCustom.assignHandler(NEICPH.channel, new NEICPH());
        FMLCommonHandler.instance().bus().register(instance);
        MinecraftForge.EVENT_BUS.register(instance);
	API.registerHighlightHandler(new DefaultHighlightHandler(), ItemInfo.Layout.HEADER);
        WorldOverlayRenderer.load();
    }

    @SubscribeEvent
    public void tickEvent(TickEvent.ClientTickEvent event) {
        if(event.phase == Phase.END)
            return;

        Minecraft mc = Minecraft.getMinecraft();
        if(mc.theWorld != null) {
            loadWorld(mc.theWorld, false);

            if (!NEIClientConfig.isEnabled())
                return;

            KeyManager.tickKeyStates();

            NEIController.updateUnlimitedItems(mc.thePlayer.inventory);
            if (mc.currentScreen == null)
                NEIController.processCreativeCycling(mc.thePlayer.inventory);
        }

        GuiScreen gui = mc.currentScreen;
        if (gui != lastGui) {
            if (gui instanceof GuiMainMenu)
                lastworld = null;
            else if (gui instanceof GuiSelectWorld)
                NEIClientConfig.reloadSaves();
        }
        lastGui = gui;
    }

    @SubscribeEvent
    public void tickEvent(TickEvent.RenderTickEvent event) {
    }


    @SubscribeEvent
    public void renderLastEvent(RenderWorldLastEvent event) {
        if (NEIClientConfig.isEnabled())
            WorldOverlayRenderer.render(event.partialTicks);
    }


    public void loadWorld(World world, boolean fromServer) {
        if (world != lastworld) {
            WorldOverlayRenderer.reset();

            if (!fromServer) {
                NEIClientConfig.setHasSMPCounterPart(false);
                NEIClientConfig.setInternalEnabled(false);

                if (!Minecraft.getMinecraft().isSingleplayer())//wait for server to initiate in singleplayer
                    NEIClientConfig.loadWorld("remote/" + ClientUtils.getServerIP().replace(':', '~'));
            }

            lastworld = world;
        }
    }

    public static ClientHandler instance() {
        return instance;
    }

    public static RuntimeException throwCME(final String message) {
        final GuiScreen errorGui = new GuiErrorScreen(null, null)
        {
            @Override
            public void handleMouseInput() {}

            @Override
            public void handleKeyboardInput() {}

            @Override
            public void drawScreen(int par1, int par2, float par3) {
                drawDefaultBackground();
                String[] s_msg = message.split("\n");
                for (int i = 0; i < s_msg.length; ++i)
                    drawCenteredString(fontRendererObj, s_msg[i], width / 2, height / 3 + 12 * i, 0xFFFFFFFF);
            }
        };

        @SuppressWarnings("serial")
        CustomModLoadingErrorDisplayException e = new CustomModLoadingErrorDisplayException()
        {
            @Override
            public void initGui(GuiErrorScreen errorScreen, FontRenderer fontRenderer) {
                Minecraft.getMinecraft().displayGuiScreen(errorGui);
            }

            @Override
            public void drawScreen(GuiErrorScreen errorScreen, FontRenderer fontRenderer, int mouseRelX, int mouseRelY, float tickTime) {}
        };
        throw e;
    }
}
