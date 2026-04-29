# Android Tab Management

## Behavior

- The tab strip supports creating, selecting, and closing remote-session tabs.
- Closing a non-selected tab preserves the current selection.
- Closing the selected tab selects the next tab to the right when one exists; otherwise it selects the previous tab.
- Closing the final tab clears persisted tabs and returns to the welcome screen.

## Persistence

Tab close uses the same `RemoteTabSnapshot` persistence path as create and select. Every close writes the new snapshot through `RemoteTabStore.save`.

## Observability

Tab close emits `mobile_tab_closed` with the closed tab id, whether it was selected, and the remaining tab count. The final-tab case also emits `mobile_tab_welcome_shown`.

## Validation

Unit coverage lives in `RemoteTabCloserTest` and covers non-selected close, selected close, selected-last close, final-tab close, and unknown-tab no-op.
