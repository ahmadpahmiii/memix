# Tabs

Underlined tabs for sections of one screen, such as Trending, Local, Global, Favorites and My sounds.

- **Provide:** the tab labels and the selected one; the row scrolls when it overflows.
- **Selected:** `text` label with a 2px `selection` (white) underline. Unselected: `text-secondary`.
- **Use:** four or more sibling views. For two or three, use SegmentedTabs. **Exception:** an editor panel whose tabs arrive over several tickets (the Audio effects panel, P3-09, five tabs at the end) uses Tabs from its first ticket, so it is not restyled when the third tab lands. A tab may carry a small `primary` dot (8 dp) when something on it is set; the tab's spoken name then ends "on".
