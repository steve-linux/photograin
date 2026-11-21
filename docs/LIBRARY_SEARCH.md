# Search and organize recent photos

The home screen can search filenames and version names, including the localized automatic
version labels. Matching ignores case and accents; separate words must all occur somewhere
in the file/version name. Existing minimum-star filtering combines with the search and the
new All / Edited / Unedited version filter. Edited means the saved recipe differs from an
untouched EditState; it is evaluated per version, while stars belong to the source photo.

Sort by recently opened (the existing recents order), filename A–Z, or highest rating.
Equal keys retain recent order. Filenames appear below grid tiles; the search and filters
also apply to the existing original/edited comparison view. Results count versions. Reset
filters clears the query, star threshold, edit-status filter and ordering together.

This is a metadata-only view of existing recents (currently at most 24 source photos,
plus their versions), not a scan/index of the entire gallery. Names and saved recipes load
on the IO dispatcher once per home visit. Search does not decode images or request gallery
access. A missing provider name falls back to the URI segment, and existing thumbnail
handling remains in place. Filter state survives configuration changes; it is not a new
persistent library preference or album system.

Tests cover case/accent/multiword matching, automatic labels, combined filters, stable
ordering, empty results, Turkish system locale, and per-version edit state.
