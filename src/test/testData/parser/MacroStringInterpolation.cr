{% for op in ["sizeof", "alignof"] %}
  it "errors with {{ op }}" do
    assert_error %(\{{ {{ op }}({{ type }}) }}), "msg {{ op }}"
  end
{% end %}

{% for type in TYPES %}
  it "checks {{ type }}" do
    assert_error <<-CRYSTAL, "msg {{ op == "sizeof" ? "size" : "other" }}"
      struct Foo
      end
      CRYSTAL
  end
{% end %}

{% if FLAG %}
  x = "plain {{ op }} text"
{% end %}

y = "plain {{ op }} text"
