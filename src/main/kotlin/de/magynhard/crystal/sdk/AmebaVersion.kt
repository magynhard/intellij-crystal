package de.magynhard.crystal.sdk

/**
 * Minimum supported Ameba version for the IDE integration.
 *
 * 1.7.0 rebuilt the overlap-relevant rules on liveness analysis
 * (`Lint/UselessAssign` without the old `ExcludeTypeDeclarations` crutch),
 * improved issue locations, stabilized JSON output, and added ECR support.
 * Older binaries resolve to nothing so the built-in inspections stay the
 * fallback instead of doubling weaker diagnostics.
 */
object AmebaVersion {

    const val MINIMUM = "1.7.0"

    private val versionPattern = Regex("""\d+(?:\.\d+)*(?:-[0-9A-Za-z.]+)?""")

    /**
     * Extracts the version number from `ameba --version` output
     * (`"ameba 1.7.0 (abc1234)"` → `"1.7.0"`). Null when no version-like
     * sequence is present (broken or foreign binary).
     */
    fun parse(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return versionPattern.find(raw)?.value
    }

    /**
     * True when [raw] (`ameba --version` output) parses and meets the
     * minimum. Pre-releases sort below their release (`1.7.0-dev` < `1.7.0`)
     * and are rejected; unparsable output fails closed.
     */
    fun meetsMinimum(raw: String?): Boolean {
        val version = parse(raw) ?: return false
        return CrystalVersionRequirement.satisfies(">= $MINIMUM", version) == true
    }

    /**
     * True when a manifest `version:` requirement permits the minimum, false
     * when it explicitly excludes it (`1.6.0`, `~> 1.6.0`), null when it
     * cannot be judged (absent requirement, `branch`/`commit`/`tag` pins,
     * unparsable shapes). Callers warn only on an explicit false.
     */
    fun pinAllowsMinimum(requirement: String?): Boolean? {
        if (requirement.isNullOrBlank()) return null
        return CrystalVersionRequirement.satisfies(requirement, MINIMUM)
    }

    /**
     * User-facing one-liner for a [AmebaBinary.VersionProblem], shared by
     * balloons, banners, and the settings warning text.
     */
    fun warningText(problem: AmebaBinary.VersionProblem): String {
        return when (problem) {
            is AmebaBinary.VersionProblem.TooOld ->
                "Ameba ${problem.version} is older than the required $MINIMUM — linting stays disabled."
            is AmebaBinary.VersionProblem.Undetermined ->
                "Could not determine the Ameba version at ${problem.path} — linting stays disabled."
        }
    }

    /**
     * Warning text for a raw `--version` output, or null when fine (or no
     * input). Pure function of the string — the settings label delegate.
     */
    fun warningForRawVersion(path: String, raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val problem = AmebaBinary.problemOf(path, raw) ?: return null
        return warningText(problem)
    }
}
