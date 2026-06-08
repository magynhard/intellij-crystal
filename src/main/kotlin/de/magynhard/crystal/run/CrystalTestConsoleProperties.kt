package de.magynhard.crystal.run

import com.intellij.execution.Executor
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import java.io.File

class CrystalTestConsoleProperties(
    configuration: CrystalRunConfiguration,
    executor: Executor,
    private val testLocations: Map<String, List<CrystalSpecFileIndexer.TestLocation>> = emptyMap(),
    private val junitOutputFile: File? = null
) : SMTRunnerConsoleProperties(configuration, "CrystalSpec", executor), SMCustomMessagesParsing {

    override fun getTestLocator(): SMTestLocator = CrystalTestLocator.INSTANCE

    init {
        isUsePredefinedMessageFilter = true
        setIfUndefined(SMTRunnerConsoleProperties.HIDE_PASSED_TESTS, false)
        setIfUndefined(SMTRunnerConsoleProperties.OPEN_FAILURE_LINE, true)
        setIfUndefined(SMTRunnerConsoleProperties.SCROLL_TO_SOURCE, true)
        setIfUndefined(SMTRunnerConsoleProperties.SELECT_FIRST_DEFECT, true)
    }

    override fun createTestEventsConverter(
        testFrameworkName: String,
        consoleProperties: TestConsoleProperties
    ): OutputToGeneralTestEventsConverter {
        return CrystalTestEventsConverter(testFrameworkName, consoleProperties, testLocations, junitOutputFile)
    }
}
