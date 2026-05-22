package de.magynhard.crystal.run

enum class CrystalCommand(val command: String, val displayName: String) {
    RUN("run", "Run"),
    BUILD("build", "Build"),
    SPEC("spec", "Spec"),
}
