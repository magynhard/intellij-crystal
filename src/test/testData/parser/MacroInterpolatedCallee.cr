class Client
  {% for method in %w(get post) %}
    def {{method.id}}(path, headers : Headers? = nil)
      {{method.id}} path, form: body, headers: headers
    end

    def {{method.id}}_with_block(path)
      {{method.id}}(path) do |response|
        yield response
      end
    end
  {% end %}
end
