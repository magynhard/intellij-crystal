# `.!` pseudo-method suffix shapes (colorize.cr, semantic new.cr, location_spec.cr).
negated = value.!
double_negated = value.!.!
parenthesized = value.!()
multiline_call = value.!(
)

# ampersand block-pass shorthand chains with the suffix:
std_zone = location.zones.find(&.dst?.!).should_not be_nil
ENV["TERM"]? != "dumb" && !ENV["NO_COLOR"]?.try(&.empty?.!)
has_default_self_new = self_new_methods.any?(&.has_any_args?.!)

def after_bang_suffixes
end
