# Macro-spliced dot-call names with following arguments (range/bsearch.cr)

{% for p in [64, 32] %}
  private def bsearch_internal(from : Float{{ p }}, to, exclusive, &block)
    bsearch_internal from, to.to_f{{ p }}, exclusive do |value|
      yield value
    end
  end

  private def bsearch_internal(from, to : Float{{ p }}, exclusive)
    bsearch_internal from.to_f{{ p }}, to, exclusive do |value|
      yield value
    end
  end
{% end %}

LibIntrinsics.bitreverse{{n}}(value)

# A spaced interpolation stays a bare argument, never a name fragment.
spaced = obj.method {{ x }}

# Tight `!`/`?` after the interpolation is part of the interpolation end token.
negated = LibGMP::UI.new!(self).to_u{{n}}!

def trailing : Int32
  1
end
