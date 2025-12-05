import React from 'react';

/**
 * 进度条组件
 */
export function ProgressBar({ value, max, showLabel = true, className = '' }) {
  const percent = max > 0 ? Math.min(100, (value / max) * 100) : 0;
  
  return (
    <div className={`progress-container ${className}`}>
      <div className="progress-bar">
        <div 
          className="progress-fill" 
          style={{ width: `${percent}%` }}
        />
      </div>
      {showLabel && (
        <div className="progress-label">
          <span className="progress-value">{formatNumber(value)}</span>
          <span className="progress-separator">/</span>
          <span className="progress-max">{formatNumber(max)}</span>
          <span className="progress-percent">({percent.toFixed(1)}%)</span>
        </div>
      )}
    </div>
  );
}

/**
 * 持续任务进度组件（显示 ops/s 和延迟）
 */
export function ContinuousProgress({ task }) {
  if (!task || task.status === 'IDLE') {
    return null;
  }
  
  const duration = task.startedAt 
    ? formatDuration(Date.now() - new Date(task.startedAt).getTime())
    : '-';
  
  return (
    <div className="continuous-progress">
      <div className="progress-stats">
        <div className="stat">
          <span className="stat-label">运行时长</span>
          <span className="stat-value">{duration}</span>
        </div>
        <div className="stat">
          <span className="stat-label">操作数</span>
          <span className="stat-value">{formatNumber(task.operations || 0)}</span>
        </div>
        <div className="stat">
          <span className="stat-label">吞吐量</span>
          <span className="stat-value highlight">{formatOps(task.currentOpsPerSec)} ops/s</span>
        </div>
        {task.avgLatencyMs > 0 && (
          <div className="stat">
            <span className="stat-label">平均延迟</span>
            <span className="stat-value">{task.avgLatencyMs.toFixed(2)} ms</span>
          </div>
        )}
        {task.errorCount > 0 && (
          <div className="stat error">
            <span className="stat-label">错误</span>
            <span className="stat-value">{task.errorCount}</span>
          </div>
        )}
      </div>
    </div>
  );
}

function formatNumber(num) {
  if (num >= 1_000_000) return (num / 1_000_000).toFixed(1) + 'M';
  if (num >= 1_000) return (num / 1_000).toFixed(1) + 'K';
  return num.toString();
}

function formatOps(ops) {
  if (!ops || isNaN(ops)) return '0';
  if (ops >= 1_000_000) return (ops / 1_000_000).toFixed(2) + 'M';
  if (ops >= 1_000) return (ops / 1_000).toFixed(2) + 'K';
  return ops.toFixed(0);
}

function formatDuration(ms) {
  if (ms < 1000) return `${ms}ms`;
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  if (minutes < 60) return `${minutes}m ${remainingSeconds}s`;
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return `${hours}h ${remainingMinutes}m`;
}

export default ProgressBar;

