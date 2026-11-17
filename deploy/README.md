# 部署索引

- **单机一体化部署**：见 `deploy/compose-single/README.md`。PostgreSQL、Redis、MinIO、
  后端和 Nginx 使用 Docker Compose，提供更新、回滚和数据库备份脚本。
- **collector**：仍在宿主机 systemd 运行，保留 `deploy/scripts/deploy-collector.sh`、
  `deploy/scripts/lib/common.sh`、`deploy/systemd/collector.service` 与公共函数测试。
  开发机入口为 `deploy/compose-single/scripts/release-collector.sh`。
- **旧方案**：后端/前端 systemd 应用单元、旧 Nginx 站点、Kiosk 单元及其发布脚本已删除，
  不要再参考历史文档中的 `release.sh`、`deploy-backend.sh` 或 `deploy-frontend.sh`。
- **开发环境**：`script/docker-compose/` 与 `script/docker-compose-infra/` 保留，
  它们不属于板端单机部署。
