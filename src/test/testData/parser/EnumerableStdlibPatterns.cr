module Enumerable(T)
  abstract def each(& : T ->)

  def chunks(&block : T, T -> U) forall U
    res = [] of Tuple(typeof(first_key(self, block)), Array(T))
    res
  end

  def flat_map(& : T -> _)
    ary = [] of typeof(flat_map_type(yield element_type(self)))
    each do |e|
      case v = yield e
      when Array, Iterator
        ary.concat(v)
      else
        ary.push(v)
      end
    end
    ary
  end

  def reduce(&)
    memo = uninitialized typeof(reduce(element_type(self)) { |acc, i| yield acc, i })
    found = false
    each do |elem|
      memo = found ? (yield memo, elem) : elem
      found = true
    end
    found ? memo : raise EmptyError.new
  end

  def self.zip(main, others : U, &) forall U
    {% begin %}
      {% for type, type_index in U %}
        other{{type_index}} = others[{{type_index}}]
      {% end %}

      main.each_with_index do |elem, i|
        {% for type, type_index in U %}
          if other{{type_index}}.is_a?(Indexable)
            other_elem{{type_index}} = other{{type_index}}[i]
          else
            other_elem{{type_index}} = iter{{type_index}}.not_nil!.next
          end
        {% end %}

        yield({
          elem,
          {% for _t, type_index in U %}
            other_elem{{type_index}},
          {% end %}
        })
      end
    {% end %}
  end

  private struct Reflect(X)
    def self.type
      {% if X.union? %}
        {{
          raise("Union types are not supported " +
                "by this method.")
        }}
      {% else %}
        X
      {% end %}
    end
  end

  def accumulate(&block : T, T -> T) : Array(T)
    to_a(&.as(T))
  end

  def to_a : Array(T)
    to_a(&.as(T))
  end

  def key_type(ary, block)
    ary.each do |item|
      ::raise "" if key.is_a?(Drop.class)
      return key
    end
    ::raise ""
  end
end
