# smartWashCloude-server

SmartWash 后端服务，基于 Java 17、Spring Boot 3.2.5 和 MySQL。

## GitHub 与 Codespaces

Codespaces 终端的 `Welcome to Codespaces` 是正常欢迎提示，表示云端开发环境已打开。
本仓库代码推送后，已有 Codespace 可在工作区没有未提交修改时执行：

```bash
git pull --ff-only origin main
```

上传代码不等于服务已启动。启动前需要 Java 17 或更高版本、Maven，以及可以访问的 MySQL。

## 配置并启动

1. 准备 MySQL 数据库。`src/main/resources/init.sql` 中的基础初始化脚本使用数据库 `new`；请先检查脚本，再在自己的开发数据库执行。清洗业务表及脚本版本说明见 [数据库文档](docs/database/README.md)。
2. 在终端设置环境变量，数据库地址和账号按实际环境填写：

```bash
export DB_URL='jdbc:mysql://127.0.0.1:3306/new?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
export DB_USERNAME='root'
read -rsp 'MySQL password: ' DB_PASSWORD; echo
export DB_PASSWORD
export JWT_SECRET="$(openssl rand -base64 48)"
mvn spring-boot:run
```

JWT 密钥在同一环境应固定保存，可以把 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET` 配置为 Codespaces Secrets，再重新启动 Codespace。上面的随机密钥命令适用于临时开发。

服务默认监听 `8080`。启动成功后，可在 Codespaces 的 Ports（端口）面板打开该端口。
Codespaces 中的 `localhost` 指云端容器，不是本机 Windows；使用本机数据库时需要另行建立网络连接。

## 构建

```bash
mvn -DskipTests package
```

编译输出位于 `target/`。数据库密码和 JWT 密钥通过环境变量传入；本次上传不包含本机配置密码、编译产物、日志、IDE 配置和历史备份目录。PLC 接入默认关闭，具体说明见 `docs/database/`。