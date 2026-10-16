# Pack Organizer details

## Features in full

- Adds a toolbar above the lists: a search box, resolution chips (All / 16x / 32x / 64x / 128x+ / Overlay) and a match counter. Only the Available list is filtered; selected packs never disappear.
- Shows each pack's resolution at the right end of its row, read from the PNG headers of a dozen common PvP textures once per pack and cached in `config/packorganizer/packs.json`. "Overlay" means the pack has none of those textures. Delete the cache file to rescan.
- Logs `packorganizer: rescanned N packs in M ms` to `latest.log`.

## Compatibility and Mixin

Compatible with OptiFine 1.8.9 HD U M5 as a mod jar and with other Mixin 0.7.11 mods. The mixin config is marked
required: if a future OptiFine or Forge build changes a patched method, the game fails at launch
with a Mixin error naming packorganizer instead of silently misbehaving.
