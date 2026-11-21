# Edit history

Open the editor overflow menu → Edit history. Each committed editing step lists its
changed sliders and/or categories (curves, HSL, grading, masks, crop, retouch, decorations,
optics). The initial item is the state loaded at the start of the session, not necessarily
the untouched original.

Select a step, then Restore step. Merely selecting a row or cancelling the dialog does not
change the photo. The current step and later redo steps are marked separately. Restoring
moves through the same undo/redo history without recording a new step, so later states
remain available. A subsequent new edit replaces the forward branch, as ordinary undo does.
The restored edit is saved through the existing recipe persistence path; active handles,
lasso and four-point drafts are cleared to prevent editing a now-removed object.

History is session-local and resets when another photo/version opens. It is not a durable
snapshot archive: use the existing Versions feature to preserve alternatives across sessions.
No photo originals are changed and no extra image buffers are allocated for the history list.

Tests cover forward/backward jumps, branch replacement, no-op/invalid jumps, independent
history snapshots, reset, and change summaries including resets and non-slider edits.
