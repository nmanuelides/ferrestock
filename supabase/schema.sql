-- =====================================================================
-- Ferrestock — esquema de Supabase
-- Pegar completo en: Supabase > SQL Editor > New query > Run
-- =====================================================================

-- ---------- Perfiles (uno por usuario de Google) ----------------------
create table if not exists public.profiles (
  id          uuid primary key references auth.users on delete cascade,
  full_name   text not null default '',
  email       text,
  avatar_url  text,
  -- Poner en false desde el dashboard para quitarle el acceso a alguien.
  -- Si querés aprobar a mano cada cuenta nueva, cambiá el default a false.
  active      boolean not null default true,
  created_at  timestamptz not null default now()
);

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, full_name, email, avatar_url)
  values (
    new.id,
    coalesce(
      nullif(new.raw_user_meta_data->>'full_name', ''),
      nullif(new.raw_user_meta_data->>'name', ''),
      split_part(coalesce(new.email, 'Usuario'), '@', 1)
    ),
    new.email,
    new.raw_user_meta_data->>'avatar_url'
  )
  on conflict (id) do nothing;
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

create or replace function public.is_member()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from profiles where id = auth.uid() and active);
$$;

create or replace function public.current_user_name()
returns text language sql stable security definer set search_path = public as $$
  select coalesce((select full_name from profiles where id = auth.uid()), 'Usuario');
$$;

-- ---------- Productos -------------------------------------------------
create table if not exists public.products (
  id              uuid primary key default gen_random_uuid(),
  name            text not null check (length(trim(name)) > 0),
  description     text not null default '',
  category        text not null default '',
  unit            text not null default 'u',
  quantity        integer not null default 0 check (quantity >= 0),
  min_stock       integer not null default 0 check (min_stock >= 0),
  photo_path      text,
  active          boolean not null default true,   -- false = eliminado (se conserva el historial)
  created_at      timestamptz not null default now(),
  created_by      uuid references public.profiles(id),
  created_by_name text not null default ''
);
create index if not exists products_active_name_idx on public.products (active, name);

-- ---------- Órdenes ---------------------------------------------------
create table if not exists public.orders (
  id          uuid primary key default gen_random_uuid(),
  number      bigint generated always as identity,  -- numeración correlativa: ORD-000001
  description text not null default '',
  user_id     uuid references public.profiles(id),
  user_name   text not null default '',
  created_at  timestamptz not null default now()
);

create table if not exists public.order_items (
  id           uuid primary key default gen_random_uuid(),
  order_id     uuid not null references public.orders(id) on delete cascade,
  product_id   uuid references public.products(id),
  product_name text not null,
  unit         text not null default 'u',
  quantity     integer not null check (quantity > 0)
);
create index if not exists order_items_order_idx on public.order_items (order_id);

-- ---------- Historial de movimientos ----------------------------------
create table if not exists public.movements (
  id           uuid primary key default gen_random_uuid(),
  product_id   uuid references public.products(id),
  product_name text not null,
  unit         text not null default 'u',
  type         text not null check (type in ('alta','ingreso','retiro','orden','edicion','baja','devolucion')),
  quantity     integer not null default 0,   -- siempre positivo; el sentido lo da "type"
  stock_after  integer,
  note         text not null default '',
  order_id     uuid references public.orders(id),
  user_id      uuid references public.profiles(id),
  user_name    text not null default '',
  created_at   timestamptz not null default now()
);
create index if not exists movements_created_idx on public.movements (created_at desc);
create index if not exists movements_product_idx on public.movements (product_id, created_at desc);

-- ---------- Seguridad (RLS) -------------------------------------------
-- Lectura: cualquier usuario activo. Escritura: SOLO a través de las
-- funciones de abajo, así ningún cambio de stock queda sin registrar.
alter table public.profiles    enable row level security;
alter table public.products    enable row level security;
alter table public.orders      enable row level security;
alter table public.order_items enable row level security;
alter table public.movements   enable row level security;

grant select on public.profiles, public.products, public.orders, public.order_items, public.movements to authenticated;

drop policy if exists profiles_read on public.profiles;
create policy profiles_read on public.profiles for select to authenticated
  using (id = auth.uid() or public.is_member());

drop policy if exists products_read on public.products;
create policy products_read on public.products for select to authenticated using (public.is_member());

drop policy if exists orders_read on public.orders;
create policy orders_read on public.orders for select to authenticated using (public.is_member());

drop policy if exists order_items_read on public.order_items;
create policy order_items_read on public.order_items for select to authenticated using (public.is_member());

drop policy if exists movements_read on public.movements;
create policy movements_read on public.movements for select to authenticated using (public.is_member());

-- ---------- Funciones (toda escritura pasa por acá) -------------------
create or replace function public.create_product(
  p_id uuid, p_name text, p_description text, p_category text,
  p_unit text, p_quantity integer, p_min_stock integer, p_photo_path text
) returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_user text := current_user_name();
  v_unit text := coalesce(nullif(trim(p_unit), ''), 'u');
begin
  if not is_member() then raise exception 'No tenés permiso para hacer esto'; end if;
  if coalesce(p_quantity, 0) < 0 then raise exception 'La cantidad no puede ser negativa'; end if;

  insert into products (id, name, description, category, unit, quantity, min_stock, photo_path, created_by, created_by_name)
  values (coalesce(p_id, gen_random_uuid()), trim(p_name), coalesce(trim(p_description), ''), coalesce(trim(p_category), ''),
          v_unit, coalesce(p_quantity, 0), greatest(coalesce(p_min_stock, 0), 0), p_photo_path, auth.uid(), v_user)
  returning id into p_id;

  insert into movements (product_id, product_name, unit, type, quantity, stock_after, note, user_id, user_name)
  values (p_id, trim(p_name), v_unit, 'alta', coalesce(p_quantity, 0), coalesce(p_quantity, 0), 'Producto agregado', auth.uid(), v_user);

  return p_id;
end $$;

create or replace function public.update_product(
  p_id uuid, p_name text, p_description text, p_category text,
  p_unit text, p_min_stock integer, p_photo_path text
) returns void language plpgsql security definer set search_path = public as $$
declare
  v_prod products;
  v_unit text := coalesce(nullif(trim(p_unit), ''), 'u');
begin
  if not is_member() then raise exception 'No tenés permiso para hacer esto'; end if;

  update products
     set name = trim(p_name), description = coalesce(trim(p_description), ''), category = coalesce(trim(p_category), ''),
         unit = v_unit, min_stock = greatest(coalesce(p_min_stock, 0), 0), photo_path = p_photo_path
   where id = p_id and active
  returning * into v_prod;
  if not found then raise exception 'Producto no encontrado'; end if;

  insert into movements (product_id, product_name, unit, type, quantity, stock_after, note, user_id, user_name)
  values (p_id, v_prod.name, v_prod.unit, 'edicion', 0, v_prod.quantity, 'Datos del producto editados', auth.uid(), current_user_name());
end $$;

-- p_delta > 0 ingresa stock, p_delta < 0 retira stock.
create or replace function public.adjust_stock(p_product_id uuid, p_delta integer, p_note text)
returns integer language plpgsql security definer set search_path = public as $$
declare
  v_prod products;
begin
  if not is_member() then raise exception 'No tenés permiso para hacer esto'; end if;
  if coalesce(p_delta, 0) = 0 then raise exception 'La cantidad tiene que ser mayor a cero'; end if;

  select * into v_prod from products where id = p_product_id and active for update;
  if not found then raise exception 'Producto no encontrado'; end if;
  if v_prod.quantity + p_delta < 0 then
    raise exception 'Stock insuficiente de %: quedan % %', v_prod.name, v_prod.quantity, v_prod.unit;
  end if;

  update products set quantity = quantity + p_delta where id = p_product_id;

  insert into movements (product_id, product_name, unit, type, quantity, stock_after, note, user_id, user_name)
  values (p_product_id, v_prod.name, v_prod.unit,
          case when p_delta > 0 then 'ingreso' else 'retiro' end,
          abs(p_delta), v_prod.quantity + p_delta, coalesce(trim(p_note), ''), auth.uid(), current_user_name());

  return v_prod.quantity + p_delta;
end $$;

create or replace function public.delete_product(p_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_prod products;
begin
  if not is_member() then raise exception 'No tenés permiso para hacer esto'; end if;

  update products set active = false where id = p_id and active returning * into v_prod;
  if not found then raise exception 'Producto no encontrado'; end if;

  insert into movements (product_id, product_name, unit, type, quantity, stock_after, note, user_id, user_name)
  values (p_id, v_prod.name, v_prod.unit, 'baja', v_prod.quantity, 0, 'Producto eliminado', auth.uid(), current_user_name());
end $$;

-- p_items: [{"product_id": "uuid", "quantity": 3}, ...]
-- Todo o nada: si falta stock de un solo producto, no se descuenta ninguno.
create or replace function public.create_order(p_description text, p_items jsonb)
returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_user   text := current_user_name();
  v_order  orders;
  v_item   jsonb;
  v_prod   products;
  v_qty    integer;
  v_code   text;
begin
  if not is_member() then raise exception 'No tenés permiso para hacer esto'; end if;
  if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
    raise exception 'La orden no tiene productos';
  end if;

  insert into orders (description, user_id, user_name)
  values (coalesce(trim(p_description), ''), auth.uid(), v_user)
  returning * into v_order;
  v_code := 'ORD-' || lpad(v_order.number::text, 6, '0');

  for v_item in select * from jsonb_array_elements(p_items) loop
    v_qty := (v_item->>'quantity')::integer;
    if v_qty is null or v_qty <= 0 then raise exception 'Cantidad inválida en la orden'; end if;

    select * into v_prod from products where id = (v_item->>'product_id')::uuid and active for update;
    if not found then raise exception 'Uno de los productos ya no existe'; end if;
    if v_prod.quantity < v_qty then
      raise exception 'Stock insuficiente de %: quedan % %', v_prod.name, v_prod.quantity, v_prod.unit;
    end if;

    update products set quantity = quantity - v_qty where id = v_prod.id;

    insert into order_items (order_id, product_id, product_name, unit, quantity)
    values (v_order.id, v_prod.id, v_prod.name, v_prod.unit, v_qty);

    insert into movements (product_id, product_name, unit, type, quantity, stock_after, note, order_id, user_id, user_name)
    values (v_prod.id, v_prod.name, v_prod.unit, 'orden', v_qty, v_prod.quantity - v_qty,
            trim(v_code || ' · ' || coalesce(p_description, ''), ' ·'), v_order.id, auth.uid(), v_user);
  end loop;

  return v_order.id;
end $$;

revoke execute on function public.create_product(uuid, text, text, text, text, integer, integer, text) from public, anon;
revoke execute on function public.update_product(uuid, text, text, text, text, integer, text) from public, anon;
revoke execute on function public.adjust_stock(uuid, integer, text) from public, anon;
revoke execute on function public.delete_product(uuid) from public, anon;
revoke execute on function public.create_order(text, jsonb) from public, anon;
grant execute on function public.create_product(uuid, text, text, text, text, integer, integer, text) to authenticated;
grant execute on function public.update_product(uuid, text, text, text, text, integer, text) to authenticated;
grant execute on function public.adjust_stock(uuid, integer, text) to authenticated;
grant execute on function public.delete_product(uuid) to authenticated;
grant execute on function public.create_order(text, jsonb) to authenticated;
grant execute on function public.is_member() to authenticated;
grant execute on function public.current_user_name() to authenticated;

-- ---------- Fotos de productos (Storage) ------------------------------
-- Bucket público de lectura: las fotos se ven por URL, pero solo un
-- usuario activo puede subirlas.
insert into storage.buckets (id, name, public)
values ('product-photos', 'product-photos', true)
on conflict (id) do nothing;

drop policy if exists product_photos_insert on storage.objects;
create policy product_photos_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'product-photos' and public.is_member());

drop policy if exists product_photos_read on storage.objects;
create policy product_photos_read on storage.objects for select to authenticated
  using (bucket_id = 'product-photos');

-- ---------- Eliminar (desactivar) la propia cuenta --------------------
-- La llama la app desde "Eliminar cuenta". No borra al usuario: lo desactiva,
-- así todo lo que hizo sigue vinculado a su perfil (trazabilidad). Pierde el
-- acceso igual que si lo desactivaras a mano, y se cierran todas sus sesiones.
-- Para reactivarlo: profiles.active = true y deleted_at = null desde el dashboard.
alter table public.profiles add column if not exists deleted_at timestamptz;

create or replace function public.delete_my_account()
returns void language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'No hay una sesión iniciada'; end if;

  update profiles set active = false, deleted_at = now() where id = v_uid;
  delete from auth.sessions where user_id = v_uid;
end $$;

revoke execute on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

-- ---------- Eliminar una orden (devuelve el stock) -------------------
-- La llama la app desde "Eliminar orden". La orden no se borra: queda
-- marcada como eliminada (deja de verse en la lista) y su número no se
-- reutiliza. Todo lo que consumió vuelve al stock, con un movimiento
-- "devolucion" por producto, firmado por quien la eliminó.
-- Si un producto de la orden ya fue eliminado, no se le devuelve stock.
alter table public.orders add column if not exists deleted_at timestamptz;
alter table public.orders add column if not exists deleted_by_name text;

alter table public.movements drop constraint if exists movements_type_check;
alter table public.movements add constraint movements_type_check
  check (type in ('alta','ingreso','retiro','orden','edicion','baja','devolucion'));

create or replace function public.delete_order(p_order_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_user  text := current_user_name();
  v_order orders;
  v_item  order_items;
  v_prod  products;
  v_note  text;
begin
  if not is_member() then raise exception 'No tenés permiso para hacer esto'; end if;

  -- Marcarla primero bloquea la fila: dos personas no pueden devolver el stock dos veces.
  update orders set deleted_at = now(), deleted_by_name = v_user
   where id = p_order_id and deleted_at is null
  returning * into v_order;
  if not found then raise exception 'La orden no existe o ya fue eliminada'; end if;

  v_note := trim('ORD-' || lpad(v_order.number::text, 6, '0') || ' eliminada · '
                 || coalesce(v_order.description, ''), ' ·');

  for v_item in select * from order_items where order_id = p_order_id loop
    select * into v_prod from products where id = v_item.product_id and active for update;
    if found then
      update products set quantity = quantity + v_item.quantity where id = v_prod.id;

      insert into movements (product_id, product_name, unit, type, quantity, stock_after, note, order_id, user_id, user_name)
      values (v_prod.id, v_prod.name, v_prod.unit, 'devolucion', v_item.quantity,
              v_prod.quantity + v_item.quantity, v_note, p_order_id, auth.uid(), v_user);
    end if;
  end loop;
end $$;

revoke execute on function public.delete_order(uuid) from public, anon;
grant execute on function public.delete_order(uuid) to authenticated;
