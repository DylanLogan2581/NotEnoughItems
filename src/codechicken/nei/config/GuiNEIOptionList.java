package codechicken.nei.config;

import codechicken.core.gui.GuiCCButton;
import codechicken.nei.NEIClientConfig;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNoCallback;

import java.awt.*;
import java.net.URI;

public class GuiNEIOptionList extends GuiOptionList implements GuiYesNoCallback
{
    public GuiNEIOptionList(GuiScreen parent, OptionList optionList, boolean world) {
        super(parent, optionList, world);
    }

    @Override
    public void resize() {
        super.resize();
    }

    @Override
    public void addWidgets() {
        super.addWidgets();
    }

    @Override
    public void actionPerformed(String ident, Object... params) {
            super.actionPerformed(ident, params);
    }

    @Override
    public void confirmClicked(boolean yes, int id) {
        mc.displayGuiScreen(this);
    }
}
