# Complete Guide & Specification: Embedded Crystal (ECR)

Dieses Dokument enthält die vollständige technische Spezifikation für **ECR (Embedded Crystal)** im Vergleich zu Rubys ERB sowie alle syntaktischen Anwendungsfälle mit präzisen Codebeispielen.

---

## 1. Introduction & Core Concepts

**Embedded Crystal (ECR)** is a templating engine for the Crystal programming language that mixes raw text (typically HTML, XML, or plain text) with executable Crystal code. Unlike Ruby's ERB, which is evaluated dynamically at runtime, ECR templates are completely compiled into highly efficient Crystal code at **compile-time**.

The compilation process maps the text and tags directly onto an `IO` object write stream. This results in near-zero runtime overhead and seamless type-safety enforcement.

---

## 2. Tag Syntax Reference Matrix

The following matrix outlines all valid ECR tag configurations and their deterministic compiler behaviors.

| Tag Pattern | Classification | Compiler Semantic Behavior |
| :--- | :--- | :--- |
| `<% code %>` | **Control Block** | Executes the inner Crystal code without rendering any output directly. Used for loops, conditionals, and assignments. |
| `<%= expression %>` | **Output Block** | Evaluates the expression, invokes `to_s(io)` on the result, and streams it straight to the output buffer. |
| `<%- code %>` | **Trim Left** | Control block that suppresses and strips all leading whitespace (spaces/tabs) on the exact same line prior to the tag. |
| `<% code -%>` | **Trim Right** | Control block that suppresses and strips the immediate subsequent trailing newline character following the tag. |
| `<%# comment %>` | **Comment** | Completely ignored by the compiler. It is stripped out during macro expansion and produces zero code or whitespace output. |
| `<%% text %>` | **Literal Escape** | Escapes the ECR compiler engine. It outputs the raw literal string `<% text %>` to the resulting text. |

---

## 3. Comprehensive Case Explanations & Code Examples

### Case 3.1: Control Expressions (`<% ... %>`)
Used to embed internal Crystal control-flow logic such as `if/else` branches and `each` loops. Any valid non-expression code can go here. Loops must be terminated explicitly with an `<% end %>` block.

#### Input Template (`users.ecr`)
```html
<% if users.empty? %>
  <p>No users found.</p>
<% else %>
  <ul>
    <% users.each do |user| %>
      <li>User profile slot</li>
    <% end %>
  </ul>
<% end %>
```

### Case 3.2: Output Expressions (`<%= ... %>`)
Evaluates the code block and appends the result to the destination `IO`. The expression must return an object that responds to `to_s`. Note that by default, standard ECR does **not** auto-escape HTML characters (unless wrapped by a custom framework wrapper like Kemal's Kilt).

#### Input Template
```html
<h1>Welcome, <%= user.name.capitalize %>!</h1>
<p>Your balance is: <%= user.balance + 10.0 %></p>
```

### Case 3.3: Precise Whitespace Control (The Dashes)
Crystal's ECR implementation handles whitespace trimming using precise placement rules for the minus sign (`-`). This is highly essential for generating strictly aligned text payloads like YAML, JSON, or source code.

* `<%-` : Looks backwards and deletes horizontal whitespace up to the beginning of the line.
* `-%>` : Looks forwards and deletes the single trailing carriage return/newline byte.

#### Input Template (Without Trimming)
```text
List:
<% [1, 2].each do |i| %>
  - Item <%= i %>
<% end %>
```

#### Rendered Output (With empty newlines from control lines)
```text
List:

  - Item 1

  - Item 2
```

#### Input Template (With Precise Trimming)
```text
List:
<% [1, 2].each do |i| -%>
  - Item <%= i %>
<%- end %>
```

#### Rendered Output (Perfectly cleaned)
```text
List:
  - Item 1
  - Item 2
```

### Case 3.4: Commenting Code (`<%# ... %>`)
Provides a safe way to write inline template annotations or comment out sections of template code. The compiler completely eradicates these nodes from the generation pipeline.

#### Input Template
```html
<div class="profile">
  <%# TODO: Add avatar element here once migration is finished %>
  <h3><%= user.name %></h3>
</div>
```

### Case 3.5: Escaping ECR Blocks (`<%% ... %>`)
When building documentation or nested template generators, you may need to output a literal ECR block sequence text instead of interpreting it. Doubling the initial percentage symbol triggers this.

#### Input Template
```text
To print a name, write: <%%= user.name %>
```

#### Rendered Output
```text
To print a name, write: <%= user.name %>
```

### Case 3.6: HTML Template with Embedded ECR
The most common use case for ECR is generating HTML views in web frameworks like Kemal or Lucky. ECR tags can be freely interspersed with HTML markup, enabling dynamic page generation with full Crystal type safety at compile time.

#### Input Template (`users.html.ecr`)
```html
<!DOCTYPE html>
<html>
<head>
  <title>User List</title>
</head>
<body>
  <h1>Users</h1>

  <% if users.empty? %>
    <p class="empty-state">No users registered yet.</p>
  <% else %>
    <table>
      <thead>
        <tr>
          <th>Name</th>
          <th>Role</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        <% users.each do |user| %>
          <tr>
            <td><%= user.name %></td>
            <td><span class="badge badge-<%= user.role.downcase %>"><%= user.role %></span></td>
            <td><a href="/users/<%= user.id %>/edit">Edit</a></td>
          </tr>
        <% end %>
      </tbody>
    </table>
  <% end %>
</body>
</html>
```

#### Rendered HTML Output (with 2 users)
```html
<!DOCTYPE html>
<html>
<head>
  <title>User List</title>
</head>
<body>
  <h1>Users</h1>

    <table>
      <thead>
        <tr>
          <th>Name</th>
          <th>Role</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
          <tr>
            <td>Alice</td>
            <td><span class="badge badge-admin">Admin</span></td>
            <td><a href="/users/1/edit">Edit</a></td>
          </tr>
          <tr>
            <td>Bob</td>
            <td><span class="badge badge-user">User</span></td>
            <td><a href="/users/2/edit">Edit</a></td>
          </tr>
      </tbody>
    </table>
</body>
</html>
```

### Case 3.7: Layout Template with Yield
ECR templates can be composed using a layout pattern, where the outer wrapping template includes an `<%= yield %>` call and child templates render into it via `ECR.render`.

#### Layout Template (`layout.ecr`)
```html
<!DOCTYPE html>
<html>
<head>
  <title><%= @page_title %></title>
  <link rel="stylesheet" href="/styles.css">
</head>
<body>
  <header>
    <h1><%= @site_name %></h1>
  </header>
  <main>
    <%= content %>
  </main>
  <footer>
    <p>&copy; 2026 My Crystal App</p>
  </footer>
</body>
</html>
```

#### Driver Code
```crystal
class PageRenderer
  def render_page(title : String, content : String) : String
    @page_title = title
    @site_name = "My Crystal App"
    ECR.render("layout.ecr")
  end
end
```

---

## 4. Compile-Time Engine Integration

To invoke the ECR parser compiler, Crystal provides a native standard library module under the `ECR` namespace. There are two primary macro primitives utilized:

1. `ECR.embed(filename, io)`: Embeds the parsed file direct into the designated stream `io` at the current execution scope.
2. `ECR.render(filename)`: Syntactic sugar that creates an isolated string allocation context, rendering the template and returning a standalone `String`.

### Complete Executable Crystal Driver Example
The example below demonstrates how variable scoping naturally crosses seamlessly over into the template because the macro expands inline directly inside the context method.

```crystal
require "ecr"

class User
  property name : String
  property roles : Array(String)

  def initialize(@name, @roles)
  end
end

class ViewRenderer
  def initialize(@user : User)
  end

  # Using ECR.render to return a complete String
  def render_profile : String
    ECR.render("profile.ecr")
  end
end

# Application Bootstrap Execution
user = User.new("Alice", ["Admin", "Developer"])
renderer = ViewRenderer.new(user)

puts renderer.render_profile
```

#### Accompanying template file: `profile.ecr`
```text
User Profile Report:
====================
Name: <%= @user.name %>
Roles:
<% @user.roles.each do |role| -%>
  * <%= role %>
<%- end %>
```

---

## 5. Structural & Nuance Deviations from Ruby's ERB

While surface syntax elements map nearly 1:1, several fundamental technical and nuance disparities exist under the hood:

### 5.1 Whitespace Management Definition
* **Ruby (ERB):** Often uses `<% ... -%>` to trim the trailing newline, but leading indentation trimming with `<%- ... %>` requires specific configuration flags enabled in the ERB constructor (like `trim_mode: '-'`).
* **Crystal (ECR):** Strikingly strict and predictable. `<%-` strictly looks backwards to trim horizontal indentation on that line, and `-%>` strictly looks forward to devour the trailing newline byte. No extra configuration is needed.

### 5.2 Compilation Strictness vs. Runtime Evaluation
* **Ruby (ERB):** Templates are parsed and evaluated at runtime. If a template calls a missing method or contains a typo, the application fails lazily during execution with a `NoMethodError` or `SyntaxError`.
* **Crystal (ECR):** ECR files are read during compilation. If an ECR template invokes a method that does not exist on the current calling context scope, Crystal throws a **static compiler error** and refuses to compile the binary.

### 5.3 Execution Context & Bindings
* **Ruby (ERB):** Explicitly uses execution abstraction tokens called `Binding` objects (e.g., `ERB.new(str).result(binding)`). This allows Ruby to pass isolated local variable hashes into the template evaluator dynamically.
* **Crystal (ECR):** ECR has no concept of runtime dynamic execution bindings due to compiled type checking. Because `ECR.embed` or `ECR.render` are macros, they expand the layout code directly inline. The template inherits the exact lexical scope (local variables, instance variables like `@user`) of the block it is placed in.

---

## 6. IntelliJ Platform Integration

This section specifies how the IntelliJ Crystal plugin handles `.ecr` files and their variants within the IDE.

### 6.1 File Type Registration

The plugin registers the following file extensions as the **Embedded Crystal** file type:

| Extension | Example Filenames | Description |
| :--- | :--- | :--- |
| `.ecr` | `template.ecr`, `layout.ecr`, `footer.ecr` | Standalone ECR template file. The base language is Crystal; **HTML is always assigned as the template data language** (implicit HTML parser activation). |
| `.html.ecr` | `index.html.ecr`, `show.html.ecr`, `users.html.ecr` | HTML template with embedded Crystal. The Crystal language engine handles `<% %>` tags while the template data language is set to **HTML**, enabling HTML syntax highlighting, completion, and structure view. |

Both extensions are registered under a single `EmbeddedCrystal` language and `EmbeddedCrystalFileType`.

**Extension detection behavior:**

| Pattern | How it matches |
| :--- | :--- |
| `*.ecr` | Caught by `extensions="ecr"` — `.ecr` is the final extension. **HTML template data language is active.** |
| `*.html.ecr` | Caught by `extensions="ecr"` — IntelliJ uses the last dot-separated component as the extension, so `.html.ecr` resolves to extension `.ecr`. No special handling needed. |

#### Implementation Sketch — `plugin.xml` Registration for `.ecr` and `.html.ecr`

```xml
<fileType
    name="Embedded Crystal"
    implementationClass="de.magynhard.crystal.ecr.EmbeddedCrystalFileType"
    fieldName="INSTANCE"
    language="EmbeddedCrystal"
    extensions="ecr"/>
```

#### Dedicated Language Definition

ECR templates get their own `EmbeddedCrystalLanguage` (distinct from the `Crystal` language). The parser for this language is purpose-built — it only understands the ECR tag syntax (`<%`, `<%=`, `<%-`, `-%>`, `<%#`, `<%%`). Inside raw text regions between tags, the lexer delegates to a **template data language lexer** (e.g., HTML) when configured, or treats the text as plain text otherwise.

This mirrors how RubyMine defines `ERBLanguage` (not the same as `RubyLanguage`). The Crystal-language features (type inference, references, inspections) do **not** apply to ECR files — only the Crystal code inside `<% %>` blocks is parsed and highlighted as Crystal.

### 6.2 File Icon

Files matching the `.ecr` and `.html.ecr` extensions receive the **`<%>` icon**, analogous to RubyMine's ERB file icon.

| File Type | Icon Source | Visual |
| :--- | :--- | :--- |
| `.ecr` / `.html.ecr` | `EmbeddedCrystalIcons.FILE` — custom `<%>` glyph | `<%>` |

The icon is a triangular `<%>` glyph that mirrors the ERB icon used by RubyMine for `.erb` files. It conveys "this is a template file with embedded code tags" at a glance.

#### Implementation Sketch — `EmbeddedCrystalIcons.kt`

```kotlin
object EmbeddedCrystalIcons {
    @JvmField
    val FILE = IconLoader.getIcon("/icons/embedded_crystal.svg", EmbeddedCrystalIcons::class.java)
}
```

The icon is registered via the existing `CrystalIconProvider` (or a new `EmbeddedCrystalIconProvider`), which checks for `.ecr` and its variants:

```kotlin
class CrystalIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element is PsiFile) {
            val name = element.name
            return when {
                name.endsWith(".html.ecr") || name.endsWith(".ecr") -> EmbeddedCrystalIcons.FILE
                name.endsWith("_spec.cr") -> CrystalIcons.SPEC_FILE
                else -> null
            }
        }
        return null
    }
}
```

Or alternatively registered directly in `plugin.xml` via an `<iconProvider>` extension.

### 6.3 Template Data Language — Implicit HTML Parser

For **all `.ecr` files** (both `.ecr` and `.html.ecr`), the IDE implicitly activates the **HTML parser** as the template data language. This means:

- HTML tags inside the ECR template receive full HTML syntax highlighting (elements, attributes, attribute values, entities, etc.)
- HTML code completion works inside tag regions (attribute names, tag names, CSS class completion with configured framework support)
- HTML structure view shows the HTML element tree
- HTML inspections (e.g., unclosed tags, duplicate IDs) are active on the raw text portions
- The `<% %>` and `<%= %>` ECR tags are highlighted using the Crystal/ECR highlighting rules within their respective tag boundaries
- Code folding for HTML elements works between ECR tags

This is achieved via the **Template Language** infrastructure: `EmbeddedCrystalLanguage` implements the `TemplateLanguage` marker interface; `EcrHtmlFile` uses a `TemplateDataElementType` with `EmbeddedCrystalLanguage` as the lexer source to re-parse HTML regions with the HTML parser; and `TemplateLanguageStructureViewBuilder` automatically combines the base language (ECR tags) and template data language (HTML) into a unified Structure View.

#### Implementation Sketch — `TemplateDataElementType`

```kotlin
object EmbeddedCrystalTemplateDataElementType :
    TemplateDataElementType(
        "ECR_TEMPLATE_DATA",
        EmbeddedCrystalLanguage,
        EmbeddedCrystalTypes.ECR_OUTER,
        OuterLanguageElementType("ECR_OUTER", EmbeddedCrystalLanguage)
    )
```

The `TemplateDataElementType` constructor takes:
1. Debug name (`"ECR_TEMPLATE_DATA"`)
2. **Template language** (`EmbeddedCrystalLanguage`) — whose lexer splits the file into `ECR_OUTER` (data) and ECR tag regions
3. **Template element type** (`ECR_OUTER`) — token marking data regions in the template's token stream
4. **Outer element type** (`OuterLanguageElementType("ECR_OUTER", EmbeddedCrystalLanguage)`) — wraps Crystal content when it appears inside data regions

#### Behavior Summary

| File Pattern | File Type | Template Data Language | Example |
| :--- | :--- | :--- | :--- |
| `*.ecr` | Embedded Crystal | **HTML** | `footer.ecr` |
| `*.html.ecr` | Embedded Crystal | **HTML** | `index.html.ecr` |

For future template data languages (.xml, .json, .css), additional patterns can be added to the `FileViewProvider` logic or a `TemplateDataLanguagePatterns` registration can be introduced following the same scheme.

#### Visual Structure Example — `index.html.ecr`

The following example shows how HTML and Crystal interact in the IDE's highlighting layers:

```html
<!-- HTML comment — highlighted as HTML -->
<!DOCTYPE html>
<html>
<head>
  <title><%= @page_title %></title>
  <!--              ^^^^^^^^^^^^ Crystal expression — highlighted as Crystal -->
</head>
<body>
  <% if @items.empty? %>
  <!-- ^^ Crystal control keyword — highlighted as Crystal keyword -->
  <!--     ^^^^^^^^^^^^^^^^ Crystal expression — highlighted as Crystal -->
    <p class="empty">No items.</p>
    <!-- ^ HTML tag — highlighted as HTML -->
    <!--     ^^^^^^^^^^^^^ HTML attribute — highlighted as HTML -->
  <% end %>
  <!-- ^^ Crystal end keyword — highlighted as Crystal -->
</body>
</html>
```

### 6.4 Extension Point Registration Summary

The following extension points in `plugin.xml` are required:

| Extension Point | Registration | Purpose |
| :--- | :--- | :--- |
| `com.intellij.fileType` | `EmbeddedCrystalFileType` with `extensions="ecr"` | Associates `.ecr` and `.html.ecr` with the Embedded Crystal file type |
| `com.intellij.lang.parserDefinition` | `EmbeddedCrystalParserDefinition` for `EmbeddedCrystalLanguage` | Provides the ECR lexer and parser to the IDE |
| `com.intellij.lang.syntaxHighlighterFactory` | `EmbeddedCrystalSyntaxHighlighterFactory` for `EmbeddedCrystalLanguage` | Provides ECR syntax highlighting (ECR tags + template data language delegation) |
| `com.intellij.editorHighlighterProvider` | `EcrEditorHighlighterProvider` for filetype="Embedded Crystal" implementationClass="...EcrEditorHighlighterProvider" | Provides layered editor highlighter (ECR tags + Crystal + HTML layers) |
| `com.intellij.lang.fileViewProviderFactory` | `EmbeddedCrystalFileViewProviderFactory` for `EmbeddedCrystal` | Provides the multi-language `FileViewProvider` (ECR + HTML) |
| `com.intellij.lang.psiStructureViewFactory` | `EcrStructureViewFactory` for `EmbeddedCrystal` | Provides 3-section Structure View (ECR + HTML + Crystal) |
| `com.intellij.iconProvider` | `CrystalIconProvider` (extended) or new `EmbeddedCrystalIconProvider` | Returns the `<%>` icon for `.ecr` file variants |

This architecture mirrors how RubyMine handles `.erb` files: a dedicated template language (`ERBLanguage`), a file type (`ERBFileType`), a template data language mechanism (`TemplateDataElementType` + `TemplateLanguageStructureViewBuilder`), and a recognizable `<%>` icon.