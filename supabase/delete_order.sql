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
