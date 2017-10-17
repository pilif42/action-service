-- Add index 
ALTER TABLE action.actionplan ADD CONSTRAINT name_key UNIQUE (name); 