#!/usr/bin/env bash
# JMC 启动脚本 - 使用 JMC 8.3.1（适配 Java 17）打开最新的 JFR 文件

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JMC_APP="/Users/yangguang/Downloads/jmc-8.3.1_macos-aarch64/JDK Mission Control.app"
JFR_DIR="${SCRIPT_DIR}/recordings"

# 检查 JMC 是否存在
if [[ ! -d "$JMC_APP" ]]; then
    echo "错误: JMC 8.3.1 未找到: $JMC_APP" >&2
    echo "请确保 JMC 8.3.1 已下载到该位置" >&2
    exit 1
fi

# 如果指定了 JFR 文件，使用它；否则查找最新的
if [[ $# -gt 0 && -f "$1" ]]; then
    JFR_FILE="$1"
    echo "使用指定的 JFR 文件: $JFR_FILE"
elif [[ -d "$JFR_DIR" ]]; then
    # 查找最新的 JFR 文件
    JFR_FILE=$(ls -t "${JFR_DIR}"/*.jfr 2>/dev/null | head -1)
    if [[ -z "$JFR_FILE" ]]; then
        echo "警告: 在 $JFR_DIR 中未找到 JFR 文件" >&2
        echo "启动 JMC（不打开文件）..."
        open -a "$JMC_APP"
        exit 0
    fi
    echo "找到最新的 JFR 文件: $(basename "$JFR_FILE")"
else
    echo "启动 JMC（不打开文件）..."
    open -a "$JMC_APP"
    exit 0
fi

# 设置 JAVA_HOME（使用 Java 17）
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"

# 使用 JMC 8.3.1 打开 JFR 文件
echo "正在启动 JMC 8.3.1 并打开 JFR 文件..."
open -a "$JMC_APP" "$JFR_FILE"

echo "✅ JMC 已启动，JFR 文件: $(basename "$JFR_FILE")"
