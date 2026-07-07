-- 1. Enable AI Vector Support
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Add Vector Column to Products (Aura Discovery)
-- 384 dimensions matches BGE-Small used in the Cloudflare Edge
ALTER TABLE product ADD COLUMN IF NOT EXISTS embedding vector(384);

-- 3. Clerk Identity Helper (Enterprise Pattern)
-- Extracts the 'sub' claim (User ID) from the Clerk JWT
CREATE OR REPLACE FUNCTION public.requesting_user_id()
RETURNS text
LANGUAGE sql STABLE
AS $$
  SELECT NULLIF(
    current_setting('request.jwt.claims', true)::json->>'sub',
    ''
  )::text
$$;

-- 4. Secure the Commerce Data (Row-Level Security)
ALTER TABLE "order" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "order_item" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "browse_history" ENABLE ROW LEVEL SECURITY;

-- 5. Define Access Policies
-- Users can only see and manage their OWN orders
CREATE POLICY "Users can manage own orders" ON "order"
  FOR ALL
  USING (customer_id = requesting_user_id());

CREATE POLICY "Users can see own items" ON "order_item"
  FOR ALL
  USING (EXISTS (
    SELECT 1 FROM "order"
    WHERE "order".id = order_item.order_id
    AND "order".customer_id = requesting_user_id()
  ));

-- Products are publicly readable for discovery
ALTER TABLE product ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Products are public" ON product
  FOR SELECT
  USING (true);
