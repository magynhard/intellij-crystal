# Visibility Modifiers Examples
# private, protected — method and constant visibility

# Private methods
class Account
  def balance : Int32
    calculate_balance
  end

  private def calculate_balance : Int32
    @transactions.sum
  end

  private def validate_amount(amount : Int32)
    raise "Invalid" if amount <= 0
  end
end

# Private with multiple methods (modifier as prefix)
class Service
  private def connect
  end

  private def disconnect
  end

  private def retry_connection
  end
end

# Private block form (applies to all methods within)
class Parser
  def parse(input : String)
    tokens = tokenize(input)
    build_ast(tokens)
  end

  private

  def tokenize(input : String)
    input.split
  end

  def build_ast(tokens : Array(String))
    # ...
  end
end

# Protected methods (accessible from same type and subtypes)
class Animal
  def can_mate_with?(other : Animal) : Bool
    compatible?(other)
  end

  protected def compatible?(other : Animal) : Bool
    self.class == other.class
  end
end

class Dog < Animal
  protected def compatible?(other : Animal) : Bool
    other.is_a?(Dog)
  end
end

# Private constructor pattern
class Singleton
  @@instance : Singleton?

  private def initialize
  end

  def self.instance : Singleton
    @@instance ||= new
  end
end

# Private class methods
class Factory
  def self.create(type : String)
    case type
    when "a" then build_a
    when "b" then build_b
    else raise "Unknown type"
    end
  end

  private def self.build_a
    "A"
  end

  private def self.build_b
    "B"
  end
end

# Private constants
class Config
  DATABASE_URL = ENV["DATABASE_URL"]

  private SECRET_KEY = ENV["SECRET_KEY"]

  def encrypted_token
    # Can use SECRET_KEY here
    SECRET_KEY
  end
end

# Private types (nested)
class HttpClient
  def get(url : String) : Response
    raw = perform_request(url)
    Response.new(raw)
  end

  private class Response
    getter body : String

    def initialize(@body)
    end
  end

  private def perform_request(url : String) : String
    # ...
    ""
  end
end

# Protected with inheritance
class Base
  protected def hook
    puts "Base hook"
  end
end

class Child < Base
  def execute
    hook # Can call protected method from parent
  end

  protected def hook
    super
    puts "Child hook"
  end
end

# Abstract + private/protected
abstract class Template
  def execute
    before_hook
    perform
    after_hook
  end

  private abstract def perform
  protected abstract def before_hook
  protected abstract def after_hook
end

# Module private methods
module Helpers
  extend self

  def public_helper
    internal_logic
  end

  private def internal_logic
    "internal"
  end
end

# Private setter with public getter
class User
  getter name : String
  getter email : String

  def initialize(@name, @email)
  end

  def update_email(new_email : String)
    validate_email!(new_email)
    self.email = new_email
  end

  private setter email : String

  private def validate_email!(email : String)
    raise "Invalid email" unless email.includes?("@")
  end
end
