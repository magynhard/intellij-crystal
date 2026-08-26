class Config
  def initialize
    @host = nil
  end

  # Instance setter with typed parameter
  def host=(host : String?)
    @host = host
  end

  # Class-level (self) setter
  def self.env=(value : String)
  end

  # Abstract setter
  abstract def level=(value : Int32)
end

# Setters are invoked through assignment syntax
config = Config.new
config.host = "localhost"
Config.env = "prod"
