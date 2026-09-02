package de.magynhard.crystal.analysis

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.CrystalFile
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService
import java.util.IdentityHashMap

/**
 * Tracks concrete local bindings and the assignment definitions that may reach each read.
 * The analysis is deliberately conservative around blocks and macros: unknown execution may
 * suppress a warning, but can never manufacture a dead assignment.
 */
class CrystalLocalUsageAnalyzer(private val root: PsiElement) {
    data class UnusedAssignment(
        val name: String,
        val identifier: PsiElement,
        val hasOtherAssignments: Boolean
    )

    private data class Symbol(
        val id: Int,
        val name: String,
        val frame: Frame,
        val firstOffset: Int
    )

    private data class Definition(
        val id: Int,
        val symbol: Symbol,
        val identifier: PsiElement,
        val reportable: Boolean
    )

    private data class BindingSite(
        val offset: Int,
        val assignment: CrystalAssignment? = null,
        val forStatement: CrystalForStatement? = null,
        val groupedExpression: CrystalGroupedExpression? = null
    )

    private class Frame(
        val owner: PsiElement,
        val parent: Frame?
    ) {
        val symbols = linkedMapOf<String, Symbol>()

        fun lookup(name: String, offset: Int): Symbol? {
            val local = symbols[name]
            if (local != null && local.firstOffset <= offset) return local
            return parent?.lookup(name, offset)
        }
    }

    private data class State(val reaching: Map<Int, Set<Int>> = emptyMap()) {
        fun definitions(symbol: Symbol): Set<Int> = reaching[symbol.id].orEmpty()

        fun write(symbol: Symbol, definition: Definition): State =
            State(reaching + (symbol.id to setOf(definition.id)))

        fun without(symbolIds: Set<Int>): State =
            if (symbolIds.isEmpty()) this else State(reaching.filterKeys { it !in symbolIds })

        companion object {
            fun merge(states: Collection<State>): State {
                if (states.isEmpty()) return State()
                val merged = mutableMapOf<Int, MutableSet<Int>>()
                for (state in states) {
                    for ((symbol, definitions) in state.reaching) {
                        merged.getOrPut(symbol) { linkedSetOf() }.addAll(definitions)
                    }
                }
                return State(merged.mapValues { it.value.toSet() })
            }
        }
    }

    private data class Flow(
        val normal: State?,
        val breaks: List<State> = emptyList(),
        val continues: List<State> = emptyList(),
        val returns: List<State> = emptyList()
    )

    private val frames = IdentityHashMap<PsiElement, Frame>()
    private val assignmentSymbols = IdentityHashMap<CrystalAssignment, Symbol>()
    private val parameterSymbols = IdentityHashMap<PsiElement, Symbol>()
    private val definitions = mutableListOf<Definition>()
    private val definitionsByAssignment = IdentityHashMap<CrystalAssignment, Definition>()
    private val forSymbols = IdentityHashMap<CrystalForStatement, Symbol>()
    private val definitionsByFor = IdentityHashMap<CrystalForStatement, Definition>()
    private val groupedSymbols = IdentityHashMap<CrystalGroupedExpression, Symbol>()
    private val definitionsByGrouped = IdentityHashMap<CrystalGroupedExpression, Definition>()
    private val usedDefinitions = linkedSetOf<Int>()
    private val reachedDefinitions = linkedSetOf<Int>()
    private val escapedReads = mutableListOf<Pair<Int, Int>>()
    private val visibleMacroCalls = mutableMapOf<String, Boolean>()
    private var nextSymbolId = 0

    fun analyze(): List<UnusedAssignment> {
        val rootFrame = prepareFrame(root, null)
        flowChildren(root, rootFrame, State())

        for ((symbolId, blockEnd) in escapedReads) {
            definitions.asSequence()
                .filter { it.symbol.id == symbolId && it.identifier.textOffset >= blockEnd }
                .forEach { usedDefinitions.add(it.id) }
        }

        val assignmentCounts = definitions.groupingBy { it.symbol.id }.eachCount()
        return definitions.asSequence()
            .filter { it.reportable && it.id in reachedDefinitions && it.id !in usedDefinitions }
            .map {
                UnusedAssignment(
                    it.symbol.name,
                    it.identifier,
                    assignmentCounts.getValue(it.symbol.id) > 1
                )
            }
            .toList()
    }

    private fun prepareFrame(owner: PsiElement, parent: Frame?): Frame {
        val frame = Frame(owner, parent)
        frames[owner] = frame
        introduceParameters(owner, frame)

        val assignments = mutableListOf<CrystalAssignment>()
        collectFrameAssignments(owner, assignments, isRoot = true)
        val forStatements = mutableListOf<CrystalForStatement>()
        collectFrameForStatements(owner, forStatements, isRoot = true)
        val groupedAssignments = mutableListOf<CrystalGroupedExpression>()
        collectFrameGroupedAssignments(owner, groupedAssignments, isRoot = true)
        val bindingSites = assignments.map { BindingSite(it.textOffset, assignment = it) } +
            forStatements.mapNotNull { statement ->
                forIdentifier(statement)?.let { BindingSite(it.textOffset, forStatement = statement) }
            } + groupedAssignments.mapNotNull { grouped ->
                groupedAssignmentIdentifier(grouped)?.let {
                    BindingSite(it.textOffset, groupedExpression = grouped)
                }
            }
        for (site in bindingSites.sortedBy { it.offset }) {
            val assignment = site.assignment
            val identifier = assignment?.let(::localAssignmentIdentifier)
                ?: site.forStatement?.let(::forIdentifier)
                ?: site.groupedExpression?.let(::groupedAssignmentIdentifier)
                ?: continue
            val name = identifier.text
            val symbol = frame.symbols[name]?.takeIf { it.firstOffset <= identifier.textOffset }
                ?: parent?.lookup(name, owner.textOffset)
                ?: createSymbol(frame, name, identifier.textOffset)
            if (assignment != null) assignmentSymbols[assignment] = symbol
            val definition = Definition(
                definitions.size,
                symbol,
                identifier,
                assignment?.node?.findChildByType(CrystalTypes.ASSIGN) != null ||
                    site.groupedExpression?.node?.findChildByType(CrystalTypes.ASSIGN) != null
            )
            definitions.add(definition)
            if (assignment != null) {
                definitionsByAssignment[assignment] = definition
            } else if (site.forStatement != null) {
                forSymbols[site.forStatement] = symbol
                definitionsByFor[site.forStatement] = definition
            } else if (site.groupedExpression != null) {
                groupedSymbols[site.groupedExpression] = symbol
                definitionsByGrouped[site.groupedExpression] = definition
            }
        }

        prepareNestedFrames(owner, frame, isRoot = true)
        return frame
    }

    private fun introduceParameters(owner: PsiElement, frame: Frame) {
        val parameters = when (owner) {
            is CrystalMethodDefinition -> owner.parameterList?.parameterList.orEmpty()
            is CrystalMacroDefinition -> owner.parameterList?.parameterList.orEmpty()
            is CrystalBlock -> owner.parameterList?.parameterList.orEmpty()
            else -> emptyList()
        }
        for (parameter in parameters) {
            val identifier = (parameter as? PsiNameIdentifierOwner)?.nameIdentifier ?: continue
            val name = (parameter as? PsiNameIdentifierOwner)?.name ?: identifier.text
            val symbol = createSymbol(frame, name, Int.MIN_VALUE)
            parameterSymbols[parameter] = symbol
            parameterSymbols[identifier] = symbol
        }
        if (owner is CrystalRescueClause) {
            val identifier = owner.node.findChildByType(CrystalTypes.IDENTIFIER)?.psi ?: return
            parameterSymbols[identifier] = createSymbol(frame, identifier.text, Int.MIN_VALUE)
        }
    }

    private fun createSymbol(frame: Frame, name: String, firstOffset: Int): Symbol {
        val symbol = Symbol(nextSymbolId++, name, frame, firstOffset)
        frame.symbols[name] = symbol
        return symbol
    }

    private fun collectFrameAssignments(
        element: PsiElement,
        result: MutableList<CrystalAssignment>,
        isRoot: Boolean = false
    ) {
        if (!isRoot && isHardBoundary(element)) return
        if (!isRoot && element is CrystalBlock) return
        if (!isRoot && element is CrystalRescueClause) return
        if (element is CrystalAssignment && localAssignmentIdentifier(element) != null) {
            result.add(element)
        }
        for (child in element.children) collectFrameAssignments(child, result)
    }

    private fun collectFrameForStatements(
        element: PsiElement,
        result: MutableList<CrystalForStatement>,
        isRoot: Boolean = false
    ) {
        if (!isRoot && isHardBoundary(element)) return
        if (!isRoot && element is CrystalBlock) return
        if (!isRoot && element is CrystalRescueClause) return
        if (element is CrystalForStatement) result.add(element)
        for (child in element.children) collectFrameForStatements(child, result)
    }

    private fun collectFrameGroupedAssignments(
        element: PsiElement,
        result: MutableList<CrystalGroupedExpression>,
        isRoot: Boolean = false
    ) {
        if (!isRoot && isHardBoundary(element)) return
        if (!isRoot && element is CrystalBlock) return
        if (!isRoot && element is CrystalRescueClause) return
        if (element is CrystalGroupedExpression && groupedAssignmentIdentifier(element) != null) result.add(element)
        for (child in element.children) collectFrameGroupedAssignments(child, result)
    }

    private fun prepareNestedFrames(element: PsiElement, frame: Frame, isRoot: Boolean = false) {
        if (!isRoot && isHardBoundary(element)) return
        if (!isRoot && (element is CrystalBlock || element is CrystalRescueClause)) {
            prepareFrame(element, frame)
            return
        }
        for (child in element.children) prepareNestedFrames(child, frame)
    }

    private fun flowElement(element: PsiElement, frame: Frame, incoming: State): Flow {
        if (element !== root && isHardBoundary(element)) return Flow(incoming)
        return when (element) {
            is CrystalAssignment -> flowAssignment(element, frame, incoming)
            is CrystalGroupedExpression -> flowGroupedAssignment(element, frame, incoming)
            is CrystalMethodBody -> flowProtected(
                element.statementList,
                element.rescueClauseList,
                element.elseClause,
                element.ensureClause,
                frame,
                incoming
            )
            is CrystalIfStatement -> flowIf(element, frame, incoming)
            is CrystalUnlessStatement -> flowUnless(element, frame, incoming)
            is CrystalWhileStatement -> flowLoop(element.condition, element.statementList, frame, incoming)
            is CrystalUntilStatement -> flowLoop(element.condition, element.statementList, frame, incoming)
            is CrystalForStatement -> flowFor(element, frame, incoming)
            is CrystalCaseStatement -> flowCase(element, frame, incoming)
            is CrystalSelectStatement -> flowSelect(element, frame, incoming)
            is CrystalBeginStatement -> flowBegin(element, frame, incoming)
            is CrystalBlock -> flowBlock(element, incoming)
            is CrystalReturnStatement -> flowReturn(element, frame, incoming)
            is CrystalBreakStatement -> flowAbrupt(
                element.assignment ?: element.expression,
                element.postfixModifier,
                frame,
                incoming,
                isBreak = true
            )
            is CrystalNextStatement -> flowAbrupt(
                element.assignment ?: element.expression,
                element.postfixModifier,
                frame,
                incoming,
                isBreak = false
            )
            is CrystalExpression -> flowExpression(element, frame, incoming)
            else -> flowRegularElement(element, frame, incoming)
        }
    }

    private fun flowRegularElement(element: PsiElement, frame: Frame, incoming: State): Flow {
        var state = incoming
        if (element.node.elementType == CrystalTypes.IDENTIFIER && isLocalReadIdentifier(element)) {
            frame.lookup(element.text, element.textOffset)?.let { read(it, state, frame) }
        }
        when (element) {
            is CrystalVariableReference -> {
                frame.lookup(localReferenceName(element) ?: "", element.textOffset)?.let {
                    read(it, state, frame)
                }
            }
            is CrystalMethodCallExpression,
            is CrystalBareMethodCallExpression -> {
                val reference = element.reference as? CrystalReference
                when (val target = reference?.resolveLocalDeclaration()) {
                    is CrystalAssignment -> assignmentSymbols[target]?.let { read(it, state, frame) }
                    is PsiNameIdentifierOwner -> parameterSymbols[target]?.let { read(it, state, frame) }
                    is PsiElement -> parameterSymbols[target]?.let { read(it, state, frame) }
                }
                if (isVisibleMacroCall(element)) {
                    markVisibleDefinitionsUsed(state)
                }
            }
        }
        return flowChildren(element, frame, state)
    }

    private fun flowExpression(expression: CrystalExpression, frame: Frame, incoming: State): Flow {
        val children = expression.node.getChildren(null).map { it.psi }
        val question = children.indexOfFirst { it.node.elementType == CrystalTypes.QUESTION }
        val colon = children.indexOfFirst { it.node.elementType == CrystalTypes.COLON }
        if (question >= 0 && colon > question) {
            val condition = flowElements(children.subList(0, question), frame, incoming)
            val branchInput = condition.normal ?: return condition
            val whenTrue = flowElements(children.subList(question + 1, colon), frame, branchInput)
            val whenFalse = flowElements(children.subList(colon + 1, children.size), frame, branchInput)
            return mergeFlows(listOf(whenTrue, whenFalse), condition)
        }
        return flowLogicalExpression(expression, children, frame, incoming)
    }

    private fun flowLogicalExpression(
        expression: CrystalExpression,
        children: List<PsiElement>,
        frame: Frame,
        incoming: State
    ): Flow {
        val segments = mutableListOf<MutableList<PsiElement>>(mutableListOf())
        for (child in children) {
            if (child.node.elementType == CrystalTypes.OR_OR || child.node.elementType == CrystalTypes.AND_AND) {
                segments.add(mutableListOf())
            } else {
                segments.last().add(child)
            }
        }
        if (segments.size == 1) {
            val firstType = segments.first().firstOrNull { !it.text.isBlank() }?.node?.elementType
            return if (firstType == CrystalTypes.RETURN || firstType == CrystalTypes.BREAK ||
                firstType == CrystalTypes.NEXT) {
                flowSegment(segments.first(), frame, incoming)
            } else {
                flowRegularElement(expression, frame, incoming)
            }
        }

        var flow = flowSegment(segments.first(), frame, incoming)
        for (segment in segments.drop(1)) {
            val skipState = flow.normal ?: break
            val rhs = flowSegment(segment, frame, skipState)
            flow = Flow(
                State.merge(listOfNotNull(skipState, rhs.normal)),
                flow.breaks + rhs.breaks,
                flow.continues + rhs.continues,
                flow.returns + rhs.returns
            )
        }
        return flow
    }

    private fun flowSegment(elements: List<PsiElement>, frame: Frame, incoming: State): Flow {
        val significant = elements.filterNot { it.text.isBlank() }
        val abruptType = significant.firstOrNull()?.node?.elementType
        val kind = when (abruptType) {
            CrystalTypes.RETURN -> AbruptKind.RETURN
            CrystalTypes.BREAK -> AbruptKind.BREAK
            CrystalTypes.NEXT -> AbruptKind.NEXT
            else -> null
        }
        if (kind != null) {
            val value = significant.drop(1).singleOrNull()
            return flowAbruptValue(value, null, frame, incoming, kind)
        }
        return flowElements(elements, frame, incoming)
    }

    private fun flowElements(elements: List<PsiElement>, frame: Frame, incoming: State): Flow {
        var normal: State? = incoming
        val breaks = mutableListOf<State>()
        val continues = mutableListOf<State>()
        val returns = mutableListOf<State>()
        for (element in elements) {
            val state = normal ?: break
            val flow = flowElement(element, frames[element] ?: frame, state)
            normal = flow.normal
            breaks.addAll(flow.breaks)
            continues.addAll(flow.continues)
            returns.addAll(flow.returns)
        }
        return Flow(normal, breaks, continues, returns)
    }

    private fun flowChildren(element: PsiElement, frame: Frame, incoming: State): Flow {
        var normal: State? = incoming
        val breaks = mutableListOf<State>()
        val continues = mutableListOf<State>()
        val returns = mutableListOf<State>()
        for (childNode in element.node.getChildren(null)) {
            val child = childNode.psi
            val state = normal ?: break
            val childFrame = frames[child] ?: frame
            val flow = flowElement(child, childFrame, state)
            normal = flow.normal
            breaks.addAll(flow.breaks)
            continues.addAll(flow.continues)
            returns.addAll(flow.returns)
        }
        return Flow(normal, breaks, continues, returns)
    }

    private fun flowAssignment(assignment: CrystalAssignment, frame: Frame, incoming: State): Flow {
        val postfix = assignment.postfixModifier
        val conditionFlow = postfix?.let { flowElement(it.expression, frame, incoming) }
        val base = conditionFlow?.normal ?: incoming
        val assignmentFlow = flowAssignmentCore(assignment, frame, base)
        val resultNormal = if (postfix != null) {
            State.merge(listOfNotNull(base, assignmentFlow.normal))
        } else {
            assignmentFlow.normal
        }
        return Flow(
            resultNormal,
            conditionFlow?.breaks.orEmpty() + assignmentFlow.breaks,
            conditionFlow?.continues.orEmpty() + assignmentFlow.continues,
            conditionFlow?.returns.orEmpty() + assignmentFlow.returns
        )
    }

    private fun flowAssignmentCore(assignment: CrystalAssignment, frame: Frame, incoming: State): Flow {
        val symbol = assignmentSymbols[assignment] ?: return flowChildren(assignment, frame, incoming)
        var rhsState = incoming
        val compound = assignment.node.findChildByType(CrystalTypes.ASSIGN) == null
        if (compound) read(symbol, rhsState, frame)
        val rhs = assignment.assignment ?: assignment.expression
        val rhsFlow = rhs?.let { flowElement(it, frame, rhsState) } ?: Flow(rhsState)
        val normal = rhsFlow.normal
        val definition = definitionsByAssignment[assignment]
        val assigned = if (normal != null && definition != null) {
            reachedDefinitions.add(definition.id)
            normal.write(symbol, definition)
        } else {
            null
        }
        return Flow(
            assigned,
            rhsFlow.breaks,
            rhsFlow.continues,
            rhsFlow.returns
        )
    }

    private fun flowGroupedAssignment(grouped: CrystalGroupedExpression, frame: Frame, incoming: State): Flow {
        val symbol = groupedSymbols[grouped] ?: return flowRegularElement(grouped, frame, incoming)
        val expressions = grouped.expressionList
        val rhs = expressions.getOrNull(1) ?: return flowRegularElement(grouped, frame, incoming)
        if (grouped.node.findChildByType(CrystalTypes.ASSIGN) == null) read(symbol, incoming, frame)
        val rhsFlow = flowElement(rhs, frame, incoming)
        val definition = definitionsByGrouped[grouped]
        val normal = rhsFlow.normal
        val assigned = if (normal != null && definition != null) {
            reachedDefinitions.add(definition.id)
            normal.write(symbol, definition)
        } else {
            null
        }
        return Flow(assigned, rhsFlow.breaks, rhsFlow.continues, rhsFlow.returns)
    }

    private fun flowIf(statement: CrystalIfStatement, frame: Frame, incoming: State): Flow {
        val condition = statement.condition?.let { flowElement(it, frame, incoming) } ?: Flow(incoming)
        val branchInput = condition.normal ?: return condition
        val branches = mutableListOf<Flow>()
        val guardAbrupt = mutableListOf(condition.copy(normal = null))
        branches.add(flowStatementList(statement.statementList, frame, branchInput))
        var remaining = branchInput
        for (elsif in statement.elsifClauseList) {
            val elsifCondition = flowElement(elsif.condition, frame, remaining)
            guardAbrupt.add(elsifCondition.copy(normal = null))
            val elsifInput = elsifCondition.normal ?: break
            branches.add(flowStatementList(elsif.statementList, frame, elsifInput))
            remaining = elsifInput
        }
        statement.elseClause?.statementList?.let {
            branches.add(flowStatementList(it, frame, remaining))
        } ?: branches.add(Flow(remaining))
        return mergeFlows(branches + guardAbrupt)
    }

    private fun flowUnless(statement: CrystalUnlessStatement, frame: Frame, incoming: State): Flow {
        val condition = statement.condition?.let { flowElement(it, frame, incoming) } ?: Flow(incoming)
        val branchInput = condition.normal ?: return condition
        val branches = mutableListOf(flowStatementList(statement.statementList, frame, branchInput))
        statement.elseClause?.statementList?.let {
            branches.add(flowStatementList(it, frame, branchInput))
        } ?: branches.add(Flow(branchInput))
        return mergeFlows(branches, condition)
    }

    private fun flowCase(statement: CrystalCaseStatement, frame: Frame, incoming: State): Flow {
        val subject = statement.assignment ?: statement.expression ?: statement.multiAssignment
        val subjectFlow = subject?.let { flowElement(it, frame, incoming) } ?: Flow(incoming)
        val branchInput = subjectFlow.normal ?: return subjectFlow
        val branches = mutableListOf<Flow>()
        val guardAbrupt = mutableListOf(subjectFlow.copy(normal = null))
        var remaining = branchInput
        for (clause in statement.whenClauseList) {
            val guards = flowCaseGuards(clause.expressionList, frame, remaining)
            guardAbrupt.add(guards.flow.copy(normal = null))
            guards.matches?.let { branches.add(flowStatementList(clause.statementList, frame, it)) }
            remaining = guards.fallthrough ?: break
        }
        for (clause in statement.inClauseList) {
            val guard = flowElement(clause.expressionList, frame, remaining)
            guardAbrupt.add(guard.copy(normal = null))
            val state = guard.normal ?: break
            branches.add(flowStatementList(clause.statementList, frame, state))
            remaining = state
        }
        statement.elseClause?.statementList?.let {
            branches.add(flowStatementList(it, frame, remaining))
        } ?: branches.add(Flow(remaining))
        return mergeFlows(branches + guardAbrupt)
    }

    private data class CaseGuards(
        val matches: State?,
        val fallthrough: State?,
        val flow: Flow
    )

    private fun flowCaseGuards(elements: List<PsiElement>, frame: Frame, incoming: State): CaseGuards {
        var fallthrough: State? = incoming
        val matches = mutableListOf<State>()
        val abrupt = mutableListOf<Flow>()
        for (element in elements) {
            val state = fallthrough ?: break
            val guard = flowElement(element, frame, state)
            guard.normal?.let(matches::add)
            fallthrough = guard.normal
            abrupt.add(guard.copy(normal = null))
        }
        return CaseGuards(
            matches.takeIf { it.isNotEmpty() }?.let(State::merge),
            fallthrough,
            mergeFlows(abrupt)
        )
    }

    private fun flowSelect(statement: CrystalSelectStatement, frame: Frame, incoming: State): Flow {
        val branches = statement.selectWhenClauseList.map { clause ->
            val guard = flowElement(clause.statement, frame, incoming)
            guard.normal?.let { flowStatementList(clause.statementList, frame, it) } ?: guard
        }.toMutableList()
        statement.elseClause?.statementList?.let {
            branches.add(flowStatementList(it, frame, incoming))
        } ?: branches.add(Flow(incoming))
        return mergeFlows(branches)
    }

    private fun flowBegin(statement: CrystalBeginStatement, frame: Frame, incoming: State): Flow {
        return flowProtected(
            statement.statementList,
            statement.rescueClauseList,
            statement.elseClause,
            statement.ensureClause,
            frame,
            incoming
        )
    }

    private fun flowProtected(
        bodyElement: CrystalStatementList?,
        rescueClauses: List<CrystalRescueClause>,
        elseClause: CrystalElseClause?,
        ensureClause: CrystalEnsureClause?,
        frame: Frame,
        incoming: State
    ): Flow {
        val body = flowStatementList(bodyElement, frame, incoming)
        val successfulBody = elseClause?.statementList?.let { elseBody ->
            body.normal?.let {
                mergeFlows(listOf(flowStatementList(elseBody, frame, it)), body.copy(normal = null))
            } ?: body
        } ?: body

        val protectedFlows = mutableListOf(successfulBody)
        if (rescueClauses.isNotEmpty()) {
            val rescueInput = exceptionalEntry(bodyElement, incoming)
            rescueClauses.mapTo(protectedFlows) {
                flowStatementList(it.statementList, frames[it] ?: frame, rescueInput)
            }
        }
        val protected = mergeFlows(protectedFlows)
        return ensureClause?.statementList?.let { applyEnsure(protected, it, frame) } ?: protected
    }

    private fun exceptionalEntry(body: CrystalStatementList?, incoming: State): State {
        if (body == null) return incoming
        val reaching = incoming.reaching.mapValuesTo(mutableMapOf()) { it.value.toMutableSet() }
        definitions.asSequence()
            .filter { it.id in reachedDefinitions && PsiTreeUtil.isAncestor(body, it.identifier, false) }
            .forEach { reaching.getOrPut(it.symbol.id) { linkedSetOf() }.add(it.id) }
        return State(reaching.mapValues { it.value.toSet() })
    }

    private fun applyEnsure(protected: Flow, body: CrystalStatementList, frame: Frame): Flow {
        var normal: State? = null
        val breaks = mutableListOf<State>()
        val continues = mutableListOf<State>()
        val returns = mutableListOf<State>()

        fun apply(state: State, continuation: MutableList<State>?) {
            val ensured = flowStatementList(body, frame, state)
            if (continuation == null) {
                normal = ensured.normal?.let { State.merge(listOfNotNull(normal, it)) } ?: normal
            } else {
                ensured.normal?.let(continuation::add)
            }
            breaks.addAll(ensured.breaks)
            continues.addAll(ensured.continues)
            returns.addAll(ensured.returns)
        }

        protected.normal?.let { apply(it, null) }
        protected.breaks.forEach { apply(it, breaks) }
        protected.continues.forEach { apply(it, continues) }
        protected.returns.forEach { apply(it, returns) }
        return Flow(normal, breaks, continues, returns)
    }

    private fun flowLoop(
        condition: PsiElement?,
        body: CrystalStatementList?,
        frame: Frame,
        incoming: State
    ): Flow {
        var entry = incoming
        var latest = Flow(incoming)
        val limit = definitions.size + 2
        repeat(limit) {
            val conditionFlow = condition?.let { flowElement(it, frame, entry) } ?: Flow(entry)
            val bodyFlow = conditionFlow.normal?.let { flowStatementList(body, frame, it) } ?: conditionFlow
            val nextEntry = State.merge(listOf(incoming) + listOfNotNull(bodyFlow.normal) + bodyFlow.continues)
            latest = mergeFlows(listOf(Flow(conditionFlow.normal), Flow(null, breaks = bodyFlow.breaks,
                returns = bodyFlow.returns)), conditionFlow.copy(normal = null))
            if (nextEntry == entry) return Flow(
                State.merge(listOfNotNull(conditionFlow.normal, incoming) + bodyFlow.breaks),
                returns = conditionFlow.returns + bodyFlow.returns
            )
            entry = nextEntry
        }
        return latest
    }

    private fun flowFor(statement: CrystalForStatement, frame: Frame, incoming: State): Flow {
        val iterable = statement.expression?.let { flowElement(it, frame, incoming) } ?: Flow(incoming)
        val iterableState = iterable.normal ?: return iterable
        val symbol = forSymbols[statement] ?: return flowLoop(null, statement.statementList, frame, iterableState)
        val definition = definitionsByFor[statement] ?: return flowLoop(null, statement.statementList, frame, iterableState)
        var entry = iterableState
        var bodyFlow = Flow(iterableState)
        val limit = definitions.size + 2
        repeat(limit) {
            reachedDefinitions.add(definition.id)
            val iteration = entry.write(symbol, definition)
            bodyFlow = flowStatementList(statement.statementList, frame, iteration)
            val nextEntry = State.merge(listOf(iterableState) + listOfNotNull(bodyFlow.normal) + bodyFlow.continues)
            if (nextEntry == entry) {
                return Flow(
                    State.merge(listOf(iterableState) + listOfNotNull(bodyFlow.normal) + bodyFlow.breaks),
                    returns = iterable.returns + bodyFlow.returns
                )
            }
            entry = nextEntry
        }
        return Flow(entry, returns = iterable.returns + bodyFlow.returns)
    }

    private fun flowBlock(block: CrystalBlock, incoming: State): Flow {
        val blockFrame = frames[block] ?: return Flow(incoming)
        var entry = incoming
        var bodyFlow = Flow(incoming)
        val limit = definitions.size + 2
        repeat(limit) {
            bodyFlow = flowProtected(
                block.statementList,
                block.rescueClauseList,
                block.elseClause,
                block.ensureClause,
                blockFrame,
                entry
            )
            val repeated = State.merge(listOf(incoming) + listOfNotNull(bodyFlow.normal) +
                bodyFlow.continues + bodyFlow.breaks)
            if (repeated == entry) {
                val locals = blockFrame.symbols.values.mapTo(mutableSetOf()) { it.id }
                return Flow(
                    repeated.without(locals),
                    returns = bodyFlow.returns
                )
            }
            entry = repeated
        }
        return Flow(entry, returns = bodyFlow.returns)
    }

    private fun flowReturn(statement: CrystalReturnStatement, frame: Frame, incoming: State): Flow {
        return flowAbruptValue(
            statement.assignment ?: statement.expression,
            statement.postfixModifier,
            frame,
            incoming,
            AbruptKind.RETURN
        )
    }

    private fun flowAbrupt(
        expression: PsiElement?,
        postfix: CrystalPostfixModifier?,
        frame: Frame,
        incoming: State,
        isBreak: Boolean
    ): Flow = flowAbruptValue(
        expression,
        postfix,
        frame,
        incoming,
        if (isBreak) AbruptKind.BREAK else AbruptKind.NEXT
    )

    private enum class AbruptKind { RETURN, BREAK, NEXT }

    private fun flowAbruptValue(
        expression: PsiElement?,
        outerPostfix: CrystalPostfixModifier?,
        frame: Frame,
        incoming: State,
        kind: AbruptKind
    ): Flow {
        val nestedAssignment = expression as? CrystalAssignment
        val postfix = outerPostfix ?: nestedAssignment?.postfixModifier
        val condition = postfix?.let { flowElement(it.expression, frame, incoming) }
        val base = condition?.normal ?: incoming
        val value = when {
            nestedAssignment != null && postfix === nestedAssignment.postfixModifier ->
                flowAssignmentCore(nestedAssignment, frame, base)
            expression != null -> flowElement(expression, frame, base)
            else -> Flow(base)
        }
        val abruptStates = listOfNotNull(value.normal)
        return Flow(
            normal = if (postfix == null) null else base,
            breaks = condition?.breaks.orEmpty() + value.breaks +
                if (kind == AbruptKind.BREAK) abruptStates else emptyList(),
            continues = condition?.continues.orEmpty() + value.continues +
                if (kind == AbruptKind.NEXT) abruptStates else emptyList(),
            returns = condition?.returns.orEmpty() + value.returns +
                if (kind == AbruptKind.RETURN) abruptStates else emptyList()
        )
    }

    private fun flowStatementList(list: CrystalStatementList?, frame: Frame, incoming: State): Flow =
        list?.let { flowChildren(it, frame, incoming) } ?: Flow(incoming)

    private fun flowSequence(elements: List<PsiElement>, frame: Frame, incoming: State): Flow {
        var flow = Flow(incoming)
        for (element in elements) {
            val state = flow.normal ?: break
            flow = mergeFlows(listOf(flowElement(element, frame, state)), flow.copy(normal = null))
        }
        return flow
    }

    private fun mergeFlows(flows: List<Flow>, prior: Flow? = null): Flow {
        val all = if (prior == null) flows else flows + prior.copy(normal = null)
        return Flow(
            all.mapNotNull { it.normal }.takeIf { it.isNotEmpty() }?.let(State::merge),
            all.flatMap { it.breaks },
            all.flatMap { it.continues },
            all.flatMap { it.returns }
        )
    }

    private fun read(symbol: Symbol, state: State, frame: Frame) {
        usedDefinitions.addAll(state.definitions(symbol))
        if (symbol.frame !== frame) escapedReads.add(symbol.id to frame.owner.textRange.endOffset)
    }

    private fun markVisibleDefinitionsUsed(state: State) {
        state.reaching.values.forEach(usedDefinitions::addAll)
    }

    private fun isVisibleMacroCall(call: PsiElement): Boolean {
        val name = call.node.findChildByType(CrystalTypes.IDENTIFIER)?.text ?: return false
        return visibleMacroCalls.getOrPut(name) {
            val macros = CrystalIndexService.findMacros(name, call.project, GlobalSearchScope.allScope(call.project))
            if (macros.isEmpty()) return@getOrPut false
            val sources = CrystalRequireGraphService.getInstance(call.project).effectiveSources(call)
            macros.any { it.containingFile == call.containingFile || sources.contains(it) }
        }
    }

    private fun localAssignmentIdentifier(assignment: CrystalAssignment): PsiElement? {
        if (assignment.instanceVarAccess != null || assignment.classVarAccess != null) return null
        return (assignment as? PsiNameIdentifierOwner)?.nameIdentifier
            ?: assignment.node.findChildByType(CrystalTypes.IDENTIFIER)?.psi
    }

    private fun localReferenceName(reference: CrystalVariableReference): String? =
        reference.node.findChildByType(CrystalTypes.IDENTIFIER)?.text

    private fun forIdentifier(statement: CrystalForStatement): PsiElement? =
        statement.node.findChildByType(CrystalTypes.IDENTIFIER)?.psi

    private fun groupedAssignmentIdentifier(grouped: CrystalGroupedExpression): PsiElement? {
        if (grouped.expressionList.size < 2) return null
        val reference = grouped.expressionList.first().children.singleOrNull() as? CrystalVariableReference
            ?: return null
        return reference.node.findChildByType(CrystalTypes.IDENTIFIER)?.psi
    }

    private fun isLocalReadIdentifier(identifier: PsiElement): Boolean {
        val parent = identifier.parent
        if ((parent as? PsiNameIdentifierOwner)?.nameIdentifier === identifier) return false
        if (parameterSymbols.containsKey(identifier)) return false

        val previous = generateSequence(identifier.prevSibling) { it.prevSibling }
            .firstOrNull { it.textLength > 0 && !it.text.isBlank() }
        if (previous?.node?.elementType == CrystalTypes.DOT ||
            previous?.node?.elementType == CrystalTypes.DOUBLE_COLON) return false

        val next = generateSequence(identifier.nextSibling) { it.nextSibling }
            .firstOrNull { it.textLength > 0 && !it.text.isBlank() }
        if (next?.node?.elementType == CrystalTypes.COLON) return false

        if (parent is CrystalMethodCallExpression || parent is CrystalBareMethodCallExpression) {
            val callee = parent.node.findChildByType(CrystalTypes.IDENTIFIER)?.psi
            if (callee === identifier) {
                val local = (parent.reference as? CrystalReference)?.resolveLocalDeclaration()
                return local != null
            }
        }
        return true
    }

    private fun isHardBoundary(element: PsiElement): Boolean =
        element is CrystalMethodDefinition || element is CrystalMacroDefinition ||
            element is CrystalClassDefinition || element is CrystalModuleDefinition ||
            element is CrystalStructDefinition || element is CrystalEnumDefinition ||
            element is CrystalFile
}
