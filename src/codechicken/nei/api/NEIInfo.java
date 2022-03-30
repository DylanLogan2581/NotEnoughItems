package codechicken.nei.api;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.config.OptionCycled;
import net.minecraft.world.World;

import java.util.LinkedList;

public class NEIInfo
{
    public static final LinkedList<INEIModeHandler> modeHandlers = new LinkedList<INEIModeHandler>();

    public static void load(World world) {
    }
}
