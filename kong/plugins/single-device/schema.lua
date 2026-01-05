local typedefs = require "kong.db.schema.typedefs"

return {
  name = "single-device",
  fields = {
    { consumer = typedefs.no_consumer },
    { protocols = typedefs.protocols_http },
    { config = {
        type = "record",
        fields = {
          { keycloak_url = { type = "string", required = true }, },
          { realm = { type = "string", required = true }, },
          { client_id = { type = "string", required = true }, },
          { client_secret = { type = "string", required = true }, },
        },
      },
    },
  },
}


