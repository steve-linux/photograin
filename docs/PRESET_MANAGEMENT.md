# Saved preset management

In Looks, choose Manage presets below the preset chips. Search saved preset names, rename
or duplicate a preset, and confirm before deleting. Built-in presets are unchanged. Applying
presets stays on the existing chips; deleting a saved preset no longer happens through a
small chip cross. LUT controls are unchanged.

Renaming preserves the preset ID and all adjustments. Duplicating creates a new ID and
copies adjustments (including LUT references); deleting or renaming it does not affect the
source preset. Names are trimmed, limited to 40 characters and cannot be blank. Existing
photo edits keep their adjustment values after a preset is renamed or deleted.

The storage format remains the same presets.json list. Mutations use a process-wide lock
and a unique temporary file, checking replacement success before reporting completion.
A malformed existing library may display empty as before, but mutations refuse to overwrite
it; failures show an error in the editor. This is not an automatic recovery/backup system.
Operations run on the existing serial save dispatcher, away from the UI thread.

Tests cover persistence across instances, stable rename identity/order, independent copies,
invalid names, corrupt-library preservation, missing IDs, name length and temporary-file
cleanup. Physical-device tests are separate from this PC-only validation.
