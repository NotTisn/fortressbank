local kong = kong
local cjson = require "cjson.safe"
local http = require "resty.http"

local SingleDeviceHandler = {
  PRIORITY = 100,  -- Run AFTER openid-connect (priority 1000) to ensure token is validated first
  VERSION = "1.0.0",
}

local function decode_jwt_payload(token)
  if not token then
    return nil, "missing token"
  end

  local parts = {}
  for part in string.gmatch(token, "[^.]+") do
    table.insert(parts, part)
  end

  if #parts < 2 then
    return nil, "invalid token format"
  end

  local payload_b64 = parts[2]
  -- Normalize URL-safe base64
  payload_b64 = payload_b64:gsub("-", "+"):gsub("_", "/")
  local padding = #payload_b64 % 4
  if padding == 2 then
    payload_b64 = payload_b64 .. "=="
  elseif padding == 3 then
    payload_b64 = payload_b64 .. "="
  elseif padding ~= 0 then
    return nil, "invalid base64 padding"
  end

  local payload_json = ngx.decode_base64(payload_b64)
  if not payload_json then
    return nil, "failed to base64 decode payload"
  end

  local payload, err = cjson.decode(payload_json)
  if not payload then
    return nil, "failed to decode JSON payload: " .. (err or "unknown")
  end

  return payload, nil
end

local function get_admin_token(conf)
  local httpc = http.new()
  local url = string.format("%s/realms/%s/protocol/openid-connect/token", conf.keycloak_url, conf.realm)

  local res, err = httpc:request_uri(url, {
    method = "POST",
    body = string.format("grant_type=client_credentials&client_id=%s&client_secret=%s", 
                         conf.client_id, conf.client_secret),
    headers = {
      ["Content-Type"] = "application/x-www-form-urlencoded",
    },
    ssl_verify = false,
  })

  if not res then
    return nil, "failed to get admin token: " .. (err or "unknown")
  end

  if res.status ~= 200 then
    return nil, "failed to get admin token, status: " .. res.status
  end

  local data, decode_err = cjson.decode(res.body)
  if not data then
    return nil, "failed to decode token response: " .. (decode_err or "unknown")
  end

  return data.access_token, nil
end

local function fetch_user_sessions(conf, user_id)
  -- Get token automatically using client credentials
  local token, token_err = get_admin_token(conf)
  if not token then
    return nil, token_err
  end

  local httpc = http.new()
  local url = string.format("%s/admin/realms/%s/users/%s/sessions", conf.keycloak_url, conf.realm, user_id)

  local res, err = httpc:request_uri(url, {
    method = "GET",
    headers = {
      ["Authorization"] = "Bearer " .. token,
      ["Accept"] = "application/json",
    },
    ssl_verify = false,
  })

  if not res then
    return nil, "failed to query Keycloak: " .. (err or "unknown")
  end

  if res.status ~= 200 then
    return nil, "unexpected status from Keycloak: " .. res.status
  end

  local data, decode_err = cjson.decode(res.body)
  if not data then
    return nil, "failed to decode Keycloak response: " .. (decode_err or "unknown")
  end

  return data, nil
end

function SingleDeviceHandler:access(conf)
  local auth_header = kong.request.get_header("authorization")
  if not auth_header then
    return
  end

  local token = auth_header:match("Bearer%s+(.+)")
  if not token then
    return
  end

  local payload, err = decode_jwt_payload(token)
  if not payload then
    kong.log.err("single-device: unable to parse JWT: ", err)
    return kong.response.exit(401, {
      error = "invalid_token",
      error_description = "Unable to parse JWT for single-device validation",
    })
  end

  local user_id = payload.sub
  local token_device_id = payload.deviceId
  
  -- Log for debugging
  kong.log.debug("single-device: user_id=", user_id, ", token_device_id=", token_device_id)
  
  if not user_id then
    kong.log.err("single-device: JWT missing 'sub' claim")
    return kong.response.exit(401, {
      error = "invalid_token",
      error_description = "Token missing required 'sub' claim for single-device validation",
    })
  end
  
  if not token_device_id then
    kong.log.warn("single-device: JWT missing 'deviceId' claim - single-device enforcement disabled for this request")
    -- If deviceId is not in token, skip validation (allows backward compatibility)
    -- This should not happen if Keycloak protocol mapper is configured correctly
    return
  end

  local header_device_id = kong.request.get_header("x-device-id")
  if not header_device_id then
    kong.log.err("single-device: Missing X-Device-Id header in request")
    return kong.response.exit(401, {
      error = "device_mismatch",
      error_description = "X-Device-Id header is required for single-device validation",
    })
  end
  
  if header_device_id ~= token_device_id then
    kong.log.warn("single-device: Device mismatch - header=", header_device_id, ", token=", token_device_id)
    return kong.response.exit(401, {
      error = "device_mismatch",
      error_description = "Request device does not match token device. Please log in again.",
    })
  end

  local sessions, sess_err = fetch_user_sessions(conf, user_id)
  if not sessions then
    kong.log.err("single-device: ", sess_err)
    return kong.response.exit(401, {
      error = "session_validation_failed",
      error_description = "Unable to validate session with Keycloak",
    })
  end

  local valid = false

  for _, session in ipairs(sessions) do
    local notes = session.notes or {}
    local session_device_id = notes.deviceId or notes["deviceId"]
    if session_device_id == token_device_id then
      valid = true
      break
    end
  end

  if not valid then
    return kong.response.exit(401, {
      error = "session_invalid",
      error_description = "No active session found for this device",
    })
  end
end

return SingleDeviceHandler


