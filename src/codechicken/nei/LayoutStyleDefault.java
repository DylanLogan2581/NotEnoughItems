package codechicken.nei;

import codechicken.nei.api.LayoutStyle;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;

import static codechicken.lib.gui.GuiDraw.getStringWidth;
import static codechicken.nei.LayoutManager.*;
import static codechicken.nei.NEIClientConfig.*;

public abstract class LayoutStyleDefault extends LayoutStyle
{
    @Override
    public void layout(GuiContainer gui, VisiblityData visiblity) {
        int windowWidth = gui.width;
        int windowHeight = gui.height;
        int containerWidth = gui.xSize;
        int containerLeft = gui.guiLeft;

        reset();

        prev.y = 2;
        prev.h = 16;
        prev.w = containerLeft / 3;
        prev.x = (containerWidth + windowWidth) / 2 + 2;
        next.x = windowWidth - prev.w - 2;

        next.y = prev.y;
        next.w = prev.w;
        next.h = prev.h;
        pageLabel.x = containerLeft * 3 / 2 + containerWidth + 1;
        pageLabel.y = prev.y + 5;
        pageLabel.text = "(" + itemPanel.getPage() + "/" + itemPanel.getNumPages() + ")";

        itemPanel.y = prev.h + prev.y;
        itemPanel.x = (containerWidth + windowWidth) / 2 + 3;
        itemPanel.w = windowWidth - 3 - itemPanel.x;
        itemPanel.h = windowHeight - 15 - itemPanel.y;
        if (!canPerformAction("item"))
            itemPanel.h += 15;
        itemPanel.resize();

        more.w = more.h = less.w = less.h = 16;
        less.x = prev.x;
        more.x = windowWidth - less.w - 2;
        more.y = less.y = windowHeight - more.h - 2;

        quantity.x = less.x + less.w + 2;
        quantity.y = less.y;
        quantity.w = more.x - quantity.x - 2;
        quantity.h = less.h;

        options.x = isEnabled() ? 0 : 6;
        options.y = isEnabled() ? windowHeight - 22 : windowHeight - 28;
        options.w = 80;
        options.h = 22;

	searchField.y = windowHeight - searchField.h - 2;

        dropDown.h = 20;
        dropDown.w = prev.x - dropDown.x - 3;
        searchField.h = 20;
        searchField.w = 150;
        searchField.x = (windowWidth - searchField.w) / 2;

        if (!visiblity.showItemSection) {
            //TODO dropDown.setDropDown(0);
            searchField.setFocus(false);
        }

        int maxWidth = 0;
        for (int i = 0; i < 7; i++) {
            deleteButtons[i].w = 16;
            deleteButtons[i].h = 16;

            NBTTagCompound statelist = global.nbt.getCompoundTag("statename");
            global.nbt.setTag("statename", statelist);
            String name = statelist.getString("" + i);
            if (statelist.getTag("" + i) == null) {
                name = "" + (i + 1);
                statelist.setString("" + i, name);
            }
            stateButtons[i].label = name;
            stateButtons[i].saved = isStateSaved(i);

            int width = getStringWidth(stateButtons[i].getRenderLabel()) + 26;
            if (width + 22 > containerLeft)
                width = containerLeft - 22;

            if (width > maxWidth)
                maxWidth = width;
        }

        for (int i = 0; i < 7; i++) {
            stateButtons[i].x = 0;
            stateButtons[i].y = 58 + i * 22;
            stateButtons[i].h = 20;

            stateButtons[i].x = 0;
            stateButtons[i].w = maxWidth;
            deleteButtons[i].x = stateButtons[i].w + 3;
            deleteButtons[i].y = stateButtons[i].y + 2;
        }
    }

    public abstract void layoutButton(Button button);
}
