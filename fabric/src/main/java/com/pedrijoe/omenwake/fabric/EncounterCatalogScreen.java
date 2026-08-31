package com.pedrijoe.omenwake.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import com.pedrijoe.omenwake.catalog.DiscoveryState;
import com.pedrijoe.omenwake.catalog.VisibleCatalogSnapshot;
import com.pedrijoe.omenwake.catalog.VisibleEncounterEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class EncounterCatalogScreen extends Screen {
    private static final int OUTER_MARGIN = 12;
    private static final int MAX_CONTENT_WIDTH = 1000;
    private static final int HEADER_HEIGHT = 46;
    private static final int FILTER_HEIGHT = 22;
    private static final int FOOTER_HEIGHT = 24;
    private static final int CONTENT_GAP = 7;
    private static final int ROW_HEIGHT = 34;
    private static final int NARROW_WIDTH_THRESHOLD = 420;
    private static final int FILTER_GAP = 4;
    private static final int MAX_SEARCH_WIDTH = 280;
    private static final int OVERLAY = 0x9A0B1523;
    private static final int HEADER_PANEL = 0xD0172636;
    private static final int PANEL = 0xC0142231;
    private static final int PANEL_BORDER = 0xA0496075;
    private static final int ROW = 0x8A172535;
    private static final int ROW_ALTERNATE = 0x96203344;
    private static final int ROW_HOVER = 0xCC29435A;
    private static final int ROW_SELECTED = 0xE0325069;
    private static final int ACCENT = 0xFF65D8E7;
    private static final int COMPLETED = 0xFF8ED69A;
    private static final int DISCOVERED = 0xFF8BCBEB;
    private static final int MUTED = 0xFFB2C0CB;
    private static final int TEXT = 0xFFF2F6F8;
    private static final int SECONDARY_TEXT = 0xFFC6D0D8;
    private static final int AMBER = 0xFFF2C46B;

    private VisibleCatalogSnapshot snapshot = VisibleCatalogSnapshot.EMPTY;
    private long observedRevision = -1L;
    private CatalogFilter filter = CatalogFilter.ALL;
    private String searchQuery = "";

    private CatalogEntryList entryList;
    private EditBox searchBox;
    private Button allFilterButton;
    private Button undiscoveredFilterButton;
    private Button discoveredFilterButton;
    private Button completedFilterButton;

    private List<UiEntry> visibleEntries = List.of();
    private UiEntry selectedEntry;

    private int detailX;
    private int detailY;
    private int detailWidth;
    private int detailHeight;
    private boolean narrowLayout;

    private int cachedDetailWidth = -1;
    private long cachedDetailRevision = -1L;
    private String cachedDetailIdentity = "";
    private List<FormattedCharSequence> cachedDescriptionLines = List.of();
    private List<FormattedCharSequence> cachedTriggerLines = List.of();
    private List<FormattedCharSequence> cachedObjectiveLines = List.of();

    public EncounterCatalogScreen() {
        super(Component.translatable("screen.omenwake.catalog.title"));
    }

    @Override
    protected void init() {
        snapshot = ClientCatalogStore.getInstance().snapshot();
        observedRevision = snapshot.revision();

        int contentLeft = contentLeft();
        int contentTop = OUTER_MARGIN;
        int contentWidth = contentWidth();
        int contentHeight = height - (OUTER_MARGIN * 2);

        narrowLayout = width < NARROW_WIDTH_THRESHOLD;
        int filterAreaHeight = filterAreaHeight();
        int bodyTop = contentTop + HEADER_HEIGHT + filterAreaHeight + CONTENT_GAP;
        int bodyHeight = Math.max(60, contentHeight - HEADER_HEIGHT - filterAreaHeight - FOOTER_HEIGHT - CONTENT_GAP);

        int listLeft = contentLeft;
        int listWidth;

        if (narrowLayout) {
            int listHeight = Math.max(80, (bodyHeight * 48) / 100);
            listWidth = contentWidth;
            detailX = contentLeft;
            detailY = bodyTop + listHeight + CONTENT_GAP;
            detailWidth = contentWidth;
            detailHeight = Math.max(48, bodyHeight - listHeight - CONTENT_GAP);
            bodyHeight = listHeight;
        } else {
            listWidth = Math.max(160, (contentWidth * 38) / 100);
            detailX = listLeft + listWidth + CONTENT_GAP;
            detailY = bodyTop;
            detailWidth = Math.max(140, contentWidth - listWidth - CONTENT_GAP);
            detailHeight = bodyHeight;
        }

        int filterGroupWidth = CatalogFilter.totalWidth(FILTER_GAP);
        int searchWidth = Math.min(MAX_SEARCH_WIDTH,
            Math.max(110, contentWidth - filterGroupWidth - FILTER_GAP));
        searchBox = addRenderableWidget(new EditBox(font,
                contentLeft + contentWidth - searchWidth,
                contentTop + HEADER_HEIGHT + (narrowLayout ? FILTER_HEIGHT + FILTER_GAP : 0),
                searchWidth,
                FILTER_HEIGHT,
                Component.translatable("screen.omenwake.catalog.search")));
        searchBox.setHint(Component.translatable("screen.omenwake.catalog.search"));
        searchBox.setMaxLength(64);
        searchBox.setResponder(query -> {
            searchQuery = query;
            rebuildVisibleEntries(false);
        });

        int filterY = contentTop + HEADER_HEIGHT;
        int filterX = contentLeft;
        allFilterButton = addRenderableWidget(createFilterButton(CatalogFilter.ALL, filterX, filterY));
        filterX += CatalogFilter.ALL.width() + FILTER_GAP;
        undiscoveredFilterButton = addRenderableWidget(createFilterButton(CatalogFilter.UNDISCOVERED, filterX, filterY));
        filterX += CatalogFilter.UNDISCOVERED.width() + FILTER_GAP;
        discoveredFilterButton = addRenderableWidget(createFilterButton(CatalogFilter.DISCOVERED, filterX, filterY));
        filterX += CatalogFilter.DISCOVERED.width() + FILTER_GAP;
        completedFilterButton = addRenderableWidget(createFilterButton(CatalogFilter.COMPLETED, filterX, filterY));

        int closeWidth = 70;
        addRenderableWidget(Button.builder(Component.translatable("screen.omenwake.catalog.close"),
                        button -> onClose())
                .bounds(contentLeft + contentWidth - closeWidth,
                        contentTop + contentHeight - FOOTER_HEIGHT,
                        closeWidth,
                        20)
                .build());

        entryList = addRenderableWidget(new CatalogEntryList(minecraft, listWidth, bodyHeight, bodyTop, ROW_HEIGHT,
                this::onEntrySelected));
        entryList.setX(listLeft);
        entryList.setY(bodyTop);

        rebuildVisibleEntries(true);
        setInitialFocus(searchBox);
    }

    @Override
    public void tick() {
        super.tick();

        VisibleCatalogSnapshot latest = ClientCatalogStore.getInstance().snapshot();
        if (latest.revision() != observedRevision) {
            snapshot = latest;
            observedRevision = latest.revision();
            rebuildVisibleEntries(false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        extractMenuBackground(graphics);
        graphics.fill(0, 0, width, height, OVERLAY);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);

        int contentLeft = contentLeft();
        int contentTop = OUTER_MARGIN;
        int contentWidth = contentWidth();

        renderHeader(graphics, contentLeft, contentTop, contentWidth);
        renderFilterArea(graphics, contentLeft, contentTop + HEADER_HEIGHT, contentWidth);

        renderDetailPanel(graphics, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }

        int key = event.key();
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE)
                && getFocused() == entryList
                && entryList.getSelected() != null) {
            onEntrySelected(entryList.getSelected().entry());
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private Button createFilterButton(CatalogFilter targetFilter, int x, int y) {
        return Button.builder(targetFilter.label(filterCount(targetFilter)), button -> {
                    filter = targetFilter;
                    rebuildVisibleEntries(false);
                    refreshFilterButtonStates();
                })
                .bounds(x, y, targetFilter.width(), FILTER_HEIGHT)
                .build();
    }

    private int filterCount(CatalogFilter targetFilter) {
        return switch (targetFilter) {
            case ALL -> snapshot.totalEntries();
            case UNDISCOVERED -> snapshot.totalEntries() - snapshot.discoveredCount();
            case DISCOVERED -> snapshot.discoveredCount() - snapshot.completedCount();
            case COMPLETED -> snapshot.completedCount();
        };
    }

    private void rebuildVisibleEntries(boolean fromInit) {
        String selectedIdentity = selectedIdentity();
        List<UiEntry> rebuilt = new ArrayList<>();

        for (VisibleEncounterEntry entry : snapshot.entries()) {
            UiEntry uiEntry = UiEntry.from(entry);
            if (!matchesFilter(uiEntry) || !matchesSearch(uiEntry)) {
                continue;
            }
            rebuilt.add(uiEntry);
        }

        rebuilt.sort(Comparator.comparingInt((UiEntry entry) -> statePriority(entry.state())));

        visibleEntries = List.copyOf(rebuilt);
        selectedEntry = selectEntry(selectedIdentity);
        entryList.replaceData(visibleEntries, selectedEntry);
        if (!fromInit && selectedEntry != null) {
            entryList.ensureVisible(selectedEntry);
        }
        invalidateDetailCache();
    }

    private static int statePriority(DiscoveryState state) {
        return switch (state) {
            case COMPLETED -> 0;
            case DISCOVERED -> 1;
            case UNDISCOVERED -> 2;
        };
    }

    private void refreshFilterButtonStates() {
        allFilterButton.active = filter != CatalogFilter.ALL;
        undiscoveredFilterButton.active = filter != CatalogFilter.UNDISCOVERED;
        discoveredFilterButton.active = filter != CatalogFilter.DISCOVERED;
        completedFilterButton.active = filter != CatalogFilter.COMPLETED;
    }

    private boolean matchesFilter(UiEntry entry) {
        return switch (filter) {
            case ALL -> true;
            case UNDISCOVERED -> !entry.known();
            case DISCOVERED -> entry.known() && entry.state() == DiscoveryState.DISCOVERED;
            case COMPLETED -> entry.known() && entry.state() == DiscoveryState.COMPLETED;
        };
    }

    private boolean matchesSearch(UiEntry entry) {
        String query = searchQuery.trim();
        if (query.isEmpty()) {
            return true;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        return entry.searchableText().contains(normalized);
    }

    private UiEntry selectEntry(String selectedIdentity) {
        if (visibleEntries.isEmpty()) {
            return null;
        }
        if (!selectedIdentity.isEmpty()) {
            for (UiEntry entry : visibleEntries) {
                if (entry.identity().equals(selectedIdentity)) {
                    return entry;
                }
            }
        }
        return visibleEntries.getFirst();
    }

    private String selectedIdentity() {
        return selectedEntry == null ? "" : selectedEntry.identity();
    }

    private void onEntrySelected(UiEntry entry) {
        selectedEntry = entry;
        invalidateDetailCache();
    }

        private void renderHeader(GuiGraphicsExtractor graphics, int x, int y, int contentWidth) {
        graphics.fill(x, y, x + contentWidth, y + HEADER_HEIGHT, HEADER_PANEL);
        graphics.outline(x, y, contentWidth, HEADER_HEIGHT, PANEL_BORDER);
        graphics.fill(x, y + HEADER_HEIGHT - 2, x + contentWidth, y + HEADER_HEIGHT, ACCENT);
        graphics.text(font, title, x + 10, y + 8, TEXT, true);
        graphics.text(font, Component.translatable("screen.omenwake.catalog.subtitle"), x + 10, y + 22, SECONDARY_TEXT, false);

        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            graphics.text(font, Component.translatable("screen.omenwake.catalog.unavailable"), x + 180, y + 18, AMBER, false);
            return;
        }
        boolean loading = snapshot.revision() == 0 && snapshot.totalEntries() == 0;
        if (loading) {
            graphics.text(font, Component.translatable("screen.omenwake.catalog.loading"), x + 180, y + 18, TEXT, false);
            return;
        }
        if (snapshot.totalEntries() == 0) {
            graphics.text(font, Component.translatable("screen.omenwake.catalog.empty"), x + 180, y + 18, TEXT, false);
            return;
        }
        int summaryX = Math.max(x + 188, x + contentWidth - 275);
        if (contentWidth < 520) {
            graphics.text(font, Component.translatable("screen.omenwake.catalog.summary.discovered",
                snapshot.discoveredCount(), snapshot.totalEntries()), summaryX, y + 9, DISCOVERED, false);
            graphics.text(font, Component.translatable("screen.omenwake.catalog.summary.completed",
                snapshot.completedCount(), snapshot.totalEntries()), summaryX, y + 23, COMPLETED, false);
            return;
        }
        graphics.text(font, Component.translatable("screen.omenwake.catalog.summary.discovered",
            snapshot.discoveredCount(), snapshot.totalEntries()), summaryX, y + 9, DISCOVERED, false);
        graphics.text(font, Component.translatable("screen.omenwake.catalog.summary.completed",
            snapshot.completedCount(), snapshot.totalEntries()), summaryX, y + 23, COMPLETED, false);
        graphics.text(font, Component.translatable("screen.omenwake.catalog.summary.available_points",
            snapshot.availablePoints()), summaryX + 138, y + 9, AMBER, false);
        graphics.text(font, Component.translatable("screen.omenwake.catalog.summary.points",
            snapshot.lifetimePoints()), summaryX + 138, y + 23, SECONDARY_TEXT, false);
    }

        private void renderFilterArea(GuiGraphicsExtractor graphics, int x, int y, int contentWidth) {
        graphics.fill(x, y, x + contentWidth, y + filterAreaHeight(), 0x6E101D2B);
        graphics.outline(x, y, contentWidth, filterAreaHeight(), 0x70496075);
        renderFilterIndicator(graphics, allFilterButton, CatalogFilter.ALL, y);
        renderFilterIndicator(graphics, undiscoveredFilterButton, CatalogFilter.UNDISCOVERED, y);
        renderFilterIndicator(graphics, discoveredFilterButton, CatalogFilter.DISCOVERED, y);
        renderFilterIndicator(graphics, completedFilterButton, CatalogFilter.COMPLETED, y);
        int searchX = searchBox.getX();
        int searchY = searchBox.getY();
        graphics.outline(searchX - 1, searchY - 1, searchBox.getWidth() + 2, searchBox.getHeight() + 2,
            searchBox.isFocused() ? ACCENT : PANEL_BORDER);
        }

        private void renderFilterIndicator(GuiGraphicsExtractor graphics, Button button, CatalogFilter tab, int y) {
        if (filter == tab) {
            graphics.fill(button.getX(), y + FILTER_HEIGHT - 2, button.getX() + button.getWidth(), y + FILTER_HEIGHT, ACCENT);
        }
        }

    private void renderDetailPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(detailX, detailY, detailX + detailWidth, detailY + detailHeight, PANEL);
        graphics.outline(detailX, detailY, detailWidth, detailHeight, PANEL_BORDER);

        int x = detailX + 8;
        int y = detailY + 8;
        int availableWidth = Math.max(80, detailWidth - 16);

        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            graphics.text(font, Component.translatable("screen.omenwake.catalog.unavailable"), x, y, AMBER, false);
            return;
        }
        boolean loading = snapshot.revision() == 0 && snapshot.totalEntries() == 0;
        if (loading) {
            graphics.text(font, Component.translatable("screen.omenwake.catalog.loading"), x, y, TEXT, false);
            return;
        }
        if (snapshot.totalEntries() == 0) {
            graphics.text(font, Component.translatable("screen.omenwake.catalog.empty"), x, y, TEXT, false);
            return;
        }
        if (visibleEntries.isEmpty()) {
            graphics.text(font, Component.translatable("screen.omenwake.catalog.no_results"), x, y, TEXT, false);
            return;
        }
        if (selectedEntry == null) {
            graphics.text(font, Component.translatable("screen.omenwake.catalog.select_entry"), x, y, TEXT, false);
            return;
        }

        rebuildDetailCacheIfNeeded(availableWidth);

        graphics.text(font, selectedEntry.title(), x, y, TEXT, true);
        y += 13;
        graphics.fill(x, y + 2, x + 4, y + 6, selectedEntry.stateColor());
        graphics.text(font, selectedEntry.stateLabel(), x + 8, y, selectedEntry.stateColor(), false);
        y += 14;
        graphics.fill(x, y, x + availableWidth, y + 1, PANEL_BORDER);
        y += 7;

        graphics.text(font, Component.translatable("screen.omenwake.catalog.section.overview"), x, y, ACCENT, false);
        y += 11;

        if (!selectedEntry.known()) {
            graphics.text(font, Component.translatable("screen.omenwake.catalog.unknown_description"), x, y, SECONDARY_TEXT, false);
            return;
        }

        for (FormattedCharSequence line : cachedDescriptionLines) {
            graphics.text(font, line, x, y, SECONDARY_TEXT, false);
            y += 10;
        }

        y += 5;
        graphics.text(font, Component.translatable("screen.omenwake.catalog.section.activation"), x, y, ACCENT, false);
        y += 11;
        for (FormattedCharSequence line : cachedTriggerLines) {
            graphics.text(font, line, x, y, DISCOVERED, false);
            y += 10;
        }
        graphics.text(font, Component.translatable("screen.omenwake.catalog.activation_chance",
                selectedEntry.activationChancePercent()), x, y, AMBER, false);
        y += 15;

        graphics.text(font, Component.translatable("screen.omenwake.catalog.section.objective"), x, y, ACCENT, false);
        y += 11;
        for (FormattedCharSequence line : cachedObjectiveLines) {
            graphics.text(font, line, x, y, COMPLETED, false);
            y += 10;
        }

        y += 6;
        graphics.text(font, Component.translatable("screen.omenwake.catalog.section.progress"), x, y, ACCENT, false);
        y += 11;
        int statisticWidth = Math.max(52, (availableWidth - 6) / 2);
        graphics.fill(x, y, x + statisticWidth, y + 28, 0x7A203346);
        graphics.fill(x + statisticWidth + 6, y, x + (statisticWidth * 2) + 6, y + 28, 0x7A203346);
        graphics.text(font,
                String.valueOf(selectedEntry.participationCount()),
                x + 6,
                y,
                TEXT,
                true);
        graphics.text(font,
            Component.translatable("screen.omenwake.catalog.stat.attempts"),
                x + 6,
                y + 13,
                SECONDARY_TEXT,
                false);
        graphics.text(font, String.valueOf(selectedEntry.completionCount()), x + statisticWidth + 12, y, TEXT, true);
        graphics.text(font,
            Component.translatable("screen.omenwake.catalog.stat.completions"),
                x + statisticWidth + 12,
                y + 13,
                SECONDARY_TEXT,
                false);
        y += 35;

        graphics.text(font, Component.translatable("screen.omenwake.catalog.section.protection"), x, y, ACCENT, false);
        y += 11;
        if (selectedEntry.protectionCharges() > 0) {
            graphics.fill(x, y + 2, x + Math.min(availableWidth, selectedEntry.protectionCharges() * 8), y + 6, AMBER);
            graphics.text(font,
                    Component.translatable("screen.omenwake.catalog.protection", selectedEntry.protectionCharges()),
                    x + 4,
                    y + 9,
                    AMBER,
                    false);
            y += 20;
        } else {
            graphics.text(font,
                    Component.translatable("screen.omenwake.catalog.no_protection"),
                    x,
                    y,
                    MUTED,
                    false);
            y += 12;
        }

        if (!selectedEntry.completedVariantKeys().isEmpty()) {
            graphics.text(font, Component.translatable("screen.omenwake.catalog.section.variants"), x, y, ACCENT, false);
            y += 11;
            graphics.text(font,
                    Component.translatable("screen.omenwake.catalog.variants",
                            selectedEntry.completedVariantKeys().size(), selectedEntry.completedVariantKeys().size()),
                    x,
                    y,
                    AMBER,
                    false);
        }
    }

    private int contentLeft() {
        return Math.max(OUTER_MARGIN, (width - contentWidth()) / 2);
    }

    private int contentWidth() {
        return Math.min(MAX_CONTENT_WIDTH, Math.max(120, width - (OUTER_MARGIN * 2)));
    }

    private int filterAreaHeight() {
        return narrowLayout ? (FILTER_HEIGHT * 2) + FILTER_GAP : FILTER_HEIGHT;
    }

    private void rebuildDetailCacheIfNeeded(int widthForWrap) {
        String identity = selectedIdentity();
        if (selectedEntry == null || !selectedEntry.known()) {
            cachedDetailIdentity = identity;
            cachedDetailWidth = widthForWrap;
            cachedDetailRevision = snapshot.revision();
            cachedDescriptionLines = List.of();
            cachedTriggerLines = List.of();
            cachedObjectiveLines = List.of();
            return;
        }

        if (cachedDetailWidth == widthForWrap
                && cachedDetailRevision == snapshot.revision()
                && Objects.equals(cachedDetailIdentity, identity)) {
            return;
        }

        cachedDetailIdentity = identity;
        cachedDetailWidth = widthForWrap;
        cachedDetailRevision = snapshot.revision();
        cachedDescriptionLines = List.copyOf(font.split(selectedEntry.description(), widthForWrap));
        cachedTriggerLines = List.copyOf(font.split(selectedEntry.trigger(), widthForWrap));
        cachedObjectiveLines = List.copyOf(font.split(
                Component.translatable("screen.omenwake.catalog.objective", selectedEntry.objective()),
                widthForWrap));
    }

    private void invalidateDetailCache() {
        cachedDetailWidth = -1;
    }

    private enum CatalogFilter {
        ALL("screen.omenwake.catalog.filter.all", 56),
        UNDISCOVERED("screen.omenwake.catalog.filter.undiscovered", 112),
        DISCOVERED("screen.omenwake.catalog.filter.discovered", 98),
        COMPLETED("screen.omenwake.catalog.filter.completed", 92);

        private final String translationKey;
        private final int width;

        CatalogFilter(String translationKey, int width) {
            this.translationKey = translationKey;
            this.width = width;
        }

        static int totalWidth(int gap) {
            return java.util.Arrays.stream(values()).mapToInt(CatalogFilter::width).sum()
                    + gap * (values().length - 1);
        }

        public Component label(int count) {
            return Component.translatable(translationKey, count);
        }

        public int width() {
            return width;
        }
    }

    private record UiEntry(
            boolean known,
            String identity,
            DiscoveryState state,
            Component title,
            Component description,
            Component objective,
            Component trigger,
            int activationChancePercent,
            int participationCount,
            int completionCount,
            int protectionCharges,
            List<String> completedVariantKeys,
            String searchableText
    ) {
        static UiEntry from(VisibleEncounterEntry source) {
            if (source instanceof VisibleEncounterEntry.Unknown unknown) {
                Component title = Component.translatable("screen.omenwake.catalog.unknown");
                return new UiEntry(
                        false,
                        "unknown:" + unknown.slot(),
                        DiscoveryState.UNDISCOVERED,
                        title,
                        Component.translatable("screen.omenwake.catalog.unknown_description"),
                        Component.empty(),
                        Component.empty(),
                        0,
                        0,
                        0,
                        0,
                        List.of(),
                        title.getString().toLowerCase(Locale.ROOT));
            }

            VisibleEncounterEntry.Known known = (VisibleEncounterEntry.Known) source;
            Component title = Component.translatable(known.titleKey());
            Component description = Component.translatable(known.descriptionKey());
            Component objective = Component.translatable(known.objectiveKey());
            Component trigger = Component.translatable(known.triggerKey());
            String search = (title.getString() + " " + known.encounterId()).toLowerCase(Locale.ROOT);

            return new UiEntry(
                    true,
                    "known:" + known.encounterId(),
                    known.state(),
                    title,
                    description,
                    objective,
                    trigger,
                    known.activationChancePercent(),
                    known.participationCount(),
                    known.completionCount(),
                    known.protectionCharges(),
                    List.copyOf(known.completedVariantKeys()),
                    search);
        }

        Component stateLabel() {
            return Component.translatable("screen.omenwake.catalog.state." + state.name().toLowerCase(Locale.ROOT));
        }

        int stateColor() {
            return switch (state) {
                case UNDISCOVERED -> MUTED;
                case DISCOVERED -> DISCOVERED;
                case COMPLETED -> COMPLETED;
            };
        }
    }

    private static final class CatalogEntryList extends ObjectSelectionList<CatalogEntryList.CatalogRowEntry> {
        private final java.util.function.Consumer<UiEntry> onSelect;

        CatalogEntryList(net.minecraft.client.Minecraft minecraft,
                         int width,
                         int height,
                         int y,
                         int itemHeight,
                         java.util.function.Consumer<UiEntry> onSelect) {
            super(minecraft, width, height, y, itemHeight);
            this.onSelect = onSelect;
        }

        void replaceData(List<UiEntry> entries, UiEntry selected) {
            List<CatalogRowEntry> rows = new ArrayList<>(entries.size());
            CatalogRowEntry selectedRow = null;
            for (UiEntry entry : entries) {
                CatalogRowEntry row = new CatalogRowEntry(this, entry);
                rows.add(row);
                if (selected != null && selected.identity().equals(entry.identity())) {
                    selectedRow = row;
                }
            }
            replaceEntries(rows);
            if (selectedRow != null) {
                setSelected(selectedRow);
            } else if (!rows.isEmpty()) {
                setSelected(rows.getFirst());
                onSelect.accept(rows.getFirst().entry());
            }
        }

        void ensureVisible(UiEntry target) {
            for (CatalogRowEntry row : children()) {
                if (row.entry().identity().equals(target.identity())) {
                    setSelected(row);
                    centerScrollOn(row);
                    return;
                }
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(120, getWidth() - 10);
        }

        @Override
        protected int scrollBarX() {
            return getRight() - 5;
        }

        private static final class CatalogRowEntry extends ObjectSelectionList.Entry<CatalogRowEntry> {
            private final CatalogEntryList owner;
            private final UiEntry entry;

            CatalogRowEntry(CatalogEntryList owner, UiEntry entry) {
                this.owner = owner;
                this.entry = entry;
            }

            UiEntry entry() {
                return entry;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                int x = getContentX();
                int y = getContentY();
                int width = getContentWidth();
                int height = getContentHeight();
                boolean selected = owner.getSelected() == this;

                int background = selected ? ROW_SELECTED : hovered ? ROW_HOVER
                        : (getContentY() / getContentHeight()) % 2 == 0 ? ROW : ROW_ALTERNATE;
                graphics.fill(x, y, x + width, y + height, background);
                if (selected) {
                    graphics.fill(x, y, x + 4, y + height, ACCENT);
                }
                if (owner.getFocused() == this) {
                    graphics.outline(x, y, width, height, ACCENT);
                }

                int titleColor = entry.known() ? TEXT : SECONDARY_TEXT;
                graphics.text(owner.minecraft.font, entry.title(), x + 9, y + 6, titleColor, false);
                graphics.fill(x + 9, y + 22, x + 13, y + 26, entry.stateColor());
                graphics.text(owner.minecraft.font, entry.stateLabel(), x + 17, y + 20, entry.stateColor(), false);
            }

            @Override
            public Component getNarration() {
                if (!entry.known()) {
                    return Component.translatable("screen.omenwake.catalog.narration.undiscovered");
                }
                return Component.translatable("screen.omenwake.catalog.narration.known",
                        entry.title(),
                        entry.stateLabel(),
                        entry.completionCount(),
                        entry.protectionCharges());
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                if (event.button() != 0) {
                    return false;
                }
                owner.setSelected(this);
                owner.centerScrollOn(this);
                owner.onSelect.accept(entry);
                return true;
            }

            @Override
            public void updateNarration(NarrationElementOutput narrationElementOutput) {
                narrationElementOutput.add(NarratedElementType.TITLE, getNarration());
            }
        }
    }
}