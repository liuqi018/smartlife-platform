# SmartLife Platform

## 项目简介

SmartLife Platform 是一个基于 Spring Boot 的智能运维测试平台，用于模拟企业应用运行中的常见故障，并配合 AIOps 平台进行指标监控、告警和自动诊断。

## 项目功能

- Spring Boot 应用服务
- Prometheus 指标采集
- AlertManager 告警管理
- Actuator 健康检查
- Redis/MySQL 环境支持
- 故障注入接口

## 支持故障注入

| 故障类型 | 说明 |
| --- | --- |
| CPU 高负载 | 通过故障注入接口模拟应用 CPU 持续占用 |
| JVM OOM | 通过逐步保留堆对象模拟 JVM 堆内存压力升高 |
| MySQL 慢查询 | 通过执行 `SELECT SLEEP(...)` 模拟数据库慢 SQL 场景 |
| SmartLife 服务不可用 | 通过停止应用服务模拟服务不可用 |
| Redis 不可用 | 通过停止 Redis 服务模拟 Redis 异常 |
| MySQL 不可用 | 通过停止 MySQL 服务模拟数据库连接失败 |

Prometheus 负责采集应用指标与探测健康状态，并将匹配规则的告警发送至 AlertManager。

## 技术栈

- Java
- Spring Boot
- MySQL
- Redis
- Docker
- Prometheus
- AlertManager

## 快速启动

```bash
docker compose up -d
```
