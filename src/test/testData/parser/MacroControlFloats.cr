# Float literals inside macro control tags (json/pull_parser_spec.cr)

{% for pair in [[Int8, 1_i8], [Float32, 1.0_f32], [Float64, 1.0]] %}
  {% type = pair[0] %}
  {% value = pair[1] %}
  it "reads {{ type }}" do
    pull = JSON::PullParser.new({{ value }}.to_json)
  end
{% end %}

{% if threshold == 0.5 %}
  slow
{% elsif rate > 1.5e3 %}
  fast
{% else %}
  unknown
{% end %}

{% unless scale == 1.0f32 %}
  scaled
{% end %}
