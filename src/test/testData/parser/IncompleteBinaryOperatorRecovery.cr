def before_prefix
  value = !~ other
end

def after_prefix
  true
end

def before_postfix
  value = other !~
end

def after_postfix
  true
end

toplevel_broken = !~ other

def after_toplevel
  true
end
