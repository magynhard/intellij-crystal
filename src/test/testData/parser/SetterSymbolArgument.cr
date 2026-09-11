class Reader
  delegate :color?, :color=, :lines, :output, :output=, to: @editor
  delegate :word_delimiters, :word_delimiters=, to: @editor

  def close
  end
end
