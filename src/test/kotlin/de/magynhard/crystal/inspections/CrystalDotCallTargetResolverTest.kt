package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalBareArgumentList
import de.magynhard.crystal.psi.CrystalCallArgs
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalPsiUtils

class CrystalDotCallTargetResolverTest : BasePlatformTestCase() {

    fun testResolvesDirectQualifiedAbsoluteAndLexicalConstantIdentities() {
        val source = """
            class DirectService
              def self.global_call
              end
            end

            module Outer
              class Service
                def self.qualified_call
                end

                def self.absolute_call
                end

                def self.lexical_call
                end
              end

              def self.exercise
                Service.lexical_call
              end
            end

            module Other
              class DirectService
                def self.global_call(unrelated)
                end
              end

              class Service
                def self.qualified_call(unrelated)
                end
              end
            end

            DirectService.global_call
            Outer::Service.qualified_call
            ::Outer::Service.absolute_call
        """.trimIndent()

        assertMethods(source, "global_call", "DirectService", "DirectService", "DirectService")
        assertMethods(source, "qualified_call", "Outer::Service", "Outer::Service", "Outer::Service")
        assertMethods(source, "absolute_call", "::Outer::Service", "Outer::Service", "Outer::Service")
        assertMethods(source, "lexical_call", "Service", "Outer::Service", "Outer::Service")
    }

    fun testAmbiguousLexicalSimpleConstantSuppressesResolution() {
        val call = resolveCall(
            """
                class Service
                  def self.run
                  end
                end

                module Outer
                  class Service
                    def self.run
                    end
                  end

                  def self.exercise
                    Service.run
                  end
                end
            """.trimIndent(),
            "run",
            "Service"
        )

        assertSame(DotCallResolution.Suppressed, call.resolution)
    }

    fun testSeparatesStaticInstanceAndUnrelatedSameNamedMethods() {
        val source = """
            class Target
              def self.run(static_value)
              end

              def run(instance_value, optional = 1)
              end
            end

            class Other
              def self.run(unrelated_static, extra)
              end

              def run(unrelated_instance, extra)
              end
            end

            Target.run(1)
            target = Target.new
            target.run(1)
        """.trimIndent()

        val static = assertMethods(source, "run", "Target", "Target", "Target")
        assertEquals("static_value", static.methods.single().parameterList?.text)

        val instance = assertMethods(source, "run", "target", "Target", "Target")
        assertEquals("instance_value, optional = 1", instance.methods.single().parameterList?.text)
    }

    fun testTraversesSuperclassIncludesTransitivelyInCrystalLookupOrder() {
        val methods = assertMethods(
            """
                module BaseFeature
                  def work(base_feature)
                  end
                end

                module EarlyFeature
                  include BaseFeature

                  def work(early_feature, optional = 1)
                  end
                end

                module LateFeature
                  def work(late_feature, optional = 1, extra = 2)
                  end
                end

                class Parent
                  def work(parent_feature, optional = 1, extra = 2, final = 3)
                  end
                end

                class Child < Parent
                  include EarlyFeature
                  include LateFeature

                  def work(direct)
                  end
                end

                value = Child.new
                value.work(1)
            """.trimIndent(),
            "work",
            "value",
            "Child",
            "Child",
            "LateFeature",
            "EarlyFeature",
            "BaseFeature",
            "Parent"
        )

        assertEquals(
            listOf("direct", "late_feature, optional = 1, extra = 2",
                "early_feature, optional = 1", "base_feature",
                "parent_feature, optional = 1, extra = 2, final = 3"),
            methods.methods.map { it.parameterList?.text ?: "()" }
        )
    }

    fun testQualifiedSuperclassAndExtendUseExactIdentities() {
        val source = """
            module Shared
              module FactoryMethods
                def create(shared)
                end
              end

              class Parent
                def self.create(parent)
                end
              end
            end

            module Other
              module FactoryMethods
                def create(unrelated)
                end
              end

              class Parent
                def self.create(unrelated)
                end
              end
            end

            class Factory < Shared::Parent
              extend Shared::FactoryMethods

              def self.create(direct, optional = 1)
              end
            end

            Factory.create(1)
        """.trimIndent()

        val methods = assertMethods(
            source,
            "create",
            "Factory",
            "Factory",
            "Factory",
            "Shared::FactoryMethods",
            "Shared::Parent"
        )
        assertEquals(
            listOf("direct, optional = 1", "shared", "parent"),
            methods.methods.map { it.parameterList?.text }
        )
    }

    fun testSimpleSuperclassAndTransitiveExtendIncludeEdges() {
        val methods = assertMethods(
            """
                module Core
                  def build(core)
                  end
                end

                module FactoryFeature
                  include Core

                  def build(feature, optional = 1)
                  end
                end

                class Base
                  def self.build(base, optional = 1, extra = 2)
                  end
                end

                class Factory < Base
                  extend FactoryFeature
                end

                Factory.build(1)
            """.trimIndent(),
            "build",
            "Factory",
            "Factory",
            "FactoryFeature",
            "Core",
            "Base"
        )

        assertEquals(
            listOf("feature, optional = 1", "core", "base, optional = 1, extra = 2"),
            methods.methods.map { it.parameterList?.text }
        )
    }

    fun testDirectModuleStaticLookupTraversesTransitiveExtendEdge() {
        val methods = assertMethods(
            """
                module BaseFactory
                  def build(value)
                  end
                end

                module Factory
                  extend BaseFactory
                end

                Factory.build(1)
            """.trimIndent(),
            "build",
            "Factory",
            "Factory",
            "BaseFactory"
        )

        assertEquals("value", methods.methods.single().parameterList?.text)
    }

    fun testExtendSelfExposesInstanceMethodsWithoutHidingDirectSelfMethods() {
        val source = """
            module Feature
              extend self

              def exposed(value)
              end

              def self.direct(value, optional = 1)
              end
            end

            Feature.exposed(1)
            Feature.direct(1)
        """.trimIndent()

        val exposed = assertMethods(source, "exposed", "Feature", "Feature", "Feature")
        assertFalse(CrystalPsiUtils.isSelfMethod(exposed.methods.single()))

        val direct = assertMethods(source, "direct", "Feature", "Feature", "Feature")
        assertTrue(CrystalPsiUtils.isSelfMethod(direct.methods.single()))
    }

    fun testCollectsHierarchyMetadataAcrossExactTypeReopenings() {
        val methods = assertMethods(
            """
                module Early
                  def work(early)
                  end
                end

                module Late
                  def work(late, optional = 1)
                  end
                end

                class Worker
                  include Early
                end

                class Worker
                  include Late
                end

                worker = Worker.new
                worker.work(1)
            """.trimIndent(),
            "work",
            "worker",
            "Worker",
            "Late",
            "Early"
        )

        assertEquals(listOf("late, optional = 1", "early"), methods.methods.map { it.parameterList?.text })
    }

    fun testSuppressesConstructorWhenSuperclassIdentityIsMissing() {
        val call = resolveCall(
            """
                class Child < MissingParent
                end

                Child.new
            """.trimIndent(),
            "new",
            "Child"
        )

        assertSame(DotCallResolution.Suppressed, call.resolution)
    }

    fun testSuppressesAmbiguousIncludedAndExtendedModuleIdentities() {
        val included = resolveCall(
            """
                module Feature
                  def work(global_value)
                  end
                end

                module Outer
                  module Feature
                    def work(lexical_value)
                    end
                  end

                  class Worker
                    include Feature
                  end
                end

                worker = Outer::Worker.new
                worker.work(1)
            """.trimIndent(),
            "work",
            "worker"
        )
        assertSame(DotCallResolution.Suppressed, included.resolution)

        val extended = resolveCall(
            """
                module FactoryMethods
                  def build(global_value)
                  end
                end

                module Outer
                  module FactoryMethods
                    def build(lexical_value)
                    end
                  end

                  class Factory
                    extend FactoryMethods
                  end
                end

                Outer::Factory.build(1)
            """.trimIndent(),
            "build",
            "Outer::Factory"
        )
        assertSame(DotCallResolution.Suppressed, extended.resolution)
    }

    fun testUnresolvedEdgesInTheOtherExposureModeDoNotSuppress() {
        val instanceMethods = assertMethods(
            """
                module Feature
                  def work(value)
                  end
                end

                class Worker
                  include Feature
                  extend MissingFactoryMethods
                end

                worker = Worker.new
                worker.work(1)
            """.trimIndent(),
            "work",
            "worker",
            "Worker",
            "Feature"
        )
        assertEquals("value", instanceMethods.methods.single().parameterList?.text)

        val staticMethods = assertMethods(
            """
                class Factory
                  include MissingInstanceMethods

                  def self.build(value)
                  end
                end

                Factory.build(1)
            """.trimIndent(),
            "build",
            "Factory",
            "Factory",
            "Factory"
        )
        assertEquals("value", staticMethods.methods.single().parameterList?.text)
    }

    fun testSuppressesConflictingSuperclassesAcrossExactReopenings() {
        val call = resolveCall(
            """
                class FirstParent
                  def work(first)
                  end
                end

                class SecondParent
                  def work(second)
                  end
                end

                class Worker < FirstParent
                end

                class Worker < SecondParent
                end

                worker = Worker.new
                worker.work(1)
            """.trimIndent(),
            "work",
            "worker"
        )

        assertSame(DotCallResolution.Suppressed, call.resolution)
    }

    fun testSuppressesMacroControlledMethodsAndRelevantEdges() {
        val method = resolveCall(
            """
                class Worker
                  {% if false %}
                  def work(value)
                  end
                  {% end %}
                end

                worker = Worker.new
                worker.work(1)
            """.trimIndent(),
            "work",
            "worker"
        )
        assertSame(DotCallResolution.Suppressed, method.resolution)

        val edge = resolveCall(
            """
                module Feature
                  def work(value)
                  end
                end

                class Worker
                  {% if false %}
                  include Feature
                  {% end %}
                end

                worker = Worker.new
                worker.work(1)
            """.trimIndent(),
            "work",
            "worker"
        )
        assertSame(DotCallResolution.Suppressed, edge.resolution)
    }

    fun testSuppressesExactIdentityWithMacroControlledReopening() {
        val call = resolveCall(
            """
                class Worker
                  def work(required)
                  end
                end

                {% if false %}
                class Worker
                  def work
                  end
                end
                {% end %}

                worker = Worker.new
                worker.work
            """.trimIndent(),
            "work",
            "worker"
        )

        assertSame(DotCallResolution.Suppressed, call.resolution)
    }

    fun testFindsIncludeNestedUnderLegalVisibilityWrapper() {
        val methods = assertMethods(
            """
                module Feature
                  def work(value)
                  end
                end

                class Worker
                  private include Feature
                end

                worker = Worker.new
                worker.work(1)
            """.trimIndent(),
            "work",
            "worker",
            "Worker",
            "Feature"
        )

        assertEquals("value", methods.methods.single().parameterList?.text)
    }

    fun testExtractsAllArgumentHolderFormsThroughResolverAPI() {
        val calls = resolveCalls(
            """
                class Service
                  def self.parenthesized(value)
                  end

                  def self.bare(value)
                  end

                  def self.argumentless
                  end
                end

                Service.parenthesized(1)
                Service.bare 1
                Service.argumentless
            """.trimIndent()
        )

        val parenthesized = select(calls, "parenthesized", "Service")
        assertTrue(parenthesized.descriptor.argumentHolder is CrystalCallArgs)
        assertEquals("(1)", parenthesized.descriptor.argumentHolder.text)

        val bare = select(calls, "bare", "Service")
        assertTrue(bare.descriptor.argumentHolder is CrystalBareArgumentList)
        assertEquals("1", bare.descriptor.argumentHolder.text)

        val argumentless = select(calls, "argumentless", "Service")
        assertSame(argumentless.descriptor.access, argumentless.descriptor.argumentHolder)
        assertEquals("argumentless", argumentless.descriptor.methodNameElement.text)

        calls.forEach { assertTrue(it.resolution is DotCallResolution.Methods) }
    }

    fun testExtractsMultilineCallsForEveryHolderAndInferredReceiver() {
        val source = """
            class Service
              def parenthesized(value)
              end

              def bare(value)
              end

              def argumentless
              end
            end

            service = Service.new
            service
              .parenthesized(1)
            service
              .bare 1
            service
              .argumentless
        """.trimIndent()
        val calls = resolveCalls(source)

        val parenthesized = select(calls, "parenthesized", "service")
        assertTrue(parenthesized.descriptor.argumentHolder is CrystalCallArgs)
        assertResolvedMethod(parenthesized, "Service")

        val bare = select(calls, "bare", "service")
        assertTrue(bare.descriptor.argumentHolder is CrystalBareArgumentList)
        assertResolvedMethod(bare, "Service")

        val argumentless = select(calls, "argumentless", "service")
        assertSame(argumentless.descriptor.access, argumentless.descriptor.argumentHolder)
        assertResolvedMethod(argumentless, "Service")
    }

    fun testResolvesLocalTypedParameterAndInstanceVariableReceivers() {
        val source = """
            class Service
              def local_call(local_arg)
              end

              def parameter_call(parameter_arg)
              end

              def ivar_call(ivar_arg)
              end
            end

            class Runner
              @service : Service

              def exercise(parameter : Service)
                local = Service.new
                local.local_call(1)
                parameter.parameter_call(1)
                @service.ivar_call(1)
              end
            end
        """.trimIndent()

        assertMethods(source, "local_call", "local", "Service", "Service")
        assertMethods(source, "parameter_call", "parameter", "Service", "Service")
        assertMethods(source, "ivar_call", "@service", "Service", "Service")
    }

    fun testResolvesTransparentParenthesizedReceiverIdentities() {
        val source = """
            class Service
              def local_call(value)
              end

              def parameter_call(value)
              end

              def ivar_call(value)
              end

              def self.class_call(value)
              end
            end

            class Runner
              @service : Service

              def exercise(parameter : Service)
                local = Service.new
                (local).local_call
                ((parameter)).parameter_call
                (@service).ivar_call
                (Service).class_call
              end
            end
        """.trimIndent()

        assertMethods(source, "local_call", "(local)", "Service", "Service")
        assertMethods(source, "parameter_call", "((parameter))", "Service", "Service")
        assertMethods(source, "ivar_call", "(@service)", "Service", "Service")
        assertMethods(source, "class_call", "(Service)", "Service", "Service")
    }

    fun testResolvesTransparentParenthesizedConstructorReceiver() {
        val methods = assertMethods(
            """
                class Service
                  def initialize(value)
                  end
                end
                (Service).new
            """.trimIndent(),
            "new",
            "(Service)",
            "Service",
            "Service"
        )

        assertEquals("value", methods.methods.single().parameterList?.text)
    }

    fun testResolvesGroupedQualifiedAndAbsoluteConstantPaths() {
        val source = """
            class Service
              def self.absolute_call(value)
              end

              def initialize(global_value)
              end
            end

            module Outer
              class Service
                def self.qualified_call(value)
                end

                def self.nested_call(value)
                end

                def initialize(outer_value)
                end
              end
            end

            (Outer::Service).qualified_call
            ((Outer::Service)).nested_call
            (::Service).absolute_call
            (Outer::Service).new
            ((Outer::Service)).new
            (::Service).new
        """.trimIndent()

        assertMethods(source, "qualified_call", "(Outer::Service)", "Outer::Service", "Outer::Service")
        assertMethods(source, "nested_call", "((Outer::Service))", "Outer::Service", "Outer::Service")
        assertMethods(source, "absolute_call", "(::Service)", "Service", "Service")
        assertEquals(
            "outer_value",
            assertMethods(source, "new", "(Outer::Service)", "Outer::Service", "Outer::Service")
                .methods.single().parameterList?.text
        )
        assertEquals(
            "outer_value",
            assertMethods(source, "new", "((Outer::Service))", "Outer::Service", "Outer::Service")
                .methods.single().parameterList?.text
        )
        assertEquals(
            "global_value",
            assertMethods(source, "new", "(::Service)", "Service", "Service")
                .methods.single().parameterList?.text
        )
    }

    fun testGroupedQualifiedRecordCollisionRemainsSuppressed() {
        val call = resolveCall(
            """
                module Outer
                  class Service
                    def initialize(value)
                    end
                  end

                  record Service, record_value : Int32
                end

                (Outer::Service).new
            """.trimIndent(),
            "new",
            "(Outer::Service)"
        )

        assertSame(DotCallResolution.Suppressed, call.resolution)
    }

    fun testGroupedCompositeReceiversRemainSuppressed() {
        val source = """
            class Service
              def run(value)
              end
            end
            class Other
              def run(value)
              end
            end
            service = Service.new
            other = Other.new
            (condition ? service : other).run
            (service || other).run
            (identity(service)).run
            (@@service).run
        """.trimIndent()

        assertSame(DotCallResolution.Suppressed, resolveCall(source, "run", "(condition ? service : other)").resolution)
        assertSame(DotCallResolution.Suppressed, resolveCall(source, "run", "(service || other)").resolution)
        assertSame(DotCallResolution.Suppressed, resolveCall(source, "run", "(identity(service))").resolution)
        assertSame(DotCallResolution.Suppressed, resolveCall(source, "run", "(@@service)").resolution)
    }

    fun testGroupedNonTransparentReceiversRemainSuppressed() {
        val source = """
            class Service
              def self.run
              end
            end
            class Other
              def self.run
              end
            end
            value = Service
            (value = Service).assignment
            [Service, Other].multiple
            (condition ? Service : Other).conditional
            ({{ receiver }}).macro
        """.trimIndent()

        assertSame(DotCallResolution.Suppressed, resolveCall(source, "assignment", "(value = Service)").resolution)
        assertSame(DotCallResolution.Suppressed, resolveCall(source, "multiple", "[Service, Other]").resolution)
        assertSame(DotCallResolution.Suppressed, resolveCall(source, "conditional", "(condition ? Service : Other)").resolution)
        assertSame(DotCallResolution.Suppressed, resolveCall(source, "macro", "({{ receiver }})").resolution)
    }

    fun testResolvesGenericConstructorAssignmentToRootIdentity() {
        val methods = assertMethods(
            """
                class Box(T)
                  def unpack(value)
                  end
                end
                value = Box(Int32).new
                value.unpack
            """.trimIndent(),
            "unpack",
            "value",
            "Box",
            "Box"
        )

        assertEquals("value", methods.methods.single().parameterList?.text)
    }

    fun testResolvesDirectGenericConstructorTargetToRootIdentity() {
        val source = """
            class Box(T)
              def initialize(first, second)
              end
            end
            Box(Int32).new
            Box(Int32).new()
            Box(Int32).new 1
        """.trimIndent()

        val calls = resolveCalls(source).filter {
            it.descriptor.methodName == "new" && it.descriptor.receiverText == "Box(Int32)"
        }
        assertEquals(3, calls.size)
        calls.forEach { call ->
            val resolution = call.resolution as? DotCallResolution.Methods
                ?: fail("Expected methods for Box(Int32).new, got ${call.resolution}")
            resolution as DotCallResolution.Methods
            assertEquals(ExactReceiverType("Box", "Box"), resolution.receiverType)
            assertEquals(listOf("Box"), resolution.methods.map(::declaringIdentity))
            assertEquals("first, second", resolution.methods.single().parameterList?.text)
        }
    }

    fun testDirectGenericConstructorUsesSelfNewPrecedence() {
        val methods = assertMethods(
            """
                class Box(T)
                  def self.new(first, second)
                  end

                  def initialize(first)
                  end
                end
                Box(Int32).new 1
            """.trimIndent(),
            "new",
            "Box(Int32)",
            "Box",
            "Box"
        )

        assertTrue(CrystalPsiUtils.isSelfMethod(methods.methods.single()))
        assertEquals("first, second", methods.methods.single().parameterList?.text)
    }

    fun testDirectGenericConstructorUsesImplicitConstructor() {
        val call = resolveCall(
            """
                class Box(T)
                end
                Box(Int32).new 1
            """.trimIndent(),
            "new",
            "Box(Int32)"
        )

        val resolution = call.resolution as? DotCallResolution.ImplicitConstructor
            ?: fail("Expected implicit constructor for Box(Int32), got ${call.resolution}")
        resolution as DotCallResolution.ImplicitConstructor
        assertEquals(ExactReceiverType("Box", "Box"), resolution.receiverType)
    }

    fun testCallValuedGenericConstructorArgumentRemainsSuppressed() {
        val call = resolveCall(
            """
                class Box(T)
                  def unpack(value)
                  end
                end
                value = Box(type_source()).new
                value.unpack
            """.trimIndent(),
            "unpack",
            "value"
        )

        assertSame(DotCallResolution.Suppressed, call.resolution)
    }

    fun testDirectCallValuedGenericConstructorTargetRemainsSuppressed() {
        val call = resolveCall(
            """
                class Box(T)
                end
                Box(type_source()).new
            """.trimIndent(),
            "new",
            "Box(type_source())"
        )

        assertSame(DotCallResolution.Suppressed, call.resolution)
    }

    fun testDirectCompositeGenericConstructorTargetsRemainSuppressed() {
        val source = """
            class Box(T)
            end
            Box(Int32 | String).new
            Box(Int32?).new
        """.trimIndent()
        val calls = resolveCalls(source).filter { it.descriptor.methodName == "new" }

        assertEquals(2, calls.size)
        calls.forEach { assertSame(DotCallResolution.Suppressed, it.resolution) }
    }

    fun testResolvesAssignmentInferredInstanceVariableReceiver() {
        val methods = assertMethods(
            """
                class Service
                  def run(value)
                  end
                end

                class Runner
                  def initialize
                    @service = Service.new
                  end

                  def execute
                    @service.run(1)
                  end
                end
            """.trimIndent(),
            "run",
            "@service",
            "Service",
            "Service"
        )

        assertEquals("value", methods.methods.single().parameterList?.text)
    }

    fun testInstanceReceiverNewIsNormalInstanceMethod() {
        val methods = assertMethods(
            """
                class Builder
                  def new(value)
                  end

                  def initialize(unrelated)
                  end
                end

                builder = Builder.new
                builder.new(1)
            """.trimIndent(),
            "new",
            "builder",
            "Builder",
            "Builder"
        )

        assertEquals("new", methods.methods.single().name)
        assertEquals("value", methods.methods.single().parameterList?.text)
    }

    fun testConstructorUsesInheritedSelfNewDefinitionSetBeforeInitialize() {
        val methods = assertMethods(
            """
                class Parent
                  def self.new(required)
                  end
                end

                class Child < Parent
                  def initialize
                  end
                end

                Child.new
            """.trimIndent(),
            "new",
            "Child",
            "Child",
            "Parent"
        )

        assertEquals("new", methods.methods.single().name)
        assertEquals("required", methods.methods.single().parameterList?.text)
    }

    fun testExtendedModuleNewCannotHideInheritedSelfNewConstructor() {
        val methods = assertMethods(
            """
                module FactoryMethods
                  def new(value)
                  end
                end

                class Parent
                  def self.new(value)
                  end
                end

                class Child < Parent
                  extend FactoryMethods
                end

                Child.new(1)
            """.trimIndent(),
            "new",
            "Child",
            "Child",
            "Parent"
        )

        assertTrue(CrystalPsiUtils.isSelfMethod(methods.methods.single()))
    }

    fun testConstructorFallsBackToAllInheritedInitializers() {
        val methods = assertMethods(
            """
                class Parent
                  def initialize(parent_value)
                  end
                end

                class Child < Parent
                  def initialize(child_value, optional = 1)
                  end
                end

                Child.new(1)
            """.trimIndent(),
            "new",
            "Child",
            "Child",
            "Child",
            "Parent"
        )

        assertEquals(listOf("initialize", "initialize"), methods.methods.map { it.name })
    }

    fun testConstructorReturnsCompleteDirectAndInheritedSelfNewSetOnly() {
        val methods = assertMethods(
            """
                class Parent
                  def self.new(parent_value)
                  end

                  def initialize(excluded_parent_initializer)
                  end
                end

                class Child < Parent
                  def self.new(child_value, optional = 1)
                  end

                  def initialize(excluded_child_initializer)
                  end
                end

                Child.new(1)
            """.trimIndent(),
            "new",
            "Child",
            "Child",
            "Child",
            "Parent"
        )

        assertEquals(listOf("new", "new"), methods.methods.map { it.name })
        assertTrue(methods.methods.all(CrystalPsiUtils::isSelfMethod))
    }

    fun testConstructorIgnoresUnresolvedExtendBeforeInitializeFallback() {
        val methods = assertMethods(
            """
                class Factory
                  extend MissingFactoryMethods

                  def initialize(value)
                  end
                end

                Factory.new(1)
            """.trimIndent(),
            "new",
            "Factory",
            "Factory",
            "Factory"
        )

        assertEquals("initialize", methods.methods.single().name)
        assertEquals("value", methods.methods.single().parameterList?.text)
    }

    fun testConstructorIgnoresMacroControlledExtendForSelfNewAndInitialize() {
        val selfNew = assertMethods(
            """
                module ConditionalFactoryMethods
                  def new(unrelated)
                  end
                end

                class ExplicitFactory
                  {% if false %}
                  extend ConditionalFactoryMethods
                  {% end %}

                  def self.new(value)
                  end
                end

                ExplicitFactory.new(1)
            """.trimIndent(),
            "new",
            "ExplicitFactory",
            "ExplicitFactory",
            "ExplicitFactory"
        )
        assertTrue(CrystalPsiUtils.isSelfMethod(selfNew.methods.single()))

        val initialize = assertMethods(
            """
                module ConditionalFactoryMethods
                  def new(unrelated)
                  end
                end

                class InitializedFactory
                  {% if false %}
                  extend ConditionalFactoryMethods
                  {% end %}

                  def initialize(value)
                  end
                end

                InitializedFactory.new(1)
            """.trimIndent(),
            "new",
            "InitializedFactory",
            "InitializedFactory",
            "InitializedFactory"
        )
        assertEquals("initialize", initialize.methods.single().name)
    }

    fun testConstructorIgnoresUnresolvedExtendForImplicitConstruction() {
        val call = resolveCall(
            """
                class EmptyFactory
                  extend MissingFactoryMethods
                end

                EmptyFactory.new
            """.trimIndent(),
            "new",
            "EmptyFactory"
        )

        assertTrue(call.resolution is DotCallResolution.ImplicitConstructor)
    }

    fun testNormalStaticMethodStillSuppressesUnresolvedExtend() {
        val call = resolveCall(
            """
                class Factory
                  extend MissingFactoryMethods

                  def self.build(value)
                  end
                end

                Factory.build(1)
            """.trimIndent(),
            "build",
            "Factory"
        )

        assertSame(DotCallResolution.Suppressed, call.resolution)
    }

    fun testAbstractClassConstructorIsSuppressed() {
        val call = resolveCall(
            """
                abstract class Base
                end

                Base.new
            """.trimIndent(),
            "new",
            "Base"
        )

        assertSame(DotCallResolution.Suppressed, call.resolution)
    }

    fun testImplicitConstructorSupportsClassesAndStructs() {
        val calls = resolveCalls(
            """
                class EmptyClass
                end

                struct EmptyStruct
                end

                EmptyClass.new
                EmptyStruct.new(1)
            """.trimIndent()
        )

        val classCall = select(calls, "new", "EmptyClass")
        val classResolution = classCall.resolution as DotCallResolution.ImplicitConstructor
        assertEquals(ExactReceiverType("EmptyClass", "EmptyClass"), classResolution.receiverType)

        val structCall = select(calls, "new", "EmptyStruct")
        val structResolution = structCall.resolution as DotCallResolution.ImplicitConstructor
        assertEquals(ExactReceiverType("EmptyStruct", "EmptyStruct"), structResolution.receiverType)
        assertTrue(structCall.descriptor.argumentHolder is CrystalCallArgs)
    }

    fun testStructConstructorUsesExplicitInitializeDefinitionSet() {
        val methods = assertMethods(
            """
                struct Point
                  def initialize(x, y)
                  end
                end

                Point.new(1, 2)
            """.trimIndent(),
            "new",
            "Point",
            "Point",
            "Point"
        )

        assertEquals("initialize", methods.methods.single().name)
        assertEquals("x, y", methods.methods.single().parameterList?.text)
    }

    fun testRecordFallbackOnlyMatchesExactSimpleRecordConstructorPath() {
        val fallback = resolveCall(
            """
                record Config, value : Int32
                Config.new(1)
            """.trimIndent(),
            "new",
            "Config"
        )
        val record = fallback.resolution as DotCallResolution.RecordFallback
        assertEquals("Config", record.receiverName)
        assertEquals("record Config, value : Int32", record.recordDefinition.text)

        val unrelatedIndexedClass = resolveCall(
            """
                module Other
                  class Config
                  end
                end

                record Config, value : Int32
                Config.new(1)
            """.trimIndent(),
            "new",
            "Config"
        )
        assertTrue(unrelatedIndexedClass.resolution is DotCallResolution.RecordFallback)

        val qualified = resolveCall(
            """
                module Other
                  class Config
                  end
                end

                record Config, value : Int32
                Other::Config.new(1)
            """.trimIndent(),
            "new",
            "Other::Config"
        )
        assertTrue(qualified.resolution is DotCallResolution.ImplicitConstructor)

        val missingQualifiedType = resolveCall(
            """
                record Config, value : Int32
                Other::Config.new(1)
            """.trimIndent(),
            "new",
            "Other::Config"
        )
        assertSame(DotCallResolution.Unresolved, missingQualifiedType.resolution)

        val collision = resolveCall(
            """
                class Config
                end

                record Config, value : Int32
                Config.new(1)
            """.trimIndent(),
            "new",
            "Config"
        )
        assertTrue(collision.resolution is DotCallResolution.RecordFallback)

        val inferred = resolveCall(
            """
                record Config, value : Int32
                config = Config.new(1)
                config.run
            """.trimIndent(),
            "run",
            "config"
        )
        assertSame(DotCallResolution.Suppressed, inferred.resolution)
    }

    fun testNestedRecordIdentityDoesNotHijackGlobalAndResolvesLexically() {
        val global = assertMethods(
            """
                class Config
                  def initialize(global_value)
                  end
                end

                module Other
                  record Config, nested_value : Int32
                end

                Config.new(1)
            """.trimIndent(),
            "new",
            "Config",
            "Config",
            "Config"
        )
        assertEquals("global_value", global.methods.single().parameterList?.text)

        val lexical = resolveCall(
            """
                class Config
                  def initialize(global_value)
                  end
                end

                module Other
                  record Config, nested_value : Int32

                  def self.exercise
                    Config.new(1)
                  end
                end
            """.trimIndent(),
            "new",
            "Config"
        )
        val record = lexical.resolution as DotCallResolution.RecordFallback
        assertEquals(
            "Other",
            CrystalPsiUtils.getEnclosingType(record.recordDefinition)?.let(CrystalPsiUtils::buildQualifiedName)
        )

        val nestedOnly = resolveCall(
            """
                module Other
                  record Config, nested_value : Int32
                end

                Config.new(1)
            """.trimIndent(),
            "new",
            "Config"
        )
        assertSame(DotCallResolution.Unresolved, nestedOnly.resolution)
    }

    fun testNearerNestedClassTakesPrecedenceOverGlobalRecord() {
        val methods = assertMethods(
            """
                record Config, record_value : Int32

                module Other
                  class Config
                    def initialize(class_value)
                    end
                  end

                  def self.exercise
                    Config.new
                  end
                end
            """.trimIndent(),
            "new",
            "Config",
            "Other::Config",
            "Other::Config"
        )

        assertEquals("class_value", methods.methods.single().parameterList?.text)
    }

    fun testMacroControlledRecordDoesNotProduceFallback() {
        val calls = resolveCalls(
            """
                {% if false %}
                record Config, hidden_value : Int32
                {% end %}

                Config.new

                {% if unresolved_flag %}
                record DynamicConfig, hidden_value : Int32
                {% end %}

                DynamicConfig.new
            """.trimIndent()
        )

        assertSame(DotCallResolution.Suppressed, select(calls, "new", "Config").resolution)
        assertSame(DotCallResolution.Suppressed, select(calls, "new", "DynamicConfig").resolution)
    }

    fun testQualifiedRecordSuppressionUsesExactQualifiedIdentity() {
        val exactClass = assertMethods(
            """
                module A
                  record Config, record_value : Int32
                end

                module B
                  class Config
                    def initialize(class_value)
                    end
                  end
                end

                B::Config.new(1)
            """.trimIndent(),
            "new",
            "B::Config",
            "B::Config",
            "B::Config"
        )
        assertEquals("class_value", exactClass.methods.single().parameterList?.text)

        val exactRecord = resolveCall(
            """
                module B
                  record Config, record_value : Int32
                end

                B::Config.new(1)
            """.trimIndent(),
            "new",
            "B::Config"
        )
        assertSame(DotCallResolution.Suppressed, exactRecord.resolution)
    }

    fun testExtractorPreservesMultilineQualifiedReceiverOwnership() {
        val selected = resolveCall(
            """
                module Outer
                  class Service
                    def self.run(value)
                    end
                  end
                end

                Outer :: Service
                  .run(1)
            """.trimIndent(),
            "run",
            "Outer::Service"
        )

        assertEquals("Outer::Service", selected.descriptor.receiverText)
        assertTrue(selected.descriptor.argumentHolder is CrystalCallArgs)
        val methods = selected.resolution as DotCallResolution.Methods
        assertEquals("Outer::Service", declaringIdentity(methods.methods.single()))
    }

    fun testCanonicalSignatureRetainsCallableDistinctOverloads() {
        val methods = assertMethods(
            """
                class Signatures
                  def self.pick(value)
                  end

                  def self.pick(*value)
                  end

                  def self.pick(**value)
                  end

                  def self.pick(external internal)
                  end

                  def self.pick(optional = 1)
                  end

                  def self.pick(&block)
                  end

                  def self.pick(*, named_only)
                  end
                end

                Signatures.pick(1)
            """.trimIndent(),
            "pick",
            "Signatures",
            "Signatures",
            "Signatures",
            "Signatures",
            "Signatures",
            "Signatures",
            "Signatures",
            "Signatures",
            "Signatures"
        )

        assertEquals(
            setOf("value", "*value", "**value", "external internal",
                "optional = 1", "&block", "*, named_only"),
            methods.methods.mapNotNull { it.parameterList?.text }.toSet()
        )
    }

    fun testStructuralSignatureSeparatesNamesKindsAndTypesWithoutConcatenationCollisions() {
        val methods = assertMethods(
            """
                class Signatures
                  def self.pick(public internal)
                  end

                  def self.pick(publicinternal)
                  end

                  def self.pick(value : First)
                  end

                  def self.pick(value : Second)
                  end

                  def self.pick(*value : First)
                  end

                  def self.pick(**value : First)
                  end

                  def self.pick(*, named : First)
                  end
                end

                Signatures.pick(1)
            """.trimIndent(),
            "pick",
            "Signatures",
            "Signatures",
            "Signatures",
            "Signatures",
            "Signatures",
            "Signatures",
            "Signatures",
            "Signatures",
            "Signatures"
        )

        assertEquals(
            setOf("public internal", "publicinternal", "value : First", "value : Second",
                "*value : First", "**value : First", "*, named : First"),
            methods.methods.mapNotNull { it.parameterList?.text }.toSet()
        )
    }

    fun testStructuralSignatureDistinguishesAnonymousTypedBlockRestrictions() {
        val methods = assertMethods(
            """
                class Signatures
                  def self.pick(& : Int32 -> String)
                  end

                  def self.pick(& : String -> String)
                  end
                end

                Signatures.pick
            """.trimIndent(),
            "pick",
            "Signatures",
            "Signatures",
            "Signatures",
            "Signatures"
        )

        assertEquals(
            setOf("& : Int32 -> String", "& : String -> String"),
            methods.methods.mapNotNull { it.parameterList?.text }.toSet()
        )
    }

    fun testEquivalentOptionalOverridesIgnoreDefaultExpressionContents() {
        val methods = assertMethods(
            """
                class Parent
                  def run(value = build_parent_default)
                  end
                end

                class Child < Parent
                  def run(value = build_child_default)
                  end
                end

                child = Child.new
                child.run
            """.trimIndent(),
            "run",
            "child",
            "Child",
            "Child"
        )

        assertEquals("build_child_default", methods.methods.single().parameterList?.parameterList?.single()?.expression?.text)
    }

    fun testLatestSameFileMethodAndRepeatedIncludeExtendEdgesWin() {
        val instanceMethods = assertMethods(
            """
                module Early
                  def work(early)
                  end
                end

                module Late
                  def work(late, optional = 1)
                  end
                end

                class Worker
                  include Early
                  include Late
                  include Early

                  def work(value = early_default)
                  end
                end

                class Worker
                  def work(value = late_default)
                  end
                end

                worker = Worker.new
                worker.work
            """.trimIndent(),
            "work",
            "worker",
            "Worker",
            "Worker",
            "Early",
            "Late"
        )
        assertEquals("late_default", instanceMethods.methods.first().parameterList?.parameterList?.single()?.expression?.text)

        val staticMethods = assertMethods(
            """
                module EarlyFactory
                  def build(early)
                  end
                end

                module LateFactory
                  def build(late, optional = 1)
                  end
                end

                class Factory
                  extend EarlyFactory
                  extend LateFactory
                  extend EarlyFactory
                end

                Factory.build(1)
            """.trimIndent(),
            "build",
            "Factory",
            "Factory",
            "EarlyFactory",
            "LateFactory"
        )
        assertEquals(listOf("early", "late, optional = 1"), staticMethods.methods.map { it.parameterList?.text })
    }

    fun testSuppressesIdenticalCrossFileReopeningSignaturesButRetainsDistinctOverloads() {
        myFixture.addFileToProject(
            "first.cr",
            """
                class CrossFile
                  def duplicate(value)
                  end

                  def distinct(value, extra)
                  end
                end
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "second.cr",
            """
                class CrossFile
                  def duplicate(value)
                  end

                  def distinct(value, extra, final)
                  end
                end
            """.trimIndent()
        )

        val duplicate = resolveCall(
            """
                value = CrossFile.new
                value.duplicate(1)
            """.trimIndent(),
            "duplicate",
            "value"
        )
        assertSame(DotCallResolution.Suppressed, duplicate.resolution)

        val distinct = assertMethods(
            """
                value = CrossFile.new
                value.distinct(1)
            """.trimIndent(),
            "distinct",
            "value",
            "CrossFile",
            "CrossFile",
            "CrossFile"
        )
        assertEquals(2, distinct.methods.size)
    }

    fun testSuppressesRelevantIncludePrecedenceAcrossDifferentFiles() {
        myFixture.addFileToProject(
            "first-edge.cr",
            """
                module FirstFeature
                  def work(first)
                  end
                end

                class CrossFileWorker
                  include FirstFeature
                end
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "second-edge.cr",
            """
                module SecondFeature
                  def work(second, optional = 1)
                  end
                end

                class CrossFileWorker
                  include SecondFeature
                end
            """.trimIndent()
        )

        val call = resolveCall(
            """
                worker = CrossFileWorker.new
                worker.work(1)
            """.trimIndent(),
            "work",
            "worker"
        )
        assertSame(DotCallResolution.Suppressed, call.resolution)
    }

    fun testQualifiedDeclarationUsesAllLexicalNamespacePrefixes() {
        val methods = assertMethods(
            """
                module Outer
                  class Service
                    def run(value)
                    end
                  end
                end

                module Other
                  class Service
                    def run(unrelated)
                    end
                  end
                end

                class Outer::Runner
                  def execute(service : Service)
                    service.run(1)
                  end
                end
            """.trimIndent(),
            "run",
            "service",
            "Outer::Service",
            "Outer::Service"
        )

        assertEquals("value", methods.methods.single().parameterList?.text)
    }

    fun testNearestIdenticalOverrideWinsWithoutHidingDistinctIncludedOverload() {
        val methods = assertMethods(
            """
                module Feature
                  def run(value)
                  end

                  def run(value, extra)
                  end
                end

                class Parent
                  def run(value)
                  end

                  def run(value, extra, final)
                  end
                end

                class Child < Parent
                  include Feature

                  def run(value)
                  end
                end

                child = Child.new
                child.run(1)
            """.trimIndent(),
            "run",
            "child",
            "Child",
            "Child",
            "Feature",
            "Parent"
        )

        assertEquals(
            listOf("value", "value, extra", "value, extra, final"),
            methods.methods.map { it.parameterList?.text }
        )
        assertEquals("Child", declaringIdentity(methods.methods.first()))
    }

    fun testMacroInterpolatedTargetAndReceiverAreSuppressed() {
        val calls = resolveCalls(
            """
                class Service
                  def self.run
                  end
                end

                Service.{{name}}
                {{receiver}}.run
            """.trimIndent()
        )

        val target = select(calls, "{{name}}", "Service")
        assertSame(DotCallResolution.Suppressed, target.resolution)
        val receiver = select(calls, "run", "{{receiver}}")
        assertSame(DotCallResolution.Suppressed, receiver.resolution)
    }

    fun testMacroInterpolationInsideArgumentsDoesNotSuppressExactTarget() {
        val methods = assertMethods(
            """
                class Service
                  def self.run(value)
                  end
                end

                Service.run({{value}})
            """.trimIndent(),
            "run",
            "Service",
            "Service",
            "Service"
        )

        assertEquals("value", methods.methods.single().parameterList?.text)
    }

    fun testExtractsQualifiedAndAbsoluteReceiverSpellingFromPsiChains() {
        val source = """
            module Outer
              class Service
                def self.qualified
                end

                def self.absolute
                end
              end
            end

            Outer :: Service
              .qualified
            ::Outer :: Service
              .absolute
        """.trimIndent()

        assertMethods(source, "qualified", "Outer::Service", "Outer::Service", "Outer::Service")
        assertMethods(source, "absolute", "::Outer::Service", "Outer::Service", "Outer::Service")
    }

    private fun assertMethods(
        source: String,
        methodName: String,
        receiverText: String,
        receiverIdentity: String,
        vararg declaringIdentities: String
    ): DotCallResolution.Methods {
        val selected = resolveCall(source, methodName, receiverText)
        assertEquals(methodName, selected.descriptor.methodNameElement.text)
        assertEquals(receiverText, selected.descriptor.receiverText)
        val resolution = selected.resolution as? DotCallResolution.Methods
            ?: fail("Expected methods for $receiverText.$methodName, got ${selected.resolution}")
        resolution as DotCallResolution.Methods
        assertEquals(ExactReceiverType(receiverIdentity.substringAfterLast("::"), receiverIdentity), resolution.receiverType)
        assertEquals(declaringIdentities.toList(), resolution.methods.map(::declaringIdentity))
        return resolution
    }

    private fun declaringIdentity(method: CrystalMethodDefinition): String =
        CrystalPsiUtils.getEnclosingType(method)?.let(CrystalPsiUtils::buildQualifiedName)
            ?: error("Expected enclosing type for ${method.text}")

    private fun assertResolvedMethod(call: ResolvedCall, identity: String) {
        assertEquals(call.descriptor.methodName, call.descriptor.methodNameElement.text)
        assertEquals("service", call.descriptor.receiverText)
        val resolution = call.resolution as DotCallResolution.Methods
        assertEquals(ExactReceiverType(identity, identity), resolution.receiverType)
        assertEquals(listOf(identity), resolution.methods.map(::declaringIdentity))
    }

    private fun resolveCall(source: String, methodName: String, receiverText: String): ResolvedCall =
        select(resolveCalls(source), methodName, receiverText)

    private fun resolveCalls(source: String): List<ResolvedCall> {
        val file = myFixture.configureByText("test.cr", source)
        assertNull(PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java))
        return PsiTreeUtil.findChildrenOfType(file, CrystalDotCallAccess::class.java)
            .mapNotNull { access ->
                val descriptor = CrystalCallExtractor.extractDotCall(access) ?: return@mapNotNull null
                ResolvedCall(descriptor, CrystalDotCallTargetResolver.resolve(access))
            }
    }

    private fun select(calls: List<ResolvedCall>, methodName: String, receiverText: String): ResolvedCall {
        val matches = calls.filter {
            it.descriptor.methodName == methodName && it.descriptor.receiverText == receiverText
        }
        assertEquals(
            "Expected exactly one $receiverText.$methodName call; calls were ${calls.map { "${it.descriptor.receiverText}.${it.descriptor.methodName}" }}",
            1,
            matches.size
        )
        return matches.single()
    }

    private data class ResolvedCall(
        val descriptor: DotCallDescriptor,
        val resolution: DotCallResolution
    )
}
