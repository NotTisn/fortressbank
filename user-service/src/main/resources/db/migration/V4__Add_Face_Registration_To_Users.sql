-- Add face registration support to users table
ALTER TABLE users ADD COLUMN is_face_registered BOOLEAN DEFAULT FALSE;
