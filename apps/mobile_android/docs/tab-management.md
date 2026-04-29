# Android Tab Management

## Behavior

- The tab strip supports creating, selecting, and closing remote-session tabs.
- Creating a tab with a link that is already open selects the existing tab instead of creating a duplicate.
- Restoring tabs from persistence collapses historical duplicate links and remaps selection to the kept tab when needed.
- Closing a non-selected tab preserves the current selection.
- Closing the selected tab selects the next tab to the right when one exists; otherwise it selects the previous tab.
- Closing the final tab clears persisted tabs and returns to the welcome screen.

## Persistence

Tab close, deduplicated create, restore cleanup, and regular select all use the same `RemoteTabSnapshot` persistence path. Every selection-changing operation writes the new snapshot through `RemoteTabStore.save`.

## Observability

- Tab create dedupe emits `mobile_tab_create_deduplicated` with the selected tab id and session hash.
- Restore cleanup emits `mobile_tab_restore_deduplicated` with the number of dropped duplicate tabs.
- Tab close emits `mobile_tab_closed` with the closed tab id, whether it was selected, and the remaining tab count. The final-tab case also emits `mobile_tab_welcome_shown`.

## Validation

Unit coverage lives in `RemoteTabCloserTest` and `RemoteTabDeduplicatorTest`. Close tests cover non-selected close, selected close, selected-last close, final-tab close, and unknown-tab no-op. Dedupe tests cover normalized-link matching, different query values, and restore selection remapping.
