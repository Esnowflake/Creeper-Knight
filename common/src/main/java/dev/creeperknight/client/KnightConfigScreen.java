package dev.creeperknight.client;

import dev.creeperknight.config.KnightConfig;
import dev.creeperknight.net.ConfigService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Incomplete numeric text and valid edits remain a local draft until Save succeeds. */
public final class KnightConfigScreen extends Screen {
    private final Screen parent;
    private KnightConfig draft;
    private KnightConfig.Group group = KnightConfig.Group.RIDERS;
    private final Map<String, String> numbers = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private String status = "creeperknight.status.loading";
    private boolean pending, confirming, dragging;
    private int left, right, optionsLeft, top, bottom, scroll;
    private record Row(KnightConfig.Option option, AbstractWidget widget, int index) {}
    public KnightConfigScreen(Screen parent) { super(Component.translatable("creeperknight.title")); this.parent = parent; }
    @Override protected void init() {
        rows.clear();
        if (minecraft.player == null && draft == null) status = "creeperknight.status.enterworld";
        int panel = Math.min(760, width - 24);
        left = (width - panel) / 2; right = left + panel;
        int sidebar = Math.max(94, Math.min(144, panel / 4));
        optionsLeft = left + sidebar + 14; top = 64; bottom = height - 80;
        int categoryStep = Math.min(25, Math.max(14, (bottom - 42) / KnightConfig.Group.values().length));
        for (var category : KnightConfig.Group.values()) {
            Button button = Button.builder(Component.translatable("creeperknight.group." + category.name().toLowerCase()), b -> {
                group = category; scroll = 0; rebuildWidgets();
            }).bounds(left + 4, 42 + category.ordinal() * categoryStep, sidebar - 4, Math.min(20, categoryStep - 2)).build();
            button.active = category != group && !pending; addRenderableWidget(button);
        }
        if (draft != null) {
            int index = 0;
            int controlWidth = Math.min(126, (right - optionsLeft) / 3);
            int controlX = right - controlWidth - 12;
            for (var option : KnightConfig.OPTIONS.stream().filter(o -> o.group() == group).toList()) {
                AbstractWidget widget;
                if (option.isBoolean() || option.isChoice()) {
                    Button button = Button.builder(valueLabel(option), b -> {
                        if (option.isBoolean()) option.set(draft, !(Boolean)option.get(draft));
                        else { int next = ((Number)option.get(draft)).intValue() + 1; option.set(draft, next > option.max() ? (int)option.min() : next); }
                        b.setMessage(valueLabel(option)); refreshRows();
                    }).bounds(controlX, top, controlWidth, 20).build();
                    widget = button;
                } else {
                    EditBox box = new EditBox(font, controlX, top, controlWidth, 20, label(option));
                    box.setMaxLength(16);
                    box.setValue(numbers.computeIfAbsent(option.key(), k -> option.get(draft).toString()));
                    box.setResponder(value -> {
                        numbers.put(option.key(), value);
                        colorNumber(option, box);
                    });
                    colorNumber(option, box); widget = box;
                }
                rows.add(new Row(option, widget, index++)); addWidget(widget);
            }
        }
        refreshRows();
        int third = panel / 3, footer = height - 30;
        Button reset = Button.builder(Component.translatable("creeperknight.reset"), b -> {
            draft = new KnightConfig(); numbers.clear(); status = "creeperknight.status.defaults"; rebuildWidgets();
        }).bounds(left, footer, third - 4, 20).build();
        reset.active = draft != null && KnightClient.editable && !pending; addRenderableWidget(reset);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(left + third, footer, third - 4, 20).build());
        Button save = Button.builder(Component.translatable("creeperknight.save"), b -> save())
            .bounds(left + third * 2, footer, panel - third * 2, 20).build();
        save.active = draft != null && KnightClient.editable && !pending; addRenderableWidget(save);
    }
    private Component label(KnightConfig.Option option) { return Component.translatable("creeperknight.option." + option.key()); }
    private Component valueLabel(KnightConfig.Option option) {
        return Component.translatable(option.isBoolean() ? (Boolean)option.get(draft) ? "options.on" : "options.off"
            : "creeperknight.choice." + option.key() + "." + ((Number)option.get(draft)).intValue());
    }
    private boolean available(KnightConfig.Option option) {
        if (option.key().equals("chaseDuringFuse")) return !draft.instantExplosion;
        if (option.key().equals("instantExplosionDistance")) return draft.instantExplosion;
        return true;
    }
    private Component tooltip(KnightConfig.Option option) {
        var text = Component.translatable("creeperknight.option." + option.key() + ".tooltip");
        if (!available(option)) text.append("\n").append(Component.translatable(
            option.key().equals("chaseDuringFuse") ? "creeperknight.disabled.instant" : "creeperknight.disabled.distance"));
        return text;
    }
    private void colorNumber(KnightConfig.Option option, EditBox box) {
        try {
            double value = Double.parseDouble(box.getValue()); option.set(new KnightConfig(), value);
            box.setTextColor(option.key().equals("wandLifetimeSeconds") && value < 2 ? 0xFFAA00 : 0xFFFFFF);
        } catch (IllegalArgumentException e) { box.setTextColor(0xFF7777); }
    }
    private int maxScroll() { return Math.max(0, rows.size() * 26 - (bottom - top)); }
    private void refreshRows() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        for (Row row : rows) {
            int y = top + row.index * 26 - scroll;
            row.widget.setY(y); row.widget.visible = y >= top && y + 20 <= bottom;
            row.widget.active = KnightClient.editable && !pending && available(row.option);
            if (row.widget instanceof EditBox box) box.setEditable(row.widget.active);
            row.widget.setTooltip(Tooltip.create(tooltip(row.option)));
            if (!row.widget.visible && getFocused() == row.widget) setFocused(null);
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (x >= optionsLeft && x <= right && y >= top && y <= bottom) {
            scroll -= (int)(delta * 26); refreshRows(); return true;
        }
        return super.mouseScrolled(x, y, delta);
    }
    private void scrollTo(double y) {
        scroll = (int)((y - top) / Math.max(1, bottom - top) * (maxScroll() + bottom - top) - (bottom - top) / 2.0);
        refreshRows();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && maxScroll() > 0 && x >= right - 7 && x <= right && y >= top && y <= bottom) {
            dragging = true; scrollTo(y); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) { scrollTo(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        dragging = false; return super.mouseReleased(x, y, button);
    }
    private void save() {
        KnightConfig candidate = draft.copy();
        try {
            for (var option : KnightConfig.OPTIONS) if (!option.isBoolean() && !option.isChoice() && numbers.containsKey(option.key()))
                option.set(candidate, Double.parseDouble(numbers.get(option.key())));
            candidate.validate();
        } catch (IllegalArgumentException e) { status = "creeperknight.status.invalid"; return; }
        draft = candidate; pending = true; status = "creeperknight.status.saving";
        KnightClient.send.accept(draft.toJson()); rebuildWidgets();
    }
    public void accept(ConfigService.Snapshot snapshot) {
        if (snapshot.message().equals("creeperknight.status.confirmblocks") && draft != null && pending) {
            String proposed = draft.toJson(); confirming = true;
            minecraft.setScreen(new ConfirmScreen(confirmed -> {
                confirming = false; minecraft.setScreen(this);
                if (confirmed) { pending = true; status = "creeperknight.status.saving"; KnightClient.send.accept(ConfigService.confirmBlockDamage(proposed)); }
                else { pending = false; status = "creeperknight.status.cancelled"; KnightClient.send.accept(""); }
                rebuildWidgets();
            }, Component.translatable("creeperknight.confirm.title"), Component.translatable("creeperknight.confirm.blocks")) {
                @Override public void onClose() { KnightConfigScreen.this.confirming = false; KnightConfigScreen.this.onClose(); }
            }); return;
        }
        if (draft == null || pending && snapshot.message().equals("creeperknight.status.saved")) {
            draft = KnightClient.config.copy(); numbers.clear();
        }
        if (!snapshot.message().isEmpty()) { pending = false; status = snapshot.message(); }
        else if (!KnightClient.editable) status = "creeperknight.status.readonly";
        else if (status.equals("creeperknight.status.loading")) status = "creeperknight.status.ready";
        rebuildWidgets();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.fill(left, 36, optionsLeft - 8, bottom + 4, 0x990C141C);
        graphics.fill(optionsLeft - 4, 36, right + 2, bottom + 4, 0x99172029);
        graphics.drawString(font, Component.translatable("creeperknight.group." + group.name().toLowerCase()), optionsLeft + 2, 44, 0xFFE399);
        graphics.enableScissor(optionsLeft, top, right, bottom);
        for (Row row : rows) if (row.widget.visible) {
            int y = row.widget.getY();
            graphics.drawString(font, font.plainSubstrByWidth(label(row.option).getString(), row.widget.getX() - optionsLeft - 8),
                optionsLeft + 2, y + 6, available(row.option) ? 0xEEEEEE : 0x888888);
            row.widget.render(graphics, mouseX, mouseY, partialTick);
            if (mouseX >= optionsLeft && mouseX < right - 8 && mouseY >= y && mouseY < y + 20) setTooltipForNextRenderPass(tooltip(row.option));
        }
        graphics.disableScissor();
        if (maxScroll() > 0) {
            int track = bottom - top;
            int thumb = Math.max(20, track * track / (rows.size() * 26));
            int y = top + scroll * (track - thumb) / maxScroll();
            graphics.fill(right - 5, top, right - 1, bottom, 0xFF303A46);
            graphics.fill(right - 5, y, right - 1, y + thumb, 0xFF9AAFC1);
        }
        String shown = status;
        if (group == KnightConfig.Group.SCEPTER && draft != null) {
            try { if (Double.parseDouble(numbers.getOrDefault("wandLifetimeSeconds", Integer.toString(draft.wandLifetimeSeconds))) < 2) shown = "creeperknight.wand.selfwarning"; }
            catch (NumberFormatException ignored) {}
        }
        graphics.drawCenteredString(font, Component.translatable(shown), width / 2, height - 65, 0xFFE399);
        graphics.drawCenteredString(font, Component.translatable("creeperknight.gamerule"), width / 2, height - 49, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { if (!confirming) { draft = null; numbers.clear(); pending = false; } }
    @Override public void onClose() { confirming = false; draft = null; numbers.clear(); pending = false; minecraft.setScreen(parent); }
}
