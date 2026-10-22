package com.rvf.client;

import com.rvf.RVF;
import com.rvf.mixin.MixinRecipeBookComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Extra things about the open inventory screen that the plain slot list doesn't cover.
 * Right now that is the recipe book: whether it is open and what is typed in its search box.
 * It is recorded in its own action, so recordings made without it keep working.
 */
public final class ReplayInventoryExtras {

    private ReplayInventoryExtras() {}

    private static final int MAX_SEARCH_LENGTH = 64;

    public record Snapshot(boolean bookOpen, String bookSearch) {
        public static final Snapshot EMPTY = new Snapshot(false, "");

        /** Changes whenever anything visible changes. */
        public String key() {
            return this.bookOpen ? "1:" + this.bookSearch : "0";
        }
    }

    // ---- recording ----

    private static boolean failed = false;
    private static boolean searchFailed = false;

    public static Snapshot captureCurrent(Minecraft minecraft) {
        if (failed) {
            return Snapshot.EMPTY;
        }
        try {
            return capture(minecraft);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            RVF.LOGGER.error("Recipe book recording is switched off because it failed", e);
            return Snapshot.EMPTY;
        }
    }

    private static Snapshot capture(Minecraft minecraft) {
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)) {
            return Snapshot.EMPTY;
        }

        for (GuiEventListener child : screen.children()) {
            if (child instanceof RecipeBookComponent<?> book) {
                boolean open = book.isVisible();
                String search = "";
                if (open && !searchFailed) {
                    try {
                        EditBox box = ((MixinRecipeBookComponentAccessor) book).rvf$getSearchBox();
                        if (box != null) {
                            search = box.getValue();
                            if (search.length() > MAX_SEARCH_LENGTH) {
                                search = search.substring(0, MAX_SEARCH_LENGTH);
                            }
                        }
                    } catch (RuntimeException | LinkageError e) {
                        searchFailed = true;
                        RVF.LOGGER.error("Recipe book search text recording is switched off because it failed", e);
                    }
                }
                return new Snapshot(open, search);
            }
        }
        return Snapshot.EMPTY;
    }

    public static void encode(RegistryFriendlyByteBuf buf, Snapshot snapshot) {
        buf.writeBoolean(snapshot.bookOpen());
        if (snapshot.bookOpen()) {
            buf.writeUtf(snapshot.bookSearch());
        }
    }

    public static Snapshot decode(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return Snapshot.EMPTY;
        }
        return new Snapshot(true, buf.readUtf());
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

    /** The recorded extras if the given entity is the recorded player, otherwise EMPTY. */
    public static Snapshot getFor(int id) {
        return id == entityId ? snapshot : Snapshot.EMPTY;
    }

    /** Vanilla narrows the layout (the book overlaps the inventory instead of pushing it) below this GUI width. */
    private static final int NARROW_WIDTH = 379;

    /** Left edge of the inventory panel, including the sideways shift vanilla makes while the recipe book is open. */
    public static int panelLeft(int guiWidth, int imageWidth, Snapshot extras) {
        if (extras.bookOpen() && guiWidth >= NARROW_WIDTH) {
            return 177 + (guiWidth - imageWidth - 200) / 2;
        }
        return (guiWidth - imageWidth) / 2;
    }

    private static final ResourceLocation RECIPE_BOOK_TEX = ResourceLocation.withDefaultNamespace("textures/gui/recipe_book.png");
    private static final ResourceLocation TAB_SPRITE = ResourceLocation.withDefaultNamespace("recipe_book/tab");
    private static final ResourceLocation TAB_SELECTED_SPRITE = ResourceLocation.withDefaultNamespace("recipe_book/tab_selected");
    private static final ResourceLocation FILTER_SPRITE = ResourceLocation.withDefaultNamespace("recipe_book/filter_disabled");

    /** Draws the recipe book panel next to the inventory: background, search field, filter button and the search tab. */
    public static void renderRecipeBook(GuiGraphics g, Minecraft minecraft, int guiWidth, int guiHeight, Snapshot extras) {
        if (!extras.bookOpen()) {
            return;
        }

        int xOffset = guiWidth < NARROW_WIDTH ? 0 : 86;
        int x = (guiWidth - 147) / 2 - xOffset;
        int y = (guiHeight - 166) / 2;

        g.blit(RenderPipelines.GUI_TEXTURED, RECIPE_BOOK_TEX, x, y, 1f, 1f, 147, 166, 256, 256);

        // Search field
        Font font = minecraft.font;
        int fieldX = x + 25;
        int fieldY = y + 13;
        int fieldW = 81;
        int fieldH = 14;
        g.fill(fieldX, fieldY, fieldX + fieldW, fieldY + fieldH, 0xFFA0A0A0);
        g.fill(fieldX + 1, fieldY + 1, fieldX + fieldW - 1, fieldY + fieldH - 1, 0xFF000000);
        if (extras.bookSearch().isEmpty()) {
            g.drawString(font, Component.translatable("gui.recipebook.search_hint"), fieldX + 4, fieldY + 3, 0xFF808080, false);
        } else {
            g.drawString(font, font.plainSubstrByWidth(extras.bookSearch(), fieldW - 8), fieldX + 4, fieldY + 3, 0xFFE0E0E0, false);
        }

        // Filter button ("craftable only"), shown in its default state
        g.blitSprite(RenderPipelines.GUI_TEXTURED, FILTER_SPRITE, x + 110, y + 12, 26, 16);

        // The search tab, selected, with the compass icon vanilla uses for it
        g.blitSprite(RenderPipelines.GUI_TEXTURED, TAB_SELECTED_SPRITE, x - 32, y + 3, 35, 27);
        g.renderItem(new ItemStack(Items.COMPASS), x - 23, y + 8);
    }

}
