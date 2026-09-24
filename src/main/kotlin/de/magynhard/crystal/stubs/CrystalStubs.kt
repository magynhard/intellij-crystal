package de.magynhard.crystal.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import de.magynhard.crystal.psi.*

// ==================== Stub Interfaces ====================

interface CrystalNamedStub {
    val name: String?
}

// ==================== Class Stub ====================

class CrystalClassDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
    /** For qualified names like `class Foo::Bar`, stores the namespace prefix ("Foo"). */
    val enclosingNamespace: String? = null
) : StubBase<CrystalClassDefinition>(parent, elementType), CrystalNamedStub

// ==================== Module Stub ====================

class CrystalModuleDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
    /** For qualified names like `module Foo::Bar`, stores the namespace prefix ("Foo"). */
    val enclosingNamespace: String? = null
) : StubBase<CrystalModuleDefinition>(parent, elementType), CrystalNamedStub

// ==================== Struct Stub ====================

class CrystalStructDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
    /** For qualified names like `struct Foo::Bar`, stores the namespace prefix ("Foo"). */
    val enclosingNamespace: String? = null
) : StubBase<CrystalStructDefinition>(parent, elementType), CrystalNamedStub

// ==================== Enum Stub ====================

class CrystalEnumDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
    /** For qualified names like `enum Foo::Bar`, stores the namespace prefix ("Foo"). */
    val enclosingNamespace: String? = null
) : StubBase<CrystalEnumDefinition>(parent, elementType), CrystalNamedStub

// ==================== Method Stub ====================

class CrystalMethodDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
    val isSelfMethod: Boolean,
    /** Explicit receiver (`def Time::Location.new`) or enclosing record; null for plain lexical ownership. */
    val ownerQualifiedName: String? = null,
) : StubBase<CrystalMethodDefinition>(parent, elementType), CrystalNamedStub

// ==================== Macro Stub ====================

class CrystalMacroDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<CrystalMacroDefinition>(parent, elementType), CrystalNamedStub

// ==================== Lib Stub ====================

class CrystalLibDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<CrystalLibDefinition>(parent, elementType), CrystalNamedStub

// ==================== Annotation Stub ====================

class CrystalAnnotationDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<CrystalAnnotationDefinition>(parent, elementType), CrystalNamedStub

// ==================== Alias Stub ====================

class CrystalAliasDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<CrystalAliasDefinition>(parent, elementType), CrystalNamedStub

// ==================== Constant Stub ====================

class CrystalConstantAssignmentStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
    /** Qualified owner (`Foo::Bar`, `LibC`); null for file top-level constants. */
    val ownerQualifiedName: String? = null,
    /** `private`/`protected` constants are file-scoped, exactly like the compiler. */
    val isPrivate: Boolean = false,
) : StubBase<CrystalConstantAssignment>(parent, elementType), CrystalNamedStub
