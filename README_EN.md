<p align="center">
  <img src=".github/assets/djcraft-title.png" alt="DJCraft: Rhythm Combat Mod" width="768">
</p>

<p align="center">
  <a href="README.md">简体中文</a> | <strong>English</strong>
</p>

> Turn music into the rhythm of combat in Minecraft.

DJCraft combines music tracks, beat judgment, and Minecraft combat. Once a track pack starts playing, attack, defend, and move in time with its beat map. Accurate timing keeps your combo alive, builds energy, and improves your combat performance. Mistakes consume your margin for error and may break your combo.

Currently built for **Minecraft 1.21.1 with NeoForge**.

## Core Gameplay

### Fight to the Beat

- Track combat charts appear on the HUD in either falling-note or classic mode.
- Melee attacks, bow and crossbow shots, shield blocks, and selected movement actions are judged against the beat.
- Consecutive hits build your combo, restore energy, and produce stronger HUD, sound, and animation feedback.
- Different beat results can affect damage; misses consume tolerance or break your combo.

### Collect, Burn, and Play Discs

- Install track packs to add custom music and combat charts.
- Use a blank disc and the DJ Crafting Table to burn a track onto a physical music disc.
- The Portable Jukebox stores 54 discs and supports sequential play, repeat-one, and shuffle modes.
- Discs record their all-time highest combo and total play time.
- A disc gains a golden appearance once its highest combo reaches 80% of the chart's beat count.

### Rhythm Weapons and Combat Actions

DJCraft brings vanilla melee weapons, bows, crossbows, tridents, maces, and shields into its rhythm system while adding several special weapons:

- **Laser Crossbow**: Fires a long-range beam that can pierce multiple targets.
- **Magic Crossbow**: Deals higher single-target damage to the first enemy hit.
- **Assault Crossbow**: Fires a low-energy single-target beam and can consume tipped arrows to transfer potion effects.
- **Explosive Bow**: Charges automatically and creates a terrain-safe area explosion on impact.
- **Shield Parry**: Raise your shield at the right moment to negate damage and earn combo and energy rewards.

The special beam weapons are currently obtained primarily through Creative Mode.

### High-Mobility DJ Combat

During an active DJ session, players can use:

- Eight-direction dashes;
- Multiple mid-air jumps;
- An aerial ground slam with area knockback;
- An enhanced **Flowery** dash that crashes through enemies with a rainbow trail.

Passive items such as DJ Fumo, Band of Energy, and Note in a Bottle can increase tolerance, maximum energy, or the number of available air jumps.

### Multiplayer DJ Groups

- Invite online players into a shared listening group.
- Synchronize one playlist across the group, with support for joining mid-session and transferring ownership.
- Each player keeps independent combo, energy, tolerance, and combat state.
- Missing redistributable track packs can be downloaded from the server and verified automatically.

### Cyber Grind

Enter **Cyber Grind** from the DJ Crafting Table, select a playlist from your Portable Jukebox, and fight through advancing waves of enemies to the music.

- Supports solo challenges and group preparation through DJ networking.
- Battles take place in a separate midnight arena, with waves scaled to the number of surviving players.
- Enemies drop no loot or experience and cannot damage the arena terrain.
- Defeated players safely return to their pre-match position while keeping their inventory and experience.
- The HUD shows the countdown, current wave, and enemy pressure, and tracks separate personal and team best scores.

## Quick Start

1. Install DJCraft, NeoForge, and the mod's required dependencies.
2. Place compatible track packs in the game's `djcraft/trackpacks/` directory.
3. Craft a Blank Disc, DJ Crafting Table, and Portable Jukebox.
4. Hold a Blank Disc and right-click the DJ Crafting Table, then select a track to burn it.
5. Insert the disc into the Portable Jukebox, then right-click normally or press the default `G` key to open the player.
6. Start the music, watch the chart, and fight to the beat.

The default `V` key activates the DJ dash. Other actions use the normal controls for their corresponding items and movements.

## Multiplayer Notes

Multiplayer servers verify that the client and server have matching track-pack content. If a pack is missing or has a different version, install or download the correct version before burning discs, playing tracks, or joining a group. When the server provides a redistributable pack, it can be handled directly from the mod's download screen.

## Project Status

DJCraft is under active development. Music synchronization, first-person animations, multiplayer groups, and Cyber Grind are sensitive to the real game environment. When reporting an issue, include the track pack in use, whether it occurred in singleplayer or multiplayer, and clear reproduction steps.

## More Documentation

The detailed technical and gameplay documentation is currently maintained in Chinese:

- [Gameplay mechanics](docs/gameplay-mechanics.md)
- [Track-pack guide](docs/trackpack-feature.md)
- [Documentation index](docs/README.md)

## License

All rights reserved. Do not redistribute this mod or its assets without permission.

## Development References

- [NeoForge documentation](https://docs.neoforged.net/)
- [NeoForge API documentation](https://docs.neoforged.net/docs/gettingstarted/)
- [NeoForge Gradle toolchain documentation](https://docs.neoforged.net/toolchain/docs/)
- [GeckoLib documentation](https://github.com/bernie-g/geckolib/wiki/Getting-Started)
- [Minecraft Wiki](https://minecraft.wiki/)
- [Player Animation Library documentation](https://docs.zigythebird.com/pal/intro)
