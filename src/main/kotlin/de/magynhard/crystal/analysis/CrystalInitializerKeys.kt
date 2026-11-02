package de.magynhard.crystal.analysis

import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.navigation.CrystalAccessorCoupling
import de.magynhard.crystal.psi.*

/** Advisory completion metadata, never an exhaustive constructor/argument-checking signature. */
internal data class CrystalInitializerKey(val name: String, val typeText: String?, val required: Boolean)

internal object CrystalInitializerKeys {
    // Recognize the complete, fixed forwarding and field-reading contract, not a module name,
    // a **args parameter, or the mere presence of @type.instance_vars. Other macros stay opaque.
    private val template = """
        {% verbatim do %}
        def self.new(**args)
          instance = self.allocate
          instance.initialize(args)
          instance
        end
        private def initialize(args : NamedTuple)
          {% for ivar in @type.instance_vars %}
            {% default_value = ivar.default_value %}
            {% if ivar.type.nilable? %}
              @{{ivar.id}} = args["{{ivar.id}}"]? {% if ivar.has_default_value? %}|| {{ default_value }}{% end %}
            {% else %}
              {% if ivar.has_default_value? %}
                @{{ivar.id}} = args["{{ivar.id}}"]? || {{ default_value }}
              {% else %}
                @{{ivar.id}} = args["{{ivar.id}}"]
              {% end %}
            {% end %}
          {% end %}
        end
        {% end %}
    """.trimIndent()
    private val tokens = Regex("\"(?:\\\\.|[^\"\\\\])*\"|#[^\\n]*|[A-Za-z_][A-Za-z_0-9]*[!?]?|\\S")
    private fun fingerprint(text: String): List<String> = tokens.findAll(text)
        .map { it.value }.filterNot { it.startsWith("#") }.toList()
    private val expected = fingerprint(template)

    fun recognizes(hook: CrystalMacroDefinition): Boolean =
        hook.macroBody?.text?.let(::fingerprint) == expected

    fun mayReplacePositionalInitializer(hook: CrystalMacroDefinition): Boolean {
        val body = fingerprint(hook.macroBody?.text.orEmpty())
        return body.indices.any { index ->
            body[index] == "def" && body.getOrNull(index + 1) == "initialize" &&
                // JSON::Serializable's named-only initializer cannot intercept
                // initialize(args). Other generated initializer bodies stay opaque.
                body.drop(index + 2).take(3) != listOf("(", "*", ",")
        }
    }

    /** Only source-written fields of the exact declaration; generated fields remain unknown. */
    fun fields(declaration: CrystalNamedElement): List<CrystalInitializerKey> = buildList {
        for (call in CrystalAccessorCoupling.findAccessorCallsIn(declaration)) {
            val macro = CrystalAccessorCoupling.accessorMacroName(call) ?: continue
            if (macro.startsWith("class_") || macro.endsWith("!") ||
                CrystalPsiUtils.isInsideMacroControlRegion(call)) continue
            val holder = when (call) {
                is CrystalMethodCallExpression -> call.callArgs ?: call.bareArgumentList
                is CrystalBareMethodCallExpression -> call.callArgs ?: call.bareArgumentList
                else -> null
            }
            for (argument in CrystalPsiCallArguments.argumentElements(holder)) {
                val name = CrystalAccessorCoupling.accessorArgName(argument) ?: continue
                val type = PsiTreeUtil.getChildOfType(argument, CrystalTypeReference::class.java)?.text
                val assignment = PsiTreeUtil.getChildOfType(argument, CrystalAssignment::class.java)
                val default = argument.node.findChildByType(CrystalTypes.ASSIGN) != null || assignment != null
                add(key(name, type, default))
            }
        }
        for (assignment in PsiTreeUtil.findChildrenOfType(declaration, CrystalAssignment::class.java)) {
            if (CrystalPsiUtils.getEnclosingType(assignment) != declaration ||
                PsiTreeUtil.getParentOfType(assignment, CrystalMethodDefinition::class.java) != null ||
                CrystalPsiUtils.isInsideMacroControlRegion(assignment)) continue
            val name = assignment.instanceVarAccess?.text?.removePrefix("@") ?: continue
            val type = PsiTreeUtil.getChildOfType(assignment, CrystalTypeReference::class.java)?.text
            add(key(name, type, assignment.node.findChildByType(CrystalTypes.ASSIGN) != null))
        }
        for (field in PsiTreeUtil.findChildrenOfType(declaration, CrystalPropertyDeclaration::class.java)) {
            if (CrystalPsiUtils.getEnclosingType(field) != declaration ||
                PsiTreeUtil.getParentOfType(field, CrystalMethodDefinition::class.java) != null ||
                CrystalPsiUtils.isInsideMacroControlRegion(field)) continue
            val name = field.instanceVarAccess?.text?.removePrefix("@") ?: continue
            add(key(name, field.typeReference.text, field.expression != null))
        }
    }.groupBy { it.name }.mapNotNull { (_, fields) ->
        val types = fields.mapNotNull { it.typeText }.distinct()
        if (types.size > 1) null else fields.first().copy(typeText = types.singleOrNull(), required = fields.all { it.required })
    }

    private fun key(name: String, type: String?, default: Boolean): CrystalInitializerKey {
        val parsed = type?.let(::parseTypeSet) as? CrystalTypeResolution.Known
        val nilable = parsed?.types?.any { it.name.removePrefix("::") == "Nil" } == true
        return CrystalInitializerKey(name, type, !default && !nilable && type != null)
    }
}
