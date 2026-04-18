#!/usr/bin/env bash
# Docker 清理脚本 - 解决 "network not found" 错误
# 当出现 "Error response from daemon: network xxx not found" 时运行此脚本

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Docker 容器网络清理工具 ==="
echo ""

# 1. 查找引用了不存在网络的容器
echo "[1/4] 检查问题容器..."
PROBLEM_CONTAINERS=$(docker ps -a --filter "name=scheduler-" --format "{{.Names}}" 2>/dev/null || true)

if [[ -z "$PROBLEM_CONTAINERS" ]]; then
    echo "  没有找到 scheduler 相关容器"
    exit 0
fi

# 2. 停止所有 scheduler 容器
echo "[2/4] 停止所有 scheduler 容器..."
docker compose down --remove-orphans 2>/dev/null || true

# 3. 删除所有 scheduler 容器（包括已退出的）
echo "[3/4] 删除问题容器..."
for container in $PROBLEM_CONTAINERS; do
    if docker rm -f "$container" 2>/dev/null; then
        echo "  已删除: $container"
    fi
done

# 4. 清理悬空网络
echo "[4/4] 清理悬空网络..."
docker network prune -f 2>/dev/null || true

echo ""
echo "=== 清理完成 ==="
echo ""
echo "现在可以运行: docker compose up -d"




