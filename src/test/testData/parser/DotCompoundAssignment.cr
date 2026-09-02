server = config.server ||= HTTP::Server.new(config.handlers)
config.client.timeout &&= fallback
state.value += increment
