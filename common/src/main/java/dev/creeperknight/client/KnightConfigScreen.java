package dev.creeperknight.client;

import dev.creeperknight.config.KnightConfig;
import dev.creeperknight.net.ConfigService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

public final class KnightConfigScreen extends Screen {
    private final Screen parent;
    private KnightConfig draft;
    private KnightConfig.Group group = KnightConfig.Group.RIDERS;
    private final List<Row> rows = new ArrayList<>();
    private final List<Input> inputs = new ArrayList<>();
    private String status = "creeperknight.status.loading";
    private boolean pending;
    private int page;
    private int pageSize;
    private record Row(KnightConfig.Option option, int y) {}
    private record Input(KnightConfig.Option option, EditBox box) {}
    public KnightConfigScreen(Screen parent) {
        super(Component.translatable("creeperknight.title")); this.parent = parent;
    }
    @Override protected void init() {
        rows.clear(); inputs.clear();
        if (minecraft.player == null && draft == null) status = "creeperknight.status.enterworld";
        int panel = Math.min(440, width - 24);
        int left = (width - panel) / 2;
        int tabWidth = panel / KnightConfig.Group.values().length;
        for (var category : KnightConfig.Group.values()) {
            Button button = Button.builder(Component.translatable("creeperknight.group." + category.name().toLowerCase()), b -> {
                if (readInputs()) { group = category; page = 0; rebuildWidgets(); }
            }).bounds(left + category.ordinal() * tabWidth, 36, tabWidth - 2, 20).build();
            button.active = category != group && !pending;
            addRenderableWidget(button);
        }
        if (draft != null) {
            var allOptions = KnightConfig.OPTIONS.stream().filter(o -> o.group() == group).toList();
            pageSize = Math.max(1, Math.min(6, (height - 160) / 22));
            int pages = Math.max(1, (allOptions.size() + pageSize - 1) / pageSize);
            page = Math.min(page, pages - 1);
            var options = allOptions.stream().skip((long)page * pageSize).limit(pageSize).toList();
            int y = 66;
            for (var option : options) {
                rows.add(new Row(option, y));
                Tooltip tooltip = Tooltip.create(optionTooltip(option));
                if (option.isBoolean()) {
                    Button button = Button.builder(boolLabel((Boolean)option.get(draft)), b -> {
                        if (option.key().equals("instantExplosion") && !readInputs()) return;
                        option.set(draft, !(Boolean)option.get(draft)); b.setMessage(boolLabel((Boolean)option.get(draft)));
                        if (option.key().equals("instantExplosion")) rebuildWidgets();
                    }).bounds(left + panel - 92, y - 3, 92, 20).tooltip(tooltip).build();
                    button.active = KnightClient.editable && !pending && optionAvailable(option);
                    addRenderableWidget(button);
                } else if (option.isChoice()) {
                    Button button = Button.builder(choiceLabel(option), b -> {
                        int next = ((Number)option.get(draft)).intValue() + 1;
                        option.set(draft, next > option.max() ? (int)option.min() : next); b.setMessage(choiceLabel(option));
                    }).bounds(left + panel - 92, y - 3, 92, 20).tooltip(tooltip).build();
                    button.active = KnightClient.editable && !pending; addRenderableWidget(button);
                } else {
                    EditBox box = new EditBox(font, left + panel - 92, y - 3, 92, 20,
                        Component.translatable("creeperknight.option." + option.key()));
                    box.setMaxLength(16);
                    box.setValue(option.get(draft).toString());
                    if (option.key().equals("wandLifetimeSeconds")) {
                        box.setResponder(value -> {
                            try { double seconds = Double.parseDouble(value); box.setTextColor(seconds >= 0 && seconds < 2 ? 0xFFAA00 : 0xFFFFFF); }
                            catch (NumberFormatException e) { box.setTextColor(0xFF7777); }
                        });
                        box.setTextColor(draft.wandLifetimeSeconds < 2 ? 0xFFAA00 : 0xFFFFFF);
                    }
                    box.setEditable(KnightClient.editable && !pending && optionAvailable(option));
                    box.active = KnightClient.editable && !pending && optionAvailable(option);
                    box.setTooltip(tooltip);
                    inputs.add(new Input(option, box));
                    addRenderableWidget(box);
                }
                y += 22;
            }
            if (pages > 1) {
                Button previous = Button.builder(Component.literal("<"), b -> { if (readInputs()) { page--; rebuildWidgets(); } })
                    .bounds(left, height - 104, 28, 20).build();
                previous.active = page > 0 && !pending; addRenderableWidget(previous);
                Button next = Button.builder(Component.literal(">"), b -> { if (readInputs()) { page++; rebuildWidgets(); } })
                    .bounds(left + panel - 28, height - 104, 28, 20).build();
                next.active = page + 1 < pages && !pending; addRenderableWidget(next);
            }
        }
        int footer = height - 32;
        Button reset = Button.builder(Component.translatable("creeperknight.reset"), b -> {
            draft = new KnightConfig(); status = "creeperknight.status.defaults"; rebuildWidgets();
        }).bounds(left, footer, panel / 3 - 4, 20).build();
        reset.active = draft != null && KnightClient.editable && !pending;
        addRenderableWidget(reset);
        Button save = Button.builder(Component.translatable("creeperknight.save"), b -> save()).bounds(left + panel / 3, footer, panel / 3 - 4, 20).build();
        save.active = draft != null && KnightClient.editable && !pending;
        addRenderableWidget(save);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(left + 2 * panel / 3, footer, panel / 3, 20).build());
    }
    private Component boolLabel(boolean enabled) { return Component.translatable(enabled ? "options.on" : "options.off"); }
    private Component choiceLabel(KnightConfig.Option option) {
        return Component.translatable("creeperknight.choice." + option.key() + "." + ((Number)option.get(draft)).intValue());
    }
    private boolean optionAvailable(KnightConfig.Option option) {
        if (option.key().equals("chaseDuringFuse")) return !draft.instantExplosion;
        if (option.key().equals("instantExplosionDistance")) return draft.instantExplosion;
        return true;
    }
    private Component optionTooltip(KnightConfig.Option option) {
        var text = Component.translatable("creeperknight.option." + option.key() + ".tooltip");
        if (!optionAvailable(option)) text.append("\n").append(Component.translatable(
            option.key().equals("chaseDuringFuse") ? "creeperknight.disabled.instant" : "creeperknight.disabled.distance"));
        return text;
    }
    private boolean readInputs() {
        if (draft == null) return true;
        boolean valid = true;
        for (Input input : inputs) {
            try {
                double value = Double.parseDouble(input.box.getValue());
                input.option.set(draft, value);
                input.box.setTextColor(input.option.key().equals("wandLifetimeSeconds") && value < 2 ? 0xFFAA00 : 0xFFFFFF);
            } catch (IllegalArgumentException e) { input.box.setTextColor(0xFF7777); valid = false; }
        }
        if (!valid) status = "creeperknight.status.invalid";
        return valid;
    }
    private void save() {
        if (!readInputs()) return;
        try { draft.validate(); }
        catch (IllegalArgumentException e) { status = "creeperknight.status.invalid"; return; }
        pending = true; status = "creeperknight.status.saving";
        KnightClient.send.accept(draft.toJson()); rebuildWidgets();
    }
    public void accept(ConfigService.Snapshot snapshot) {
        if (snapshot.message().equals("creeperknight.status.confirmblocks") && draft != null && pending) {
            String proposed = draft.toJson();
            minecraft.setScreen(new ConfirmScreen(confirmed -> {
                minecraft.setScreen(this);
                if (confirmed) { pending = true; status = "creeperknight.status.saving"; KnightClient.send.accept(ConfigService.confirmBlockDamage(proposed)); }
                else { pending = false; draft.wandBlockDamage = false; status = "creeperknight.status.cancelled"; KnightClient.send.accept(""); }
                rebuildWidgets();
            }, Component.translatable("creeperknight.confirm.title"), Component.translatable("creeperknight.confirm.blocks")));
            return;
        }
        boolean wasPending = pending;
        pending = false;
        if (draft == null || wasPending) draft = KnightClient.config.copy();
        if (!snapshot.message().isEmpty()) status = snapshot.message();
        else if (!KnightClient.editable) status = "creeperknight.status.readonly";
        else if (draft != null && status.equals("creeperknight.status.loading")) status = "creeperknight.status.ready";
        rebuildWidgets();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        int panel = Math.min(440, width - 24);
        int left = (width - panel) / 2;
        for (Row row : rows) {
            Component label = Component.translatable("creeperknight.option." + row.option.key());
            graphics.drawString(font, font.plainSubstrByWidth(label.getString(), panel - 106), left + 2, row.y + 3,
                optionAvailable(row.option) ? 0xEEEEEE : 0x888888);
            if (mouseX >= left && mouseX < left + panel && mouseY >= row.y - 3 && mouseY < row.y + 17)
                setTooltipForNextRenderPass(optionTooltip(row.option));
        }
        if (draft != null) {
            int count = (int)KnightConfig.OPTIONS.stream().filter(o -> o.group() == group).count();
            int pages = Math.max(1, (count + pageSize - 1) / pageSize);
            if (pages > 1) graphics.drawCenteredString(font, Component.translatable("creeperknight.page", page + 1, pages), width / 2, height - 98, 0xDDDDDD);
            int seconds = draft.wandLifetimeSeconds;
            for (Input input : inputs) if (input.option.key().equals("wandLifetimeSeconds")) {
                try { seconds = Integer.parseInt(input.box.getValue()); } catch (NumberFormatException ignored) {}
            }
            if (group == KnightConfig.Group.SCEPTER && seconds < 2) graphics.drawCenteredString(font,
                Component.translatable("creeperknight.wand.selfwarning"), width / 2, height - 82, 0xFFAA00);
        }
        graphics.drawCenteredString(font, Component.translatable(status), width / 2, height - 64, 0xFFE399);
        graphics.drawCenteredString(font, Component.translatable("creeperknight.gamerule"), width / 2, height - 49, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
