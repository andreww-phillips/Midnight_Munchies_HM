/// <reference types="vite/client" />
import { createClient } from '@supabase/supabase-js';

// Let's inspect what environment variables Vite has loaded
console.log("🔍 Vite Environment Check:", import.meta.env);

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
const supabaseKey = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY;

export const supabase = createClient(supabaseUrl, supabaseKey);