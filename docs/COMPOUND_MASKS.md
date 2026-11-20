# Compound local masks

In Local → Selection, select a mask and choose **Add selection** or **Subtract selection**.
Choose Linear, Radial, Brush (including lasso), Subject/object, or Sky AI. Each added
component has a chip: select it to edit its handles, feather, strokes or object taps;
the adjacent delete button removes just that component. The original selection remains
editable as “Base selection”. “New mask” creates a separate adjustment instead.

Example: create Sky AI, subtract Subject, then subtract a brush stroke over a building.
The AI models are unchanged: sky remains experimental and may require manual refinement.

The result combines selections in list order: addition takes the maximum coverage;
subtraction takes min(current coverage, 1 − component coverage). A later addition can
restore an earlier subtraction. Invert and luminance/color range affect the final mask,
as do all its local adjustments and background cutout. Components do not have separate
color/luminance ranges or exposure controls. Up to 15 additional selections fit in one
mask; the existing eight adjustment-mask limit is unchanged.

Preview and export use the same source-coordinate 1024-long-edge grid for compound masks,
matching the existing brush/AI mask grid. Geometry-only masks without components retain
the analytic shader path. Raster results are cached across adjustment-only edits; changing
components or AI results invalidates them. No model inference runs while dragging handles.
JSON stores an optional flat `parts` list; an absent list preserves existing recipes.
New compound recipes require an app version that supports components.

Validation: unit tests cover addition, subtraction/order, inversion/range routing,
independent AI bases and invalidation, brush undo, serialization, component editing and
preview/export grid agreement. Android lint and APK build run on the computer. No
physical-device interaction or validation is part of this change.
