# Pack Organizer details

## Features in full

- **Toolbar** above the lists: a search box, resolution chips (All / 16x / 32x / 64x / 128x+ / Overlay) and a match counter. Only the Available list is filtered; selected packs never disappear.
- **Resolution labels** at the right end of each row, read from the pack name (for example `[16x]`) and from the PNG headers of common PvP textures. "Overlay" means the pack has none of those textures. Each pack is read once in the background and cached in `config/packorganizer/packs.json`; delete that file to rescan.
- **Pack folders.** Packs in subfolders of `resourcepacks` show up as collapsible folders, at any depth. A folder row shows how many packs it holds and how many are turned on, and a turned-on pack shows its folder under its name. Search looks inside folders too. While a search, chip or color is active, the list is flat with no folder rows.
- **Hover previews.** Hovering a pack row shows its item textures (sword, pearls, blocks and so on) in place of its description.
- **Previews list mode.** The Previews toolbar button makes every row show its item strip all the time, so a folder reads as a wall of previews. Name and description stay the default, and the choice is remembered.
- **Player preview.** On a wide enough window (GUI width 680 or more), a 3D player model with your own skin stands beside the lists, wearing the hovered pack's armor and holding its items. Drag to turn it. Pick the armor set and held item (sword, bow, ender pearl, golden apple, splash potion, fishing rod) under the model. Click the model to hit it, and use the held item to draw the bow, throw a pearl or potion, or eat an apple, with the pack's own sounds. The backdrop uses the pack's sky textures and can cycle from day to night.
- **Color wheel.** Click a point on the wheel to sort packs by how close their diamond sword and diamond armor are to that color, closest first. It never hides packs; the note under the wheel counts the close ones, and Clear goes back to the normal order. On a narrow window or a large GUI scale the wheel moves into a Color button popup.
- **Fast scrolling.** Previews load in the background, newest rows first, and textures are decoded no larger than they are drawn, so scrolling a long list stays smooth.
- **Transparent background** (off by default): shows the world behind the screen instead of dirt when the screen is opened in game.

![The list in Previews mode](images/previews.png)

![Sorting by color](images/color-sort.png)

![A small window, with the color wheel in a popup](images/small-window.png)

## Settings

Open Mods > Pack Organizer > Config. Each feature can be turned off: pack previews, transparent background, pack folders, color filter, resolution labels, remember open folders, player preview, pack sky backdrop, sky day cycle, show cape, hit tint, and preview sounds. Settings are saved in `config/packorganizer/settings.json`.

![The settings screen](images/settings.png)

## Logs

Logs `packorganizer: found N nested packs in M ms` to `latest.log` each time the game lists packs.

## Compatibility and Mixin

Compatible with OptiFine 1.8.9 HD U M5 as a mod jar and with other Mixin 0.7.11 mods. The mixin config is marked
required: if a future OptiFine or Forge build changes a patched method, the game fails at launch
with a Mixin error naming packorganizer instead of silently misbehaving.
