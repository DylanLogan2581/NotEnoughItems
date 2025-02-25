package codechicken.nei;

import codechicken.core.*;
import codechicken.lib.config.ConfigFile;
import codechicken.lib.config.ConfigTag;
import codechicken.lib.config.ConfigTagParent;
import codechicken.nei.api.*;
import codechicken.nei.config.*;
import codechicken.nei.recipe.RecipeInfo;
import codechicken.obfuscator.ObfuscationRun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.SaveFormatComparator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

public class NEIClientConfig
{
    private static boolean configLoaded;
    private static boolean enabledOverride;

    public static Logger logger = LogManager.getLogger("NotEnoughItems");
    public static File configDir = new File(CommonUtils.getMinecraftDir(), "config/NEI/");
    public static ConfigSet global = new ConfigSet(
            new File("saves/NEI/client.dat"),
            new ConfigFile(new File(configDir, "client.cfg")));
    public static ConfigSet world;

    public static ItemStack creativeInv[];
    private static boolean statesSaved[] = new boolean[7];

    public static boolean hasSMPCounterpart;
    public static HashSet<String> permissableActions = new HashSet<String>();
    public static HashSet<String> disabledActions = new HashSet<String>();
    public static HashSet<String> enabledActions = new HashSet<String>();

    public static ItemStackSet bannedBlocks = new ItemStackSet();

    static {
        linkOptionList();
        setDefaults();
    }

    private static void setDefaults() {
        ConfigTagParent tag = global.config;

        tag.setNewLineMode(1);

        tag.getTag("inventory.hidden").getBooleanValue(false);

        tag.getTag("inventory.layoutstyle").getIntValue(0);

        ItemSorter.initConfig(tag);

        tag.getTag("inventory.itemIDs").getIntValue(1);
        API.addOption(new OptionCycled("inventory.itemIDs", 3, true));

        tag.getTag("inventory.searchmode").getIntValue(1);
        API.addOption(new OptionCycled("inventory.searchmode", 3, true));

        tag.getTag("command.item").setDefaultValue("/give {0} {1} {2} {3} {4}");
        API.addOption(new OptionTextField("command.item"));

        setDefaultKeyBindings();
    }

    private static void linkOptionList() {
        OptionList.setOptionList(new OptionList("nei.options")
        {
            @Override
            public ConfigSet globalConfigSet() {
                return global;
            }

	    @Override
            public ConfigSet worldConfigSet() {
                return world;
            }

            @Override
            public OptionList configBase() {
                return this;
            }

            @Override
            public GuiOptionList getGui(GuiScreen parent, OptionList list, boolean world) {
                return new GuiNEIOptionList(parent, list, world);
            }
        });
    }

    private static void setDefaultKeyBindings() {
        API.addKeyBind("gui.recipe", Keyboard.KEY_R);
        API.addKeyBind("gui.usage", Keyboard.KEY_U);
        API.addKeyBind("gui.back", Keyboard.KEY_BACK);
        API.addKeyBind("gui.prev", Keyboard.KEY_PRIOR);
        API.addKeyBind("gui.next", Keyboard.KEY_NEXT);
        API.addKeyBind("gui.hide", Keyboard.KEY_O);
        API.addKeyBind("gui.search", Keyboard.KEY_F);
        API.addKeyBind("world.chunkoverlay", Keyboard.KEY_F9);
        API.addKeyBind("world.moboverlay", Keyboard.KEY_F7);
    }

    public static OptionList getOptionList() {
        return OptionList.getOptionList("nei.options");
    }

    public static void loadWorld(String saveName) {
        setInternalEnabled(true);
        logger.debug("Loading "+(Minecraft.getMinecraft().isSingleplayer() ? "Local" : "Remote")+" World");
        bootNEI(ClientUtils.getWorld());

        File saveDir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/" + saveName);
        boolean newWorld = !saveDir.exists();
        if (newWorld)
            saveDir.mkdirs();

        world = new ConfigSet(new File(saveDir, "NEI.dat"), new ConfigFile(new File(saveDir, "NEI.cfg")));
        onWorldLoad(newWorld);
    }

    private static void onWorldLoad(boolean newWorld) {
        world.config.setComment("World based configuration of NEI.\nMost of these options can be changed ingame.\nDeleting any element will restore it to it's default value");

        setWorldDefaults();
        creativeInv = new ItemStack[54];
        LayoutManager.searchField.setText(getSearchExpression());
        LayoutManager.quantity.setText(Integer.toString(getItemQuantity()));
        SubsetWidget.loadHidden();

        NEIInfo.load(ClientUtils.getWorld());
    }

    private static void setWorldDefaults() {
        NBTTagCompound nbt = world.nbt;
        if (!nbt.hasKey("search")) nbt.setString("search", "");
        if (!nbt.hasKey("quantity")) nbt.setInteger("quantity", 0);
        if (!nbt.hasKey("validateenchantments")) nbt.setBoolean("validateenchantments", false);

        world.saveNBT();
    }

    public static int getKeyBinding(String string) {
        return getSetting("keys." + string).getIntValue();
    }

    public static void setDefaultKeyBinding(String string, int key) {
        getSetting("keys." + string).getIntValue(key);
    }

    public static void bootNEI(World world) {
        if (configLoaded)
            return;

        loadStates();
        //ItemVisibilityHash.loadStates();
        //vishash = new ItemVisibilityHash();
        ItemInfo.load(world);
        GuiInfo.load();
        RecipeInfo.load();
        LayoutManager.load();
        NEIController.load();

        configLoaded = true;

        ClassDiscoverer classDiscoverer = new ClassDiscoverer(new IStringMatcher()
        {
            public boolean matches(String test) {
                return test.startsWith("NEI") && test.endsWith("Config.class");
            }
        }, IConfigureNEI.class);

        classDiscoverer.findClasses();

        for (Class<?> clazz : classDiscoverer.classes) {
            try {
                IConfigureNEI config = (IConfigureNEI) clazz.newInstance();
                config.loadConfig();
                NEIModContainer.plugins.add(config);
                logger.debug("Loaded " + clazz.getName());
            } catch (Exception e) {
                logger.error("Failed to Load " + clazz.getName(), e);
            }
        }

        ItemSorter.loadConfig();
    }

    public static void loadStates() {
        for (int state = 0; state < 7; state++)
            statesSaved[state] = !global.nbt.getCompoundTag("save" + state).hasNoTags();
    }

    public static boolean isWorldSpecific(String setting) {
        if(world == null) return false;
        ConfigTag tag = world.config.getTag(setting, false);
        return tag != null && tag.value != null;
    }

    public static boolean isStateSaved(int i) {
        return statesSaved[i];
    }

    public static ConfigTag getSetting(String s) {
        return isWorldSpecific(s) ? world.config.getTag(s) : global.config.getTag(s);
    }

    public static boolean getBooleanSetting(String s) {
        return getSetting(s).getBooleanValue();
    }

    public static boolean isHidden() {
        return !enabledOverride || getBooleanSetting("inventory.hidden");
    }

    public static boolean isEnabled() {
        return enabledOverride;
    }

    public static void setEnabled(boolean flag) {
        getSetting("inventory.widgetsenabled").setBooleanValue(flag);
    }

    public static int getItemQuantity() {
        return world.nbt.getInteger("quantity");
    }

    public static int getLayoutStyle() {
        return getIntSetting("inventory.layoutstyle");
    }

    public static String getStringSetting(String s) {
        return getSetting(s).getValue();
    }

    public static boolean showIDs() {
        int i = getIntSetting("inventory.itemIDs");
        return i == 2 || (i == 1 && isEnabled() && !isHidden());
    }

    public static void toggleBooleanSetting(String setting) {
        ConfigTag tag = getSetting(setting);
        tag.setBooleanValue(!tag.getBooleanValue());
    }

    public static void cycleSetting(String setting, int max) {
        ConfigTag tag = getSetting(setting);
        tag.setIntValue((tag.getIntValue() + 1) % max);
    }

    public static int getIntSetting(String setting) {
        return getSetting(setting).getIntValue();
    }

    public static void setIntSetting(String setting, int val) {
        getSetting(setting).setIntValue(val);
    }

    public static String getSearchExpression() {
        return world.nbt.getString("search");
    }

    public static void setSearchExpression(String expression) {
        world.nbt.setString("search", expression);
        world.saveNBT();
    }

    public static boolean invCreativeMode() {
        return enabledActions.contains("creative+") && canPerformAction("creative+");
    }

    public static boolean areDamageVariantsShown() {
        return hasSMPCounterPart() || getSetting("command.item").getValue().contains("{3}");
    }

    public static void clearState(int state) {
        statesSaved[state] = false;
        global.nbt.setTag("save" + state, new NBTTagCompound());
        global.saveNBT();
    }

    public static void loadState(int state) {
        if (!statesSaved[state])
            return;

        NBTTagCompound statesave = global.nbt.getCompoundTag("save" + state);
        GuiContainer currentContainer = NEIClientUtils.getGuiContainer();
        LinkedList<TaggedInventoryArea> saveAreas = new LinkedList<TaggedInventoryArea>();
        saveAreas.add(new TaggedInventoryArea(NEIClientUtils.mc().thePlayer.inventory));

        for (INEIGuiHandler handler : GuiInfo.guiHandlers) {
            List<TaggedInventoryArea> areaList = handler.getInventoryAreas(currentContainer);
            if (areaList != null)
                saveAreas.addAll(areaList);
        }

        for (TaggedInventoryArea area : saveAreas) {
            if (!statesave.hasKey(area.tagName))
                continue;

            for (int slot : area.slots) {
                NEIClientUtils.setSlotContents(slot, null, area.isContainer());
            }

            NBTTagList areaTag = statesave.getTagList(area.tagName, 10);
            for (int i = 0; i < areaTag.tagCount(); i++) {
                NBTTagCompound stacksave = areaTag.getCompoundTagAt(i);
                int slot = stacksave.getByte("Slot") & 0xFF;
                if (!area.slots.contains(slot))
                    continue;

                NEIClientUtils.setSlotContents(slot, ItemStack.loadItemStackFromNBT(stacksave), area.isContainer());
            }
        }
    }

    public static void saveState(int state) {
        NBTTagCompound statesave = global.nbt.getCompoundTag("save" + state);
        GuiContainer currentContainer = NEIClientUtils.getGuiContainer();
        LinkedList<TaggedInventoryArea> saveAreas = new LinkedList<TaggedInventoryArea>();
        saveAreas.add(new TaggedInventoryArea(NEIClientUtils.mc().thePlayer.inventory));

        for (INEIGuiHandler handler : GuiInfo.guiHandlers) {
            List<TaggedInventoryArea> areaList = handler.getInventoryAreas(currentContainer);
            if (areaList != null)
                saveAreas.addAll(areaList);
        }

        for (TaggedInventoryArea area : saveAreas) {
            NBTTagList areaTag = new NBTTagList();

            for (int i : area.slots) {
                ItemStack stack = area.getStackInSlot(i);
                if (stack == null)
                    continue;
                NBTTagCompound stacksave = new NBTTagCompound();
                stacksave.setByte("Slot", (byte) i);
                stack.writeToNBT(stacksave);
                areaTag.appendTag(stacksave);
            }
            statesave.setTag(area.tagName, areaTag);
        }

        global.nbt.setTag("save" + state, statesave);
        global.saveNBT();
        statesSaved[state] = true;
    }

    public static boolean hasSMPCounterPart() {
        return hasSMPCounterpart;
    }

    public static void setHasSMPCounterPart(boolean flag) {
        hasSMPCounterpart = flag;
        permissableActions.clear();
        bannedBlocks.clear();
        disabledActions.clear();
        enabledActions.clear();
    }

    public static boolean canCheatItem(ItemStack stack) {
        return canPerformAction("item") && !bannedBlocks.contains(stack);
    }

    public static boolean canPerformAction(String name) {
        if (!isEnabled())
            return false;

        String base = NEIActions.base(name);
        if (hasSMPCounterpart)
            return permissableActions.contains(base);

        if (NEIActions.smpRequired(name))
            return false;

        String cmd = getStringSetting("command." + base);
        if (cmd == null || !cmd.startsWith("/"))
            return false;

        return true;
    }

    public static String[] getStringArrSetting(String s) {
        return getStringSetting(s).replace(" ", "").split(",");
    }

    public static void setInternalEnabled(boolean b) {
        enabledOverride = b;
    }

    public static void reloadSaves() {
        File saveDir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/local");
        if (!saveDir.exists())
            return;

        List<SaveFormatComparator> saves;
        try {
            saves = Minecraft.getMinecraft().getSaveLoader().getSaveList();
        } catch (Exception e) {
            logger.error("Error loading saves", e);
            return;
        }
        HashSet<String> saveFileNames = new HashSet<String>();
        for (SaveFormatComparator save : saves)
            saveFileNames.add(save.getFileName());

        for (File file : saveDir.listFiles())
            if (file.isDirectory() && !saveFileNames.contains(file.getName()))
                ObfuscationRun.deleteDir(file, true);
    }
}
