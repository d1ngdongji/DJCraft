# Technology Stack

## Platform & Framework

- **Minecraft Version**: 1.21.1
- **Mod Loader**: NeoForge 21.1.218
- **Java Version**: 21 (required for Minecraft 1.21.1)
- **Build System**: Gradle with NeoForge ModDev plugin

## Key Dependencies

- **GeckoLib** (4.8.3): Animation library for NeoForge
- **Parchment Mappings**: Better parameter names and javadoc for Minecraft code
- **SLF4J**: Logging framework

## Build Commands

```bash
# Build the mod
./gradlew build

# Run client (development)
./gradlew runClient

# Run server (development)
./gradlew runServer

# Run game tests
./gradlew runGameTestServer

# Generate data (resources)
./gradlew runData

# Clean build artifacts
./gradlew clean

# Refresh dependencies (if IDE has issues)
./gradlew --refresh-dependencies
```

## Development Setup

- IDE: IntelliJ IDEA or Eclipse recommended
- The mod uses Mojang's official mapping names (see license in NeoForm repository)
- Configuration cache and parallel builds are enabled by default
- Source and javadoc jars are automatically downloaded for dependencies

## Project Configuration

- **Mod ID**: `djcraft`
- **Base Package**: `otto.djgun.djcraft`
- **Namespace**: All resources use `djcraft` namespace
- **Encoding**: UTF-8 for all Java compilation

## Audio System

- Uses OpenAL for precise audio playback timing
- Custom sound instance management for track synchronization
- Mixin-based integration with Minecraft's sound engine
