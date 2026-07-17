# `property` / `getter` / `setter` where the property name is a reserved word
# or is wrapped in parentheses.

property next : Crystal::EventLoop

getter(event_loop : Crystal::EventLoop) do
  @event_loop
end
