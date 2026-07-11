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