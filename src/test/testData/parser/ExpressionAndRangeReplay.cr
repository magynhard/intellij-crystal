# Ordinary and question expressions
ordinary = value
predicate = value ?
choice = condition ? first : second
nested = outer ? first : inner ? second : third

# Ranges with omitted and bounded endpoints
startless_inclusive = ..last
startless_exclusive = ...last
endless_inclusive = [first..]
endless_exclusive = [first...]
bounded_inclusive = first..last
bounded_exclusive = first...last

# Bare ranges in parenthesis-free call arguments
consume ..last
consume ...last
consume first.., other
consume first..., other
consume first..last
consume first...last

# Synthetic nested Spec DSL with dotted assertions
describe Parser do
  context "configured input" do
    configure(parser: Parser.new) do
      it "parses source" do
        parse(source).result.should eq(expected)
        parse(source).errors.should be_empty
      end
    end
  end
end
