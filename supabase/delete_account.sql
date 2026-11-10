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
