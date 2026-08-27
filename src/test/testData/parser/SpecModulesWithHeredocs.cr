module SpecKit
  module Checks
    describe CompositeRule do
      rule = CompositeRule.new

      describe "#check" do
        it "detects raise with exception class" do
          assert_finding(rule, <<-CRYSTAL)
            def process(id : Int32) : String
              raise ArgumentError.new("invalid id")
            end
          CRYSTAL
        end

        it "detects raise with string" do
          assert_finding(rule, <<-CRYSTAL)
            def lookup(key : String) : String?
              raise "not found"
            end
          CRYSTAL
        end

        it "does not flag raise without args" do
          assert_no_finding(rule, <<-CRYSTAL)
            def cleanup
              raise
            end
          CRYSTAL
        end

        it "does not flag raise in top-level code" do
          assert_no_finding(rule, <<-CRYSTAL)
            raise "fatal error"
          CRYSTAL
        end
      end

      describe "#id" do
        it "returns CAT-045" do
          rule.id.should eq("CAT-045")
        end
      end

      describe "#severity" do
        it "returns info" do
          rule.severity.should eq("info")
        end
      end
    end
  end
end
