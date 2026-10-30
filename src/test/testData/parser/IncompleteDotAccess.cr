class Wrapper
  def consume
    state = token.delimiter_state
    loop do
      token = next_token
      token.
      break unless token
      state = state.marker
    end
  end

  def trailing_access_only
    receiver.
  end
end
