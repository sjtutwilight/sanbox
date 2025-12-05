/**
 * 常量定义
 * 注意：大部分配置已迁移到后端 /api/config 接口
 */

// API 基础路径
export const API_BASE = '/api';

// 任务状态
export const TaskStatus = {
  IDLE: 'IDLE',
  RUNNING: 'RUNNING',
  COMPLETED: 'COMPLETED',
  STOPPED: 'STOPPED',
  FAILED: 'FAILED',
};

// 操作类型
export const OperationType = {
  INIT_DATA: 'INIT_DATA',
  CONTINUOUS_WRITE: 'CONTINUOUS_WRITE',
  CONTINUOUS_READ: 'CONTINUOUS_READ',
};

// 轮询间隔（毫秒）
export const POLL_INTERVAL = 1500;
