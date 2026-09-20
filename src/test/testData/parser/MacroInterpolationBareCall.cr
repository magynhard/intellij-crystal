# Macro interpolation as a bare call argument before a do block
# (std/float_printer/shortest_spec.cr)

private macro it_converts_to_s(v)
  it {{ "converts #{v} to \"#{v.id.gsub(/_f32$/, "")}\"" }} do
    assert_prints {{ v }}.to_s, "{{ v.id.gsub(/_f32$/, "") }}"
  end
end

private macro it_converts_to_s(v, str)
  it {{ "converts #{v.id.gsub(/^hexfloat\("(.*)"\)$/, "\\1")} to #{str}" }} do
    assert_prints ({{ v }}).to_s, {{ str }}
  end
end

def trailing
  1
end
