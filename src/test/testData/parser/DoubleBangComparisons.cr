# Prefix `!` at prefix precedence: NOT wraps a prefix chain only
# (compiler parse_prefix), so comparisons rebind as `(!a) == b`.
value = !!candidate
same = !!left != !!right
equality = def_metadata.yields == !!signature.block
negate = other && !!candidate

# CC-faithful rebinding: `!a == b` is `(!a) == b`.
paren = !a == b
both = x != !!y

def after_comparisons
end
