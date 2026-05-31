package de.magynhard.crystal.run

import com.intellij.execution.Executor
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties

class CrystalTestConsoleProperties(
    configuration: CrystalRunConfiguration,
    executor: Executor
) : SMTRunnerConsoleProperties(configuration, "CrystalSpec", executor), SMCustomMessagesParsing {

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
        return CrystalTestEventsConverter(testFrameworkName, consoleProperties)
    }
}
