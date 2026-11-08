# Minecraft Combat Abilities Plugin

sarape is a Paper Minecraft plugin that adds weapon and armor combat abilities, including combo-triggered effects, temporary ultimates, and armor upgrades. It tracks abilities per player and responds to combat, projectile hits, blocking, item use, and sneaking interactions.

## Install

1. Build the project with Java 21 and Maven: `mvn package`.
2. Copy the generated JAR from `target/plugin.jar` into your Paper server’s `plugins` directory.
3. Start or restart the server. The plugin targets Paper API 1.21.11.

## Use

- Fight with swords to build hit combos. Three hits activate two boosted attacks; five hits activate a 15-second damage boost.
- Land axe critical hits and attacks to trigger the axe effects. Sneak and right-click while holding an iron axe to clear stored axe damage.
- Land five trident hits in a row to trigger repeated lightning effects on the target.
- Hit targets with a bow or crossbow to build their projectile abilities.
- Block with a shield to reduce damage and build the shield ultimate. Sneak and right-click with a shield while wearing a full set of diamond armor to upgrade it to netherite.
- Wear a full set of netherite armor to build the armor ultimate; while it is active, consuming a golden apple grants Absorption IV.
- Hit with a mace to charge its ultimate, which applies Wind Burst II to the held mace.

Abilities are driven by in-game actions; the plugin defines no commands or configuration options.

---

More Minecraft plugin projects by EnderDevelopment: https://github.com/EnderDevelopment

Project page: https://dash.enderdevelopment.com/community/556b8931-abec-4031-8695-7a936ad8baf3?utm_source=github&utm_medium=readme&utm_campaign=auto_publish&utm_content=556b8931-abec-4031-8695-7a936ad8baf3
