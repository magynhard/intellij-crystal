def build(fallback)
  value = <<-TEXT rescue puts fallback
    body line
  TEXT
  other = <<-OTHER if enabled
    other body
  OTHER
end

def after_heredoc_modifiers
end
