package net.everwild.jukebox.client;

import gg.moonflower.etched.common.blockentity.AlbumJukeboxBlockEntity;
import net.everwild.jukebox.JukeboxSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/** Volume, range, pause and loop/repeat for one jukebox. Changes go to the server and sync to everyone. */
public class JukeboxSettingsScreen extends Screen {

    private final BlockPos pos;
    @Nullable private final Screen parent;
    private JukeboxSettings settings;
    private boolean dirty;

    public JukeboxSettingsScreen(BlockPos pos, @Nullable Screen parent) {
        super(Component.literal("Jukebox Settings"));
        this.pos = pos;
        this.parent = parent;
        this.settings = ClientJukebox.get(pos);
    }

    private boolean isAlbum() {
        return Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(this.pos) instanceof AlbumJukeboxBlockEntity;
    }

    private void set(JukeboxSettings s, boolean sendNow) {
        this.settings = s;
        ClientJukebox.applyLocalPreview(this.pos, s);
        if (sendNow) { ClientJukebox.send(this.pos, s); this.dirty = false; } else this.dirty = true;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 62;
        int w = 200;

        this.addRenderableWidget(new AbstractSliderButton(cx - w / 2, y, w, 20, Component.empty(), this.settings.volume()) {
            { this.updateMessage(); }
            @Override protected void updateMessage() {
                this.setMessage(Component.literal("Volume: " + Math.round(this.value * 100) + "%"));
            }
            @Override protected void applyValue() {
                JukeboxSettings o = JukeboxSettingsScreen.this.settings;
                JukeboxSettingsScreen.this.set(new JukeboxSettings((float) this.value, o.range(), o.paused(), o.loop(), o.repeatOne()), false);
            }
            @Override public void onRelease(double mx, double my) { super.onRelease(mx, my); JukeboxSettingsScreen.this.flush(); }
        });

        double r0 = (this.settings.range() - JukeboxSettings.MIN_RANGE) / (double) (JukeboxSettings.MAX_RANGE - JukeboxSettings.MIN_RANGE);
        this.addRenderableWidget(new AbstractSliderButton(cx - w / 2, y + 24, w, 20, Component.empty(), r0) {
            { this.updateMessage(); }
            private int blocks() { return Mth.floor(JukeboxSettings.MIN_RANGE + this.value * (JukeboxSettings.MAX_RANGE - JukeboxSettings.MIN_RANGE) + 0.5); }
            @Override protected void updateMessage() {
                this.setMessage(Component.literal("Hearing range: " + this.blocks() + " blocks"));
            }
            @Override protected void applyValue() {
                JukeboxSettings o = JukeboxSettingsScreen.this.settings;
                JukeboxSettingsScreen.this.set(new JukeboxSettings(o.volume(), this.blocks(), o.paused(), o.loop(), o.repeatOne()), false);
            }
            @Override public void onRelease(double mx, double my) { super.onRelease(mx, my); JukeboxSettingsScreen.this.flush(); }
        });

        this.addRenderableWidget(Button.builder(this.pauseLabel(), b -> {
            this.set(this.settings.withPaused(!this.settings.paused()), true);
            b.setMessage(this.pauseLabel());
        }).bounds(cx - w / 2, y + 52, w / 2 - 2, 20).build());

        if (this.isAlbum()) {
            this.addRenderableWidget(Button.builder(ClientJukebox.repeatLabel(this.settings.repeatOne()), b -> {
                JukeboxSettings o = this.settings;
                this.set(new JukeboxSettings(o.volume(), o.range(), o.paused(), o.loop(), !o.repeatOne()), true);
                b.setMessage(ClientJukebox.repeatLabel(this.settings.repeatOne()));
            }).bounds(cx + 2, y + 52, w / 2 - 2, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(this.loopLabel(), b -> {
                JukeboxSettings o = this.settings;
                this.set(new JukeboxSettings(o.volume(), o.range(), o.paused(), !o.loop(), o.repeatOne()), true);
                b.setMessage(this.loopLabel());
            }).bounds(cx + 2, y + 52, w / 2 - 2, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            this.set(JukeboxSettings.DEFAULT, true);
            this.rebuildWidgets();
        }).bounds(cx - w / 2, y + 84, w / 2 - 2, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(cx + 2, y + 84, w / 2 - 2, 20).build());
    }

    private Component pauseLabel() { return Component.literal(this.settings.paused() ? "> Play" : "|| Pause"); }
    private Component loopLabel() { return Component.literal(this.settings.loop() ? "Loop: On" : "Loop: Off"); }

    private void flush() {
        if (this.dirty) { ClientJukebox.send(this.pos, this.settings); this.dirty = false; }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 84, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.literal("Range above 64 blocks isn't possible: the server only sends music that far."),
                this.width / 2, this.height / 2 + 48, 0x808080);
    }

    @Override
    public void onClose() {
        this.flush();
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
