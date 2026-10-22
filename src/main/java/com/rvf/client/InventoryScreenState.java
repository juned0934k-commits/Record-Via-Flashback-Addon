package com.rvf.client;

import com.rvf.mixin.MixinAbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of whatever container screen (player inventory, chest, furnace, modded...) the recording player has open.
 * <p>
 * Slot geometry is recorded together with the items, so the replay can draw any container without knowing its type.
 * Mouse position is stored relative to the container panel's top-left corner (in GUI-scaled pixels), which keeps the
 * cursor aligned with the slots no matter what window size / GUI scale the replay is viewed at.
 * <p>
 * A state with {@code open == false} means "no container screen is open".
 */
public record InventoryScreenState(boolean open, @Nullable ResourceLocation menuType, Component title,
                                   int imageWidth, int imageHeight,
                                   List<SlotEntry> slots, ItemStack carried,
                                   float mouseX, float mouseY) {

    public static final int MAX_SLOTS = 256;

    public record SlotEntry(int x, int y, ItemStack item) {}

    public static final InventoryScreenState CLOSED = new InventoryScreenState(false, null, Component.empty(),
        0, 0, List.of(), ItemStack.EMPTY, 0, 0);

    /** Captures the current state on the client thread. All item stacks are copied. */
    public static InventoryScreenState capture(Minecraft minecraft, LocalPlayer player) {
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)) {
            return CLOSED;
        }

        AbstractContainerMenu menu = screen.getMenu();

        ResourceLocation menuTypeKey = null;
        try {
            MenuType<?> type = menu.getType(); // throws for the player's own InventoryMenu
            menuTypeKey = BuiltInRegistries.MENU.getKey(type);
        } catch (Exception ignored) {
        }

        // The container panel's real position and size (NOT screen.getRectangle(), which is the whole screen)
        var acc = (MixinAbstractContainerScreenAccessor) screen;
        int panelLeft = acc.rvf$getLeftPos();
        int panelTop = acc.rvf$getTopPos();
        int panelW = acc.rvf$getImageWidth();
        int panelH = acc.rvf$getImageHeight();

        List<SlotEntry> slots = new ArrayList<>(Math.min(menu.slots.size(), MAX_SLOTS));
        for (Slot slot : menu.slots) {
            if (slots.size() >= MAX_SLOTS) break;
            slots.add(new SlotEntry(slot.x, slot.y, slot.getItem().copy()));
        }

        // Mouse position in GUI-scaled coordinates (same formula vanilla uses for getScaledXPos/getScaledYPos)
        var window = minecraft.getWindow();
        double mouseX = minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / Math.max(1, window.getScreenWidth());
        double mouseY = minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / Math.max(1, window.getScreenHeight());

        return new InventoryScreenState(true, menuTypeKey, screen.getTitle(),
            panelW, panelH, slots, menu.getCarried().copy(),
            (float) (mouseX - panelLeft), (float) (mouseY - panelTop));
    }

    /** True if nothing visible changed. Used so we only write a new action when something actually moved/changed. */
    public boolean sameAs(@Nullable InventoryScreenState other) {
        if (other == null || this.open != other.open) return false;
        if (!this.open) return true;
        if (this.mouseX != other.mouseX || this.mouseY != other.mouseY) return false;
        if (this.imageWidth != other.imageWidth || this.imageHeight != other.imageHeight) return false;
        if (this.slots.size() != other.slots.size()) return false;
        if (!ItemStack.matches(this.carried, other.carried)) return false;
        for (int i = 0; i < this.slots.size(); i++) {
            SlotEntry a = this.slots.get(i);
            SlotEntry b = other.slots.get(i);
            if (a.x != b.x || a.y != b.y || !ItemStack.matches(a.item, b.item)) return false;
        }
        return true;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(this.open);
        if (!this.open) return;

        buf.writeBoolean(this.menuType != null);
        if (this.menuType != null) buf.writeResourceLocation(this.menuType);
        ComponentSerialization.STREAM_CODEC.encode(buf, this.title);
        buf.writeVarInt(this.imageWidth);
        buf.writeVarInt(this.imageHeight);

        buf.writeVarInt(this.slots.size());
        for (SlotEntry entry : this.slots) {
            buf.writeShort(entry.x);
            buf.writeShort(entry.y);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.item);
        }
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, this.carried);
        buf.writeFloat(this.mouseX);
        buf.writeFloat(this.mouseY);
    }

    public static InventoryScreenState decode(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return CLOSED;

        ResourceLocation menuType = buf.readBoolean() ? buf.readResourceLocation() : null;
        Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
        int imageWidth = buf.readVarInt();
        int imageHeight = buf.readVarInt();

        int count = Math.min(buf.readVarInt(), 4096);
        List<SlotEntry> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int x = buf.readShort();
            int y = buf.readShort();
            slots.add(new SlotEntry(x, y, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));
        }
        ItemStack carried = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        float mouseX = buf.readFloat();
        float mouseY = buf.readFloat();
        return new InventoryScreenState(true, menuType, title, imageWidth, imageHeight, slots, carried, mouseX, mouseY);
    }
}
