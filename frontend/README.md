# Data Generator UI (React + Vite)

前端实验控制台，调用后端 `/api/data-generator` 接口生成 Redis 数据（正常 / 误用对照）。

## 快速开始

```bash
cd frontend
npm install
npm run dev
# 浏览器打开 http://localhost:5173
```

Vite 已配置代理，将 `/api` 转发到 `http://localhost:8080`（参见 `vite.config.js`），保持后端 Spring Boot 默认端口即可。

## 构建

```bash
npm run build
# 产物在 dist/，可通过 nginx/静态资源方式托管，或复制到后端 static 目录
```

## 说明

- `src/App.jsx`：主页面（表单、任务进度、实验提示、日志）+ 实验模块（加载/运行对照实验组）。
- `src/constants.js`：模式/提示/命令映射，后续扩展实验时补充即可。
- `src/styles.css`：深色渐变风格样式。

未来新增实验管理页面时，可在 `src` 内添加路由或组件，继续复用样式与常量。
