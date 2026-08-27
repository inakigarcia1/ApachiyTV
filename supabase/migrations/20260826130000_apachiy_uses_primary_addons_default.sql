-- All profiles always sync addons from the primary profile.
ALTER TABLE public.profiles
    ALTER COLUMN uses_primary_addons SET DEFAULT TRUE;

UPDATE public.profiles
SET uses_primary_addons = TRUE
WHERE uses_primary_addons = FALSE;
