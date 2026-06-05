# Top-level {% begin %} / {% end %} blocks for compile-time code

{% begin %}
  COMPILE_TIME_CONST = {{ 1 + 2 + 3 }}
{% end %}

{% if flag?(:linux) %}
  PLATFORM = "linux"
{% elsif flag?(:darwin) %}
  PLATFORM = "macos"
{% else %}
  PLATFORM = "unknown"
{% end %}
