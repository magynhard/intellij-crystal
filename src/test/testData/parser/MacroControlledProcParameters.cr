def partial(*args : *U) forall U
  {% begin %}
    {% remaining = (T.size - U.size) %}
    ->(
        {% for i in 0...remaining %}
          arg{{i}} : {{T[i + U.size]}},
        {% end %}
      ) {
      call(
        *args,
        {% for i in 0...remaining %}
          arg{{i}},
        {% end %}
      )
    }
  {% end %}
end

def after_proc
end
