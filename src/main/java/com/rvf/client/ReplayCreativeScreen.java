package com.rvf.client;

import com.rvf.RVF;
import com.rvf.mixin.MixinCreativeModeInventoryScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What the creative inventory screen looks like around its slots: the tabs, the background of the selected tab,
 * the search text and the scroll bar. The slots and items themselves come from the normal inventory recording.
 * Recorded in its own action, so recordings made without it keep working.
 */
public final class ReplayCreativeScreen {

    private ReplayCreativeScreen() {}

    private static final int MAX_SEARCH_LENGTH = 64;
    private static final int MAX_TABS = 32;

    public record Tab(int x, boolean top, int column, boolean selected, ItemStack icon) {}

    public record Snapshot(boolean open, Identifier background, Component title, boolean showTitle,
                           boolean searchVisible, String search, float scroll, boolean tabScrolls, boolean canScroll,
                           boolean inventoryTab, List<Tab> tabs) {

        public static final Snapshot EMPTY = new Snapshot(false, Identifier.withDefaultNamespace("textures/gui/container/creative_inventory/tab_items.png"),
            Component.empty(), false, false, "", 0f, false, false, false, List.of());

        /** Changes whenever anything visible changes. */
        public String key() {
            if (!this.open) {
                return "0";
            }
            StringBuilder key = new StringBuilder("1:");
            key.append(this.background).append(':').append(this.title.getString()).append(':')
                .append(this.showTitle).append(this.searchVisible).append(this.tabScrolls).append(this.canScroll).append(this.inventoryTab)
                .append(':').append(Float.floatToIntBits(this.scroll)).append(':').append(this.search).append(':');
            for (Tab tab : this.tabs) {
                key.append(tab.selected ? '#' : '-').append(tab.x);
            }
            return key.toString();
        }
    }

    // ---- recording ----

    private static boolean failed = false;

    public static Snapshot captureCurrent(Minecraft minecraft) {
        if (failed) {
            return Snapshot.EMPTY;
        }
        try {
            return capture(minecraft);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            RVF.LOGGER.error("Creative inventory recording is switched off because it failed", e);
            return Snapshot.EMPTY;
        }
    }

    private static Snapshot capture(Minecraft minecraft) {
        if (!(minecraft.gui.screen() instanceof CreativeModeInventoryScreen screen)) {
            return Snapshot.EMPTY;
        }

        MixinCreativeModeInventoryScreenAccessor accessor = (MixinCreativeModeInventoryScreenAccessor) screen;
        CreativeModeTab selected = MixinCreativeModeInventoryScreenAccessor.rvf$getSelectedTab();
        if (selected == null) {
            return Snapshot.EMPTY;
        }

        List<Tab> tabs = new ArrayList<>();
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tabs.size() >= MAX_TABS) break;
            tabs.add(new Tab(accessor.rvf$getTabX(tab), tab.row() == CreativeModeTab.Row.TOP, tab.column(), tab == selected, tab.getIconItem().copy()));
        }

        EditBox box = accessor.rvf$getSearchBox();
        boolean searchVisible = box != null && box.visible;
        String search = "";
        if (searchVisible) {
            search = box.getValue();
            if (search.length() > MAX_SEARCH_LENGTH) {
                search = search.substring(0, MAX_SEARCH_LENGTH);
            }
        }

        return new Snapshot(true, selected.getBackgroundTexture(), selected.getDisplayName(), selected.showTitle(),
            searchVisible, search, accessor.rvf$getScrollOffs(), selected.canScroll(), accessor.rvf$canScroll(),
            selected.getType() == CreativeModeTab.Type.INVENTORY, List.copyOf(tabs));
    }

    public static void encode(RegistryFriendlyByteBuf buf, Snapshot snapshot) {
        buf.writeBoolean(snapshot.open());
        if (!snapshot.open()) {
            return;
        }
        buf.writeIdentifier(snapshot.background());
        ComponentSerialization.STREAM_CODEC.encode(buf, snapshot.title());
        buf.writeBoolean(snapshot.showTitle());
        buf.writeBoolean(snapshot.searchVisible());
        buf.writeUtf(snapshot.search());
        buf.writeFloat(snapshot.scroll());
        buf.writeBoolean(snapshot.tabScrolls());
        buf.writeBoolean(snapshot.canScroll());
        buf.writeBoolean(snapshot.inventoryTab());
        buf.writeVarInt(snapshot.tabs().size());
        for (Tab tab : snapshot.tabs()) {
            buf.writeShort(tab.x());
            buf.writeBoolean(tab.top());
            buf.writeVarInt(tab.column());
            buf.writeBoolean(tab.selected());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, tab.icon());
        }
    }

    public static Snapshot decode(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return Snapshot.EMPTY;
        }
        Identifier background = buf.readIdentifier();
        Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
        boolean showTitle = buf.readBoolean();
        boolean searchVisible = buf.readBoolean();
        String search = buf.readUtf();
        float scroll = buf.readFloat();
        boolean tabScrolls = buf.readBoolean();
        boolean canScroll = buf.readBoolean();
        boolean inventoryTab = buf.readBoolean();
        int count = Math.min(buf.readVarInt(), MAX_TABS);
        List<Tab> tabs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int x = buf.readShort();
            boolean top = buf.readBoolean();
            int column = buf.readVarInt();
            boolean selected = buf.readBoolean();
            ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            tabs.add(new Tab(x, top, column, selected, icon));
        }
        return new Snapshot(true, background, title, showTitle, searchVisible, search, scroll, tabScrolls, canScroll, inventoryTab, tabs);
    }

    // ---- replay ----

    private static int entityId = -1;
    private static Snapshot snapshot = Snapshot.EMPTY;

    public static void set(int id, Snapshot newSnapshot) {
        entityId = id;
        snapshot = newSnapshot;
    }

    public static void clear() {
        entityId = -1;
        snapshot = Snapshot.EMPTY;
    }

    /** The recorded creative screen if the given entity is the recorded player, otherwise EMPTY. */
    public static Snapshot getFor(int id) {
        return id == entityId ? snapshot : Snapshot.EMPTY;
    }

    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    private static final int LABEL_COLOR = 0xFF404040;

    private static Identifier tabSprite(boolean top, boolean selected, int column) {
        int number = Math.max(0, Math.min(column, 6)) + 1;
        return Identifier.withDefaultNamespace("container/creative_inventory/tab_" + (top ? "top" : "bottom")
            + "_" + (selected ? "selected" : "unselected") + "_" + number);
    }

    private static void drawTab(GuiGraphicsExtractor g, int left, int top, int imageHeight, Tab tab) {
        int x = left + tab.x();
        int y = top + (tab.top() ? -28 : imageHeight - 4);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, tabSprite(tab.top(), tab.selected(), tab.column()), x, y, 26, 32);
        g.item(tab.icon(), x + 5, y + (tab.top() ? 9 : 7));
    }

    /**
     * Draws everything of the creative screen except the slots: tabs, the background of the selected tab,
     * the scroll bar, the tab title, the search text and (on the survival inventory tab) the player model.
     */
    public static void renderBackground(GuiGraphicsExtractor g, Minecraft minecraft, int left, int top, int imageWidth, int imageHeight,
                                        Snapshot s, float mouseX, float mouseY) {
        // Unselected tabs first, so the panel covers their inner edge; the selected tab goes on top of it
        for (Tab tab : s.tabs()) {
            if (!tab.selected()) {
                drawTab(g, left, top, imageHeight, tab);
            }
        }

        g.blit(RenderPipelines.GUI_TEXTURED, s.background(), left, top, 0f, 0f, imageWidth, imageHeight, 256, 256);

        for (Tab tab : s.tabs()) {
            if (tab.selected()) {
                drawTab(g, left, top, imageHeight, tab);
            }
        }

        if (s.tabScrolls()) {
            int y = top + 18 + (int) (95 * s.scroll());
            g.blitSprite(RenderPipelines.GUI_TEXTURED, s.canScroll() ? SCROLLER : SCROLLER_DISABLED, left + 175, y, 12, 15);
        }

        if (s.showTitle()) {
            g.text(minecraft.font, s.title(), left + 8, top + 6, LABEL_COLOR, false);
        }
        if (s.searchVisible()) {
            g.text(minecraft.font, minecraft.font.plainSubstrByWidth(s.search(), 78), left + 82, top + 6, 0xFFFFFFFF, false);
        }

        if (s.inventoryTab() && minecraft.getCameraEntity() instanceof LivingEntity living) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, left + 73, top + 6, left + 105, top + 49,
                20, 0.0625F, mouseX, mouseY, living);
        }
    }

}
