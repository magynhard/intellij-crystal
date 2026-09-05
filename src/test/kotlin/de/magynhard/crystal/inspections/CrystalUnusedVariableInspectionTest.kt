package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalUnusedVariableInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalUnusedVariableInspection::class.java)
    }

    // ==================== Basic Unused Variables ====================

    fun testUnusedVariable() {
        myFixture.configureByText("test.cr", """
            def foo
              <weak_warning descr="Variable 'x' is never used">x</weak_warning> = 42
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUsedVariable() {
        myFixture.configureByText("test.cr", """
            def foo
              x = 42
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnderscorePrefixNoWarning() {
        myFixture.configureByText("test.cr", """
            def foo
              _unused = 42
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Reassignment ====================

    fun testReassignmentFirstUnused() {
        myFixture.configureByText("test.cr", """
            def foo
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = 1
              x = 2
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testReassignmentLastUnused() {
        myFixture.configureByText("test.cr", """
            def foo
              x = 1
              puts x
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = 2
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testReassignmentBothUsed() {
        myFixture.configureByText("test.cr", """
            def foo
              x = 1
              puts x
              x = 2
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Compound Assignment ====================

    fun testCompoundAssignmentNoWarning() {
        myFixture.configureByText("test.cr", """
            def foo
              x = 0
              x += 1
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Method Parameters ====================

    fun testMethodParameterNoWarning() {
        myFixture.configureByText("test.cr", """
            def foo(x : Int32, y : String)
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Instance/Class Vars ====================

    fun testInstanceVarNotChecked() {
        myFixture.configureByText("test.cr", """
            def foo
              @name = "hello"
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testClassVarNotChecked() {
        myFixture.configureByText("test.cr", """
            def foo
              @@count = 0
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Variable Used in Branch ====================

    fun testVariableUsedInIfBranch() {
        myFixture.configureByText("test.cr", """
            def foo
              x = 42
              if true
                puts x
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Multiple Unused Variables ====================

    fun testMultipleUnusedVariables() {
        myFixture.configureByText("test.cr", """
            def foo
              <weak_warning descr="Variable 'a' is never used">a</weak_warning> = 1
              <weak_warning descr="Variable 'b' is never used">b</weak_warning> = 2
              c = 3
              puts c
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Top-Level Variables ====================

    fun testTopLevelUnusedVariable() {
        myFixture.configureByText("test.cr", """
            <weak_warning descr="Variable 'f' is never used">f</weak_warning> = "hello"
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTopLevelUsedVariable() {
        myFixture.configureByText("test.cr", """
            f = "hello"
            puts f
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Variable Used in Expression ====================

    fun testVariableUsedInArithmeticExpression() {
        myFixture.configureByText("test.cr", """
            def foo
              local_var = "local"
              p = local_var + "sdf"
              puts "a:#{p}"
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testVariableUsedInArithmeticExpressionTopLevel() {
        myFixture.configureByText("test.cr", """
            local_var = "local"
            p = local_var + "sdf"
            puts "a:#{p}"
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testVariableUsedInAddition() {
        myFixture.configureByText("test.cr", """
            def foo
              x = 1
              y = x + 2
              puts y
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testVariableUsedInStringConcat() {
        myFixture.configureByText("test.cr", """
            def foo
              name = "world"
              greeting = "hello " + name
              puts greeting
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testVariableUsedInStringInterpolation() {
        myFixture.configureByText("test.cr", """
            def foo
              name = "world"
              puts "hello #{name}"
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Variable Used in Own Reassignment ====================

    fun testVariableUsedInOwnReassignment() {
        myFixture.configureByText("test.cr", """
            def foo
              abc = "string"
              abc = abc.upcase
              puts abc
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Conditional Branch False Positives ====================

    fun testInitialAssignWithConditionalReassignInIf() {
        myFixture.configureByText("test.cr", """
            def foo
              x = ""
              if true
                x = "override"
              end
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithConditionalReassignInUnless() {
        myFixture.configureByText("test.cr", """
            def foo
              x = ""
              unless true
                x = "override"
              end
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNonRaisingReassignBeforeRescueSupersedesInitialValue() {
        myFixture.configureByText("test.cr", """
            def foo
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = ""
              begin
                x = "override"
              rescue
              end
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithConditionalReassignInWhile() {
        myFixture.configureByText("test.cr", """
            def foo
              x = ""
              while false
                x = "override"
              end
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithConditionalReassignInUntil() {
        myFixture.configureByText("test.cr", """
            def foo
              x = ""
              until true
                x = "override"
              end
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithConditionalReassignInFor() {
        myFixture.configureByText("test.cr", """
            def foo
              x = ""
              for item in [] of String
                x = item
              end
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithConditionalReassignInCase() {
        myFixture.configureByText("test.cr", """
            def foo
              x = ""
              case 1
              when 1
                x = "one"
              end
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithNestedConditionals() {
        myFixture.configureByText("test.cr", """
            def foo
              x = ""
              if true
                if false
                  x = "deep"
                end
              end
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithConditionalAndUnconditionalOverwrite() {
        myFixture.configureByText("test.cr", """
            def foo
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = ""
              if true
                <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = "override"
              end
              x = "final"
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBeginWithoutRescueStillWarns() {
        myFixture.configureByText("test.cr", """
            def foo
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = ""
              begin
                x = "override"
              end
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testConditionalAssignInElsifChain() {
        myFixture.configureByText("test.cr", """
            def foo
              x = ""
              if true
                x = "one"
              elsif false
                x = "two"
              end
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testConditionalAssignInIfElse() {
        myFixture.configureByText("test.cr", """
            def foo
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = ""
              if true
                x = "one"
              else
                x = "two"
              end
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Real-World Def-Use Regression Cases ====================

    fun testDotReceiverCountsAsRead() {
        myFixture.configureByText("test.cr", """
            def run
              server = HTTP::Server.new
              server.each_address { |address| puts address }
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNilCheckAndTryReceiverCountAsReads() {
        myFixture.configureByText("test.cr", """
            def parse(headers)
              content_type = headers["Content-Type"]?
              return unless content_type
              content_type.try(&.starts_with?("application/json"))
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testCompoundAssignmentsAndPostfixConditionReadsInsideBlock() {
        myFixture.configureByText("test.cr", """
            def parse(range)
              max_ranges = 16
              requested_bytes = 0_i64
              parts = 0
              range.split(',') do |part|
                parts += 1
                return if parts > max_ranges
                requested_bytes += part.size
                return if requested_bytes > 1024
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testCapturedBlockWriteDoesNotKillOuterFallback() {
        myFixture.configureByText("test.cr", """
            def run
              handler_ran = false
              register do
                handler_ran = true
              end
              dispatch
              puts handler_ran
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNestedCallbackWriteDoesNotKillOuterFallback() {
        myFixture.configureByText("test.cr", """
            def parse
              test_option = nil
              configure do |parser|
                parser.on("--test") do |value|
                  test_option = value
                end
              end
              puts test_option
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRepeatedBlockWriteReachesNextIterationRead() {
        myFixture.configureByText("test.cr", """
            def build(handlers)
              current_handler = handlers.first
              handlers.each do |handler|
                current_handler.next = handler
                current_handler = handler
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBlockParameterShadowsOuterLocal() {
        myFixture.configureByText("test.cr", """
            def run(items)
              <weak_warning descr="Variable 'item' is never used">item</weak_warning> = "outer"
              items.each do |item|
                puts item
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNewBlockLocalDoesNotLeakIntoOuterScope() {
        myFixture.configureByText("test.cr", """
            def run(items)
              items.each do |item|
                local = item
                puts local
              end
              <weak_warning descr="Variable 'local' is never used">local</weak_warning> = "outer"
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testOpaqueMacroMayReadVisibleLocals() {
        myFixture.configureByText("test.cr", """
            macro render(path)
              ECR.embed({{path}})
            end

            def show
              name = "world"
              render "hello.ecr"
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRegularMethodDoesNotReadEveryVisibleLocal() {
        myFixture.configureByText("test.cr", """
            def render(path)
            end

            def show
              <weak_warning descr="Variable 'name' is never used">name</weak_warning> = "world"
              render "hello.ecr"
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testCapturedReadMayObserveLaterAssignment() {
        myFixture.configureByText("test.cr", """
            def run
              value = 1
              register do
                puts value
              end
              value = 2
              dispatch
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testCompoundAssignmentRhsAndNegatedDotCallWithBlock() {
        myFixture.configureByText("test.cr", """
            def run(config)
              server = config.server ||= HTTP::Server.new(config.handlers)
              if !server.each_address { |_| break true }
                server.bind_tcp(config.host, config.port)
              end
              display(config, server)
              server.listen
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testOptionalIndexResultUsedByNilCheckAndAbbreviatedProc() {
        myFixture.configureByText("test.cr", """
            def raw_body(request)
              content_type = request.headers["Content-Type"]?
              return "" if content_type.nil?
              if content_type.try(&.starts_with?("form")) || content_type.try(&.starts_with?("json"))
                read_body
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testIndexedReceiverBlockCompoundReadsAndPostfixReturn() {
        myFixture.configureByText("test.cr", """
            private def parse_ranges(range_header : String?, file_size : Int64) : Array({Int64, Int64})
              return [] of {Int64, Int64} unless range_header
              ranges = [] of {Int64, Int64}
              return ranges unless range_header.starts_with?("bytes=")
              max_ranges = config.max_ranges
              requested_bytes = 0_i64
              parts = 0
              range_header[6..].split(",") do |range|
                parts += 1
                return [] if parts > max_ranges
                requested_bytes += range.size
                return [] if requested_bytes > file_size
                if match = range.match /(\d{1,})-(\d{0,})/
                  startb = match[1].to_i64 { 0_i64 }
                  endb = match[2].to_i64 { 0_i64 }
                  puts startb, endb
                end
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBlockLocalUsedByPostfixNextWithBareArguments() {
        myFixture.configureByText("test.cr", """
            def send_file(env)
              File.open("file") do |file|
                if env.has_key?("Range")
                  ranges = parse_ranges(env["Range"]?, file.size)
                  next multipart(file, env, ranges) unless ranges.empty?
                end
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRescueMayReadAssignmentMadeBeforeException() {
        myFixture.configureByText("test.cr", """
            def process
              value = "initial"
              begin
                value = compute
                fail
              rescue
                puts value
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRescueVariableShadowsOuterBinding() {
        myFixture.configureByText("test.cr", """
            def process
              <weak_warning descr="Variable 'error' is never used">error</weak_warning> = "outer"
              begin
                fail
              rescue error
                puts error
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testEnsureReadsValueOnReturnPath() {
        myFixture.configureByText("test.cr", """
            def process
              value = compute
              begin
                return
              ensure
                puts value
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMethodEnsureReadsValueOnReturnPath() {
        myFixture.configureByText("test.cr", """
            def process
              value = compute
              return
            ensure
              puts value
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBlockEnsureReadsBlockLocalOnReturnPath() {
        myFixture.configureByText("test.cr", """
            def process
              transaction do
                value = compute
                return
              ensure
                puts value
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testShortCircuitWriteDoesNotKillFallback() {
        myFixture.configureByText("test.cr", """
            def process(condition)
              value = "fallback"
              condition && (value = compute)
              puts value
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTernaryWritesMergeAtFollowingRead() {
        myFixture.configureByText("test.cr", """
            def process(condition)
              <weak_warning descr="Value assigned to 'value' is never used">value</weak_warning> = "fallback"
              condition ? (value = first) : (value = second)
              puts value
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testCaseAlternativeGuardDoesNotUnconditionallyOverwrite() {
        myFixture.configureByText("test.cr", """
            def process
              value = "fallback"
              case
              when ready?, (value = compute)
                puts value
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSelectBranchesMergeAtFollowingRead() {
        myFixture.configureByText("test.cr", """
            def process(channel)
              <weak_warning descr="Value assigned to 'value' is never used">value</weak_warning> = "fallback"
              select
              when channel.receive
                value = "received"
              else
                value = "ready"
              end
              puts value
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testAssignmentValuedPostfixReturnPreservesFallthrough() {
        myFixture.configureByText("test.cr", """
            def process(condition)
              value = "fallback"
              return <weak_warning descr="Value assigned to 'value' is never used">value</weak_warning> = compute if condition
              puts value
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testEveryMultiValueReturnExpressionIsRead() {
        myFixture.configureByText("test.cr", """
            def values
              first = 1
              second = 2
              return first, second
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testEveryMultiValueBreakAndNextExpressionIsRead() {
        myFixture.configureByText("test.cr", """
            def values
              break_first = 1
              break_second = 2
              loop do
                break break_first, break_second
              end
              next_first = 3
              next_second = 4
              1.times do
                next next_first, next_second
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testReturnedHeredocInterpolationReadsLocal() {
        myFixture.configureByText("test.cr", """
            def message
              name = "Crystal"
              return <<-MSG
                Hello #{name}
                MSG
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMultiValueReturnEvaluatesHeredocBeforeFollowingAssignment() {
        myFixture.configureByText("test.cr", """
            def message
              name = "old"
              return @message = <<-MSG, <weak_warning descr="Value assigned to 'name' is never used">name</weak_warning> = "new"
                #{name}
                MSG
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPostfixIndexedAssignmentKeepsSkippedRhsDefinitionReachable() {
        myFixture.configureByText("test.cr", """
            def update(values, flag)
              x = "old"
              values[0] = x = 1 if flag
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testShortCircuitIndexedAssignmentKeepsSkippedRhsDefinitionReachable() {
        myFixture.configureByText("test.cr", """
            def update(values)
              x = "old"
              values[0] ||= x = 1
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testAndShortCircuitIndexedAssignmentKeepsSkippedRhsDefinitionReachable() {
        myFixture.configureByText("test.cr", """
            def update(values)
              x = "old"
              values[0] &&= x = 1
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPostfixAssignmentConditionDefinitionReachesLaterRead() {
        myFixture.configureByText("test.cr", """
            def update
              <weak_warning descr="Value assigned to 'condition' is never used">condition</weak_warning> = "old"
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = 1 if condition = true
              puts condition
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPostfixAssignmentRescueKeepsBothDefinitionsReachable() {
        myFixture.configureByText("test.cr", """
            def update
              fallback = "old"
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = danger rescue fallback = 2
              puts fallback
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPostfixAssignmentRescueReadsOnlyDefinitionBeforeFailedAssignment() {
        myFixture.configureByText("test.cr", """
            def update
              x = 0
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = danger rescue puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNonRaisingPostfixAssignmentDoesNotReachRescueDefinition() {
        myFixture.configureByText("test.cr", """
            def update
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = 1 rescue fallback = 2
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testGroupedLiteralDoesNotReachPostfixRescueDefinition() {
        myFixture.configureByText("test.cr", """
            def update
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = (1) rescue fallback = 2
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testScalarAndPlainStringLiteralsDoNotReachPostfixRescueDefinitions() {
        myFixture.configureByText("test.cr", """
            def update
              <weak_warning descr="Variable 'float_value' is never used">float_value</weak_warning> = 1.5 rescue float_fallback = 1
              <weak_warning descr="Variable 'char_value' is never used">char_value</weak_warning> = 'x' rescue char_fallback = 1
              <weak_warning descr="Variable 'symbol_value' is never used">symbol_value</weak_warning> = :safe rescue symbol_fallback = 1
              <weak_warning descr="Variable 'true_value' is never used">true_value</weak_warning> = true rescue true_fallback = 1
              <weak_warning descr="Variable 'false_value' is never used">false_value</weak_warning> = false rescue false_fallback = 1
              <weak_warning descr="Variable 'nil_value' is never used">nil_value</weak_warning> = nil rescue nil_fallback = 1
              <weak_warning descr="Variable 'string_value' is never used">string_value</weak_warning> = "safe" rescue string_fallback = 1
              <weak_warning descr="Variable 'symbol_string_value' is never used">symbol_string_value</weak_warning> = :"safe" rescue symbol_string_fallback = 1
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPlainHeredocDoesNotReachPostfixRescueDefinition() {
        myFixture.configureByText("test.cr", """
            def update
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = <<-TEXT rescue (fallback = 2)
                safe
                TEXT
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testFailedNestedAssignmentDoesNotReachPostfixRescue() {
        myFixture.configureByText("test.cr", """
            def update
              y = 0
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = (<weak_warning descr="Value assigned to 'y' is never used">y</weak_warning> = danger) rescue puts y
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testCompletedNestedAssignmentReachesLaterPostfixRescueFailure() {
        myFixture.configureByText("test.cr", """
            def update
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = 0
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = (x = 1) && danger rescue puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testProtectedBodyNewlinesDoNotPreserveSupersededDefinition() {
        myFixture.configureByText("test.cr", """
            def update
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = 0
              begin
                x = 1
                danger
              rescue
                puts x
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testLocalReadDoesNotMakeRescueReachable() {
        myFixture.configureByText("test.cr", """
            def update
              <weak_warning descr="Variable 'z' is never used">z</weak_warning> = 0
              x = 1
              begin
                <weak_warning descr="Variable 'y' is never used">y</weak_warning> = x
              rescue
                puts z
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testOverwrittenDefinitionDoesNotReachLaterRescueFailure() {
        myFixture.configureByText("test.cr", """
            def update
              x = 0
              begin
                <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = danger1
                x = 2
                danger2
              rescue
                puts x
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDotCallKeepsRescueReachable() {
        myFixture.configureByText("test.cr", """
            def update
              x = 0
              begin
                self.danger
              rescue
                puts x
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testConditionalDefinitionDominatesFailureInSameBranch() {
        myFixture.configureByText("test.cr", """
            def update(flag)
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = 0
              begin
                if flag
                  x = 1
                  danger
                end
              rescue
                puts x
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testCompoundAssignmentFailureSeesCompletedRhsDefinition() {
        myFixture.configureByText("test.cr", """
            def update
              x = 1
              <weak_warning descr="Value assigned to 'y' is never used">y</weak_warning> = 0
              begin
                x += (y = 1)
              rescue
                puts y
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testHeredocDefinitionReachesLaterInterpolationFailure() {
        myFixture.configureByText("test.cr", """
            def update
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = 0
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = <<-TEXT rescue (puts x)
                #{(x = 1)}
                #{danger}
                TEXT
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testInterpolatedSymbolCanReachPostfixRescue() {
        myFixture.configureByText("test.cr", """
            def update
              x = 0
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = :"#{danger}" rescue puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testInterpolatedStringCanReachPostfixRescue() {
        myFixture.configureByText("test.cr", """
            def update
              x = 0
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = "#{danger}" rescue puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testInterpolatedHeredocCanReachPostfixRescue() {
        myFixture.configureByText("test.cr", """
            def update
              x = 0
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = <<-TEXT rescue (x)
                #{danger}
                TEXT
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPostfixRescueReturnKeepsSuccessfulAndHandledDefinitionsReachable() {
        myFixture.configureByText("test.cr", """
            def update
              x = "old"
              begin
                return danger rescue x = 1
              ensure
                puts x
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPostfixRescueBreakKeepsSuccessfulAndHandledDefinitionsReachable() {
        myFixture.configureByText("test.cr", """
            def update
              x = "old"
              loop do
                break danger rescue x = 1
              end
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPostfixRescueNextKeepsSuccessfulAndHandledDefinitionsReachable() {
        myFixture.configureByText("test.cr", """
            def update
              x = "old"
              1.times do
                next danger rescue x = 1
              end
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testIndexedAssignmentRescueSeesDefinitionsReachedBeforeFailure() {
        myFixture.configureByText("test.cr", """
            def update(values)
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = "old"
              values[consume(x = 1)] = danger rescue puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBreakAndNextHeredocInterpolationsReadDistinctLocals() {
        myFixture.configureByText("test.cr", """
            def messages
              break_name = "break"
              loop do
                break <<-MSG
                  #{break_name}
                  MSG
              end
              next_name = "next"
              1.times do
                next <<-MSG
                  #{next_name}
                  MSG
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testParenthesizedReturnMakesFollowingAssignmentUnreachable() {
        myFixture.configureByText("test.cr", """
            def process
              (return)
              unreachable = compute
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testElsifConditionWriteReachesElseRead() {
        myFixture.configureByText("test.cr", """
            def process(condition)
              if condition
                puts "first"
              elsif value = nil
                puts "second"
              else
                puts value
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testForIteratorWriteDoesNotReadOverwrittenOuterValue() {
        myFixture.configureByText("test.cr", """
            def process(items)
              <weak_warning descr="Value assigned to 'item' is never used">item</weak_warning> = "outer"
              for item in items
                puts item
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testCaptureDoesNotKeepAlreadyOverwrittenDefinitionLive() {
        myFixture.configureByText("test.cr", """
            def process
              <weak_warning descr="Value assigned to 'value' is never used">value</weak_warning> = 1
              value = 2
              register { puts value }
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTypeBodiesAreIndependentScopes() {
        myFixture.configureByText("test.cr", """
            class Example
              <weak_warning descr="Variable 'class_value' is never used">class_value</weak_warning> = 2
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

}
