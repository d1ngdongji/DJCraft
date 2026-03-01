# DJCraft

DJCraft is a Minecraft mod that integrates rhythm-based combat mechanics into the game. Players can engage in combat synchronized to music tracks, with timing-based damage multipliers and beat-matching mechanics.

## Core Features

- Rhythm-based combat system with beat detection and timing windows
- Track pack system for loading custom music with beat maps
- DJ session management for multiplayer synchronization
- Client-server architecture with network packet synchronization
- Custom crosshair rendering and debug HUD for timing feedback
- Configurable item cooldowns and warmup periods based on beat timing
- OpenAL-based audio playback with precise timing control

## Key Concepts

- **DJ Session**: Active music playback session with beat timeline tracking
- **Beat Events**: Timed events on the music timeline that define combat windows
- **Hit Result**: Judgment of player attack timing (perfect, good, miss)
- **Track Pack**: Bundle of music files with associated metadata and beat definitions
- **Combat Line**: Sequence of beat events used for combat timing evaluation
