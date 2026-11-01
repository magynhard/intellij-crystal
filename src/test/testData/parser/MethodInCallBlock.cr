module LSP
  Enum.number InsertTextFormat do
    PlainText = 1
    Snippet = 2

    def to_json(json)
      json.number(value)
    end
  end

  struct CompletionItem
    property label : String
  end
end

define_methods { def inline_method; 1; end }

class AfterMethodBlocks
end
