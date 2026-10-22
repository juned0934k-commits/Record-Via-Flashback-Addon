package com.rvf.client;

import com.rvf.AddonConfig;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side, replay-only overlay that draws the recorded container screen and a "ghost" mouse cursor.
 * Nothing is drawn unless we are in a replay, the camera is the recorded player, and the recording says
 * a container screen was open at this point in time.
 */
public final class ReplayInventoryOverlay {

    private ReplayInventoryOverlay() {}

    private static final long TICK_NANOS = 50_000_000L;

    private static final ResourceLocation INVENTORY_TEX = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final ResourceLocation GENERIC_54_TEX = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation CURSOR_TEX = ResourceLocation.fromNamespaceAndPath("rvf", "textures/gui/replay_cursor.png");
    private static final int LABEL_COLOR = 0xFF404040;
    // Outlines vanilla shows in empty armor / offhand slots (indexes 5-8 and 45 of the player inventory menu)
    private static final ResourceLocation[] EMPTY_ARMOR_ICONS = {
        ResourceLocation.withDefaultNamespace("container/slot/helmet"),
        ResourceLocation.withDefaultNamespace("container/slot/chestplate"),
        ResourceLocation.withDefaultNamespace("container/slot/leggings"),
        ResourceLocation.withDefaultNamespace("container/slot/boots")
    };
    private static final ResourceLocation EMPTY_SHIELD_ICON = ResourceLocation.withDefaultNamespace("container/slot/shield");

    private static int entityId = -1;
    private static InventoryScreenState state = InventoryScreenState.CLOSED;

    // Cursor interpolation (recorded at tick rate, drawn at frame rate)
    private static float prevMouseX, prevMouseY, currMouseX, currMouseY;
    private static long lastUpdateNanos;
    private static boolean hadOpenState = false;

    public static void set(int newEntityId, InventoryScreenState newState) {
        boolean wasOpen = state.open();
        entityId = newEntityId;
        state = newState;

        if (newState.open()) {
            if (wasOpen) {
                // keep whatever is currently being displayed as the start of the next lerp
                prevMouseX = currMouseX;
                prevMouseY = currMouseY;
            } else {
                // freshly opened (or we just seeked into it): don't sweep the cursor in from the old position
                prevMouseX = newState.mouseX();
                prevMouseY = newState.mouseY();
            }
            currMouseX = newState.mouseX();
            currMouseY = newState.mouseY();
            lastUpdateNanos = System.nanoTime();
        }
    }

    public static void clear() {
        entityId = -1;
        state = InventoryScreenState.CLOSED;
    }

    private static boolean shouldRender(Minecraft minecraft) {
        if (!AddonConfig.get().showInventory || !Flashback.isInReplay() || !state.open()) return false;
        Entity camera = minecraft.getCameraEntity();
        return camera != null && camera.getId() == entityId;
    }

    public static void render(GuiGraphics g) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!shouldRender(minecraft)) return;

        InventoryScreenState s = state;
        int guiW = minecraft.getWindow().getGuiScaledWidth();
        int guiH = minecraft.getWindow().getGuiScaledHeight();

        // Same placement vanilla uses for container screens (shifted sideways while the recipe book is open)
        ReplayInventoryExtras.Snapshot extras = ReplayInventoryExtras.getFor(entityId);
        ReplayCreativeScreen.Snapshot creative = ReplayCreativeScreen.getFor(entityId);
        boolean isCreative = creative.open();
        int left = ReplayInventoryExtras.panelLeft(guiW, s.imageWidth(), extras);
        int top = (guiH - s.imageHeight()) / 2;

        // Cursor position, interpolated between the last two recorded samples
        float t = Math.min(1.0f, (System.nanoTime() - lastUpdateNanos) / (float) TICK_NANOS);
        float mx = left + prevMouseX + (currMouseX - prevMouseX) * t;
        float my = top + prevMouseY + (currMouseY - prevMouseY) * t;

        // Darkened game view behind the container, like vanilla screens
        g.fillGradient(0, 0, guiW, guiH, -1072689136, -804253680);

        // Background: real vanilla texture when we know the container, generic panel otherwise
        // The creative inventory draws its own tabs, background and scroll bar
        boolean textured = isCreative || drawVanillaBackground(g, s, left, top);
        if (isCreative) {
            ReplayCreativeScreen.renderBackground(g, minecraft, left, top, s.imageWidth(), s.imageHeight(), creative, mx, my);
        } else if (!textured) {
            drawPanel(g, left, top, s.imageWidth(), s.imageHeight());
        }

        boolean playerInv = !isCreative && isPlayerInventory(s);
        if (!isCreative) {
            g.drawString(minecraft.font, s.title(), playerInv ? left + 97 : left + 8, playerInv ? top + 8 : top + 6, LABEL_COLOR, false);
            if (textured && !playerInv) {
                g.drawString(minecraft.font, Component.translatable("container.inventory"), left + 8, top + s.imageHeight() - 94, LABEL_COLOR, false);
            }
        }

        // Player model in the preview box; it turns to look at the ghost cursor like vanilla does
        if (playerInv && textured && minecraft.getCameraEntity() instanceof LivingEntity living) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, left + 26, top + 8, left + 75, top + 78,
                30, 0.0625F, mx, my, living);
        }

        // Slot backgrounds are baked into the vanilla textures; only draw them for the generic panel
        if (!textured) {
            for (InventoryScreenState.SlotEntry slot : s.slots()) {
                drawSlotBackground(g, left + slot.x() - 1, top + slot.y() - 1);
            }
        }

        // Outlines in empty armor slots and the empty offhand slot, like vanilla
        if ((playerInv && textured) || (isCreative && creative.inventoryTab())) {
            for (int i = 0; i < EMPTY_ARMOR_ICONS.length; i++) {
                int index = 5 + i;
                if (index < s.slots().size() && s.slots().get(index).item().isEmpty()) {
                    InventoryScreenState.SlotEntry armorSlot = s.slots().get(index);
                    g.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_ARMOR_ICONS[i], left + armorSlot.x(), top + armorSlot.y(), 16, 16);
                }
            }
            if (s.slots().size() > 45 && s.slots().get(45).item().isEmpty()) {
                InventoryScreenState.SlotEntry offhandSlot = s.slots().get(45);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_SHIELD_ICON, left + offhandSlot.x(), top + offhandSlot.y(), 16, 16);
            }
        }

        InventoryScreenState.SlotEntry hovered = null;
        for (InventoryScreenState.SlotEntry slot : s.slots()) {
            int sx = left + slot.x();
            int sy = top + slot.y();
            if (!slot.item().isEmpty()) {
                g.renderItem(slot.item(), sx, sy);
                g.renderItemDecorations(minecraft.font, slot.item(), sx, sy);
            }
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                hovered = slot;
            }
        }
        if (hovered != null) {
            int hx = left + hovered.x();
            int hy = top + hovered.y();
            g.fill(hx, hy, hx + 16, hy + 16, 0x80FFFFFF);
        }

        // The recipe book panel (when it was open), drawn over the inventory like vanilla does on narrow windows
        ReplayInventoryExtras.renderRecipeBook(g, minecraft, guiW, guiH, extras);

        // Item held on the cursor, then the cursor itself on top
        ItemStack carried = s.carried();
        if (!carried.isEmpty()) {
            g.renderItem(carried, (int) mx - 8, (int) my - 8);
            g.renderItemDecorations(minecraft.font, carried, (int) mx - 8, (int) my - 8);
        }
        drawCursor(g, mx, my);
        // Item tooltip for the hovered slot, like vanilla (hidden while an item is held on the cursor)
        if (hovered != null && carried.isEmpty() && !hovered.item().isEmpty()) {
            g.setTooltipForNextFrame(minecraft.font, hovered.item(), (int) mx, (int) my);
            // A replay has no open screen to draw queued tooltips, so draw it now
            g.renderDeferredElements();
        }
    }

    private static boolean isPlayerInventory(InventoryScreenState s) {
        // The player's own InventoryMenu has no menu type and exactly 46 slots
        return s.menuType() == null && s.slots().size() == 46;
    }

    /** Draws the vanilla texture for this container if we know it. Returns false for unknown/modded screens. */
    private static boolean drawVanillaBackground(GuiGraphics g, InventoryScreenState s, int left, int top) {
        ResourceLocation type = s.menuType();
        if (type == null) {
            if (isPlayerInventory(s)) {
                g.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEX, left, top, 0f, 0f, 176, 166, 256, 256);
                return true;
            }
            return false;
        }
        if (type.getNamespace().equals("minecraft") && type.getPath().startsWith("generic_9x")) { // chests etc.
            int rows = (s.slots().size() - 36) / 9;
            if (rows >= 1 && rows <= 6) {
                int upper = rows * 18 + 17;
                g.blit(RenderPipelines.GUI_TEXTURED, GENERIC_54_TEX, left, top, 0f, 0f, 176, upper, 256, 256);
                g.blit(RenderPipelines.GUI_TEXTURED, GENERIC_54_TEX, left, top + upper, 0f, 126f, 176, 96, 256, 256);
                return true;
            }
        }
        return false;
    }

    /** Vanilla-style grey panel with bevel. */
    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF000000);                         // outline
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);         // body
        g.fill(x + 1, y + 1, x + w - 2, y + 3, 0xFFFFFFFF);             // top highlight
        g.fill(x + 1, y + 1, x + 3, y + h - 2, 0xFFFFFFFF);             // left highlight
        g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);     // bottom shadow
        g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, 0xFF555555);     // right shadow
    }

    /** 18x18 recessed slot. (x, y) is the top-left of the 18x18 area, i.e. slot origin - 1. */
    private static void drawSlotBackground(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        g.fill(x, y, x + 17, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + 17, 0xFF373737);
        g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
    }

    /** Draws the cursor texture; the arrow's tip is the top-left pixel, so (x, y) is the hotspot. */
    private static void drawCursor(GuiGraphics g, float x, float y) {
        // Draw at real screen-pixel size (12x19) like the system pointer, whatever the GUI scale is
        float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float px = Math.round(x * guiScale) / guiScale;
        float py = Math.round(y * guiScale) / guiScale;
        g.pose().pushMatrix();
        g.pose().translate(px, py);
        g.pose().scale(1.0f / guiScale, 1.0f / guiScale);
        g.blit(RenderPipelines.GUI_TEXTURED, CURSOR_TEX, 0, 0, 0f, 0f, 12, 19, 12, 19);
        g.pose().popMatrix();
    }
}
