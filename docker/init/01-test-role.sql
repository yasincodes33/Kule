-- Testlerin bağlandığı rol SUPERUSER OLMAMALIDIR.
--
-- PostgreSQL'de superuser rolleri Row Level Security politikalarını tamamen baypas eder
-- (FORCE ROW LEVEL SECURITY bile superuser'ı durdurmaz). docker-compose'un POSTGRES_USER ile
-- oluşturduğu "agentsaas" rolü ise bootstrap superuser'ıdır.
--
-- Bu, sessiz ve tehlikeli bir soruna yol açıyordu: RLS entegrasyon testi bu role bağlandığında
-- izolasyonu HİÇ doğrulamıyordu, çünkü politikalar zaten uygulanmıyordu. Test ya boşuna
-- geçiyor ya da (bizim durumumuzda) beklediği hatayı alamayıp kırılıyordu.
--
-- Bu yüzden testler için ayrı, yetkisiz bir rol oluşturuluyor. Yeni bir volume ile ilk kez
-- ayağa kalkan konteynerlerde bu script otomatik çalışır (docker-entrypoint-initdb.d).
-- Var olan bir volume için aynı komutlar elle bir kez çalıştırılmalıdır.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'agentsaas_app') THEN
        CREATE ROLE agentsaas_app LOGIN PASSWORD 'agentsaas' NOSUPERUSER NOCREATEDB NOCREATEROLE;
    END IF;
END
$$;

GRANT ALL PRIVILEGES ON DATABASE agentsaas TO agentsaas_app;

-- Flyway şemayı bu rolle kuracağı için mevcut nesnelerin de sahibi olmalı.
-- REASSIGN OWNED BY kullanılamıyor: bootstrap rolü sistemin ihtiyaç duyduğu nesnelere de
-- sahip ve PostgreSQL bunların devrini reddediyor. Bu yüzden yalnızca public şeması ve
-- içindeki uygulama nesneleri devrediliyor. Yeni bir volume'de bu döngüler boş çalışır
-- (henüz tablo yoktur) ve tabloları zaten Flyway bu rolle oluşturur.
ALTER DATABASE agentsaas OWNER TO agentsaas_app;
ALTER SCHEMA public OWNER TO agentsaas_app;

DO $$
DECLARE
    nesne record;
BEGIN
    FOR nesne IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
        EXECUTE format('ALTER TABLE public.%I OWNER TO agentsaas_app', nesne.tablename);
    END LOOP;
    FOR nesne IN SELECT sequencename FROM pg_sequences WHERE schemaname = 'public' LOOP
        EXECUTE format('ALTER SEQUENCE public.%I OWNER TO agentsaas_app', nesne.sequencename);
    END LOOP;
    FOR nesne IN SELECT viewname FROM pg_views WHERE schemaname = 'public' LOOP
        EXECUTE format('ALTER VIEW public.%I OWNER TO agentsaas_app', nesne.viewname);
    END LOOP;
END
$$;
