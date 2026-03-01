# Project Structure

## Source Organization

Base package: `otto.djgun.djcraft`

```
src/main/java/otto/djgun/djcraft/
├── client/              # Client-side only code
│   ├── ClientTrackRegistry.java    # Manages track validation on client
│   ├── DJClientEvents.java         # Client event handlers
│   └── DJDebugHud.java             # Debug overlay rendering
├── combat/              # Combat system
│   ├── client/          # Client-side combat handlers
│   ├── BeatJudgeUtil.java          # Timing judgment logic
│   ├── DJCombatHandler.java        # Server-side combat events
│   ├── DJCombatServerHandler.java  # Attack packet processing
│   ├── DJItemCooldownManager.java  # Item cooldown configuration
│   └── HitResult.java              # Combat result data
├── command/             # Minecraft commands
│   └── DJCommands.java             # /dj command implementation
├── data/                # Data structures
│   ├── BeatDefinition.java         # Beat timing data
│   ├── BeatEvent.java              # Timeline event
│   ├── Timeline.java               # Beat timeline
│   ├── TrackMeta.java              # Track metadata
│   ├── TrackPack.java              # Track bundle
│   └── TrackSettings.java          # Track configuration
├── event/               # Custom events
│   └── OnBeatEvent.java            # Beat trigger event
├── hud/                 # HUD rendering
│   └── DJCrosshairRenderer.java    # Custom crosshair
├── loader/              # Track loading system
│   ├── TrackPackLoader.java        # Loads track packs from disk
│   └── TrackPackManager.java       # Manages loaded tracks
├── mixin/               # Minecraft mixins
│   ├── ChannelMixin.java           # Audio channel hooks
│   └── SoundEngineMixin.java       # Sound engine hooks
├── network/             # Network packets
│   ├── packet/          # Packet definitions
│   └── DJNetwork.java              # Network registration
├── session/             # Session management
│   ├── DJModeManager.java          # Server session manager
│   ├── DJModeManagerClient.java    # Client session manager
│   ├── DJSession.java              # Server session state
│   └── DJSessionClient.java        # Client session state
├── sound/               # Audio system
│   ├── DJSoundInstance.java        # Custom sound instance
│   ├── DJSoundManager.java         # Sound playback manager
│   ├── OpenALHelper.java           # OpenAL utilities
│   ├── TrackPackRepositorySource.java  # Resource pack integration
│   └── TrackPackResources.java     # Resource pack provider
├── util/                # Utilities
│   └── BeatGridUtil.java           # Beat grid calculations
├── Config.java          # Mod configuration
├── DJCraft.java         # Main mod class (server/common)
└── DJCraftClient.java   # Client initialization
```

## Resources

```
src/main/resources/
├── assets/djcraft/
│   ├── lang/            # Translations
│   └── textures/        # Textures (crosshair, etc.)
└── djcraft.mixins.json  # Mixin configuration
```

## Architecture Patterns

### Client-Server Split
- Client-only code in `client/` packages
- Server logic in main packages
- Network packets for synchronization

### Event-Driven
- NeoForge event bus for game events
- Custom `OnBeatEvent` for beat triggers
- Mixin hooks for low-level integration

### Singleton Managers
- `TrackPackManager`: Global track registry
- `DJModeManager`: Server session management
- `DJModeManagerClient`: Client session management
- `ClientTrackRegistry`: Client track validation

### Session Pattern
- `DJSession` (server) and `DJSessionClient` (client) track active playback
- Sessions store timeline, current position, and beat state
- Tick-based updates for synchronization

## Key Conventions

- Static logger: `DJCraft.LOGGER`
- Mod ID constant: `DJCraft.MODID`
- Event handlers use `@SubscribeEvent` annotation
- Network packets implement custom payload interfaces
- Config uses NeoForge's `ModConfigSpec`

## HUD & Animation

### Custom Crosshair
- Replaces the vanilla crosshair when DJ mode is active via `RenderGuiLayerEvent.Pre` cancellation.
- Shows dynamic expanding and retracting lines upon weapon attacks, providing visual feedback synchronously with beats (`hud/DJCrosshairRenderer.java`).

### Weapon Idle Animation
- Applies a default idle animation for weapons in first-person view, bouncing in alignment with every exact beat in the track.
- Uses `RenderHandEvent` to smoothly interpolate and offset the player's arm/item `PoseStack` with a cosine wave calculated relative to the nearest beat points (`client/DJClientEvents.java`).
