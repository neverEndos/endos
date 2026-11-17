#!/bin/sh
# PostgreSQL 首次初始化：保留原 DB 存储配置，新增 MinIO S3 配置并切换 master。
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=minio_endpoint="$MINIO_ENDPOINT" \
  --set=minio_domain="$MINIO_PUBLIC_DOMAIN" \
  --set=minio_bucket="$MINIO_BUCKET" \
  --set=minio_access_key="$MINIO_ROOT_USER" \
  --set=minio_access_secret="$MINIO_ROOT_PASSWORD" <<'SQL'
BEGIN;

UPDATE public.infra_file_config SET master = false WHERE master = true;

INSERT INTO public.infra_file_config
  (id, name, storage, remark, master, config, creator, create_time, updater, update_time, deleted)
VALUES
  (5, '本机 MinIO', 20, '单机 Compose 私有 bucket，经 Nginx 同源代理访问', true,
   jsonb_build_object(
     '@class', 'cn.iocoder.yudao.module.infra.framework.file.core.client.s3.S3FileClientConfig',
     'endpoint', :'minio_endpoint',
     'domain', :'minio_domain',
     'bucket', :'minio_bucket',
     'accessKey', :'minio_access_key',
     'accessSecret', :'minio_access_secret',
     'enablePathStyleAccess', true,
     'enablePublicAccess', false,
     'region', 'us-east-1'
   )::text,
   '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  storage = EXCLUDED.storage,
  remark = EXCLUDED.remark,
  master = true,
  config = EXCLUDED.config,
  updater = '1',
  update_time = CURRENT_TIMESTAMP,
  deleted = 0;

SELECT setval('public.infra_file_config_seq',
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM public.infra_file_config), 1));
COMMIT;
SQL
