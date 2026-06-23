# Changelog

All notable changes to Xpense are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows a
`major.minor` version scheme tracked by `versionName` in `app/build.gradle.kts`.

## [2.1] - 2026-06-23

### Changed
- Auto-rules that share the same category and display label are now consolidated into a
  single rule, with the keywords joined as `|` alternatives — no more duplicate rows that
  differ only by keyword.
  - Creating a rule (from the Rules screen, a notification tap, or the edit-sheet "Add rule"
    button) now folds the new keyword into a matching existing rule instead of adding a duplicate.
  - A new **Merge duplicate rules** action in **Settings → Auto-Rules** collapses any
    duplicates you already have (shown only when there's something to merge).

## [2.0] - 2026-06-22

### Added
- Notification when a bank SMS can't be auto-categorized (lands in **Others**); tapping it
  opens a pre-filled Add-Rule dialog so you can categorize it on the spot. On by default.
- In-app **Notifications** inbox (Profile → Notifications) that keeps these alerts even after
  the system notification panel is cleared. Entries auto-clear once the transaction is
  categorized, and only real-time SMS are recorded (history sync is excluded).
- "Add rule" button in the transaction edit sheet for any SMS transaction without a rule.
- Inline "add category" pill in the add/edit expense sheet, to create a category without
  leaving the screen.

## [1.8] - 2026-06-16

### Changed
- Manual category edits are now locked so rule re-application never overwrites your choice.
- Removed the redundant Edit icon from selection mode.

## [1.7] - 2026-06-16

### Changed
- Trim the auto-extracted merchant name at "via UPI" and removed the UPI badge.

## [1.6] - 2026-06-16

### Changed
- Replaced the ignore eye icon with a clearer archive/unarchive icon.

## [1.5] - 2026-06-16

### Added
- Tap a transaction to edit it.
- The Note field is now persisted on add/edit.

## [1.4] - 2026-06-11

### Added
- `|` OR-alternatives in auto-rule keywords and a rule search box.

## [1.3] - 2026-06-11

### Added
- Google Drive backup & restore (JSON).

## [1.2] - 2026-06-11

### Added
- Dedicated screen for ignored transactions.

### Fixed
- Stale version shown on the About row.

## [1.1] - 2026-06-11

### Added
- Ignore-transaction feature to exclude self-transfers from totals.
- Transaction search and a dedicated Insights screen for the category breakdown.
- Indian lakh/crore digit grouping for amounts.

### Fixed
- Date & time pickers not opening in the add/edit expense sheet.
- Edited SMS transactions duplicating on resync.
