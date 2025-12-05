import React from 'react';

/**
 * 状态徽章组件
 */
const statusConfig = {
  IDLE: { label: '未启动', className: 'status-idle', icon: '○' },
  RUNNING: { label: '运行中', className: 'status-running', icon: '●' },
  COMPLETED: { label: '已完成', className: 'status-completed', icon: '✓' },
  STOPPED: { label: '已停止', className: 'status-stopped', icon: '■' },
  FAILED: { label: '失败', className: 'status-failed', icon: '✗' },
};

export function StatusBadge({ status }) {
  const config = statusConfig[status] || statusConfig.IDLE;
  
  return (
    <span className={`status-badge ${config.className}`}>
      <span className="status-icon">{config.icon}</span>
      <span className="status-label">{config.label}</span>
    </span>
  );
}

export default StatusBadge;

