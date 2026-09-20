# Leading-:: bare argument before a comma (serializable_spec.cr)

ex = expect_raises ::JSON::SerializableError, error_message do
  StrictJSONAttrPerson.from_json %({"name": 1})
end

expect_raises ::JSON::SerializableError, "message"

consume ::JSON::SerializableError
