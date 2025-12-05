import { useState, useCallback } from 'react';

const API_BASE = '/api';

/**
 * API 调用 Hook
 */
export function useApi() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const request = useCallback(async (endpoint, options = {}) => {
    setLoading(true);
    setError(null);
    
    try {
      const res = await fetch(`${API_BASE}${endpoint}`, {
        headers: {
          'Content-Type': 'application/json',
          ...options.headers,
        },
        ...options,
      });
      
      if (!res.ok) {
        const errText = await res.text();
        throw new Error(errText || res.statusText);
      }
      
      const data = await res.json();
      return data;
    } catch (err) {
      setError(err.message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return { request, loading, error };
}

/**
 * 实验相关 API
 */
const withExperimentHeaders = (headers = {}, experimentRunId) => {
  if (!experimentRunId) return headers;
  return { ...headers, 'X-Experiment-Id': experimentRunId };
};

export const experimentApi = {
  // 获取所有实验
  list: () => fetch(`${API_BASE}/experiments`).then(r => r.json()),
  
  // 获取单个实验
  get: (id) => fetch(`${API_BASE}/experiments/${id}`).then(r => r.json()),
  
  // 启动操作
  startOperation: (expId, groupId, opId, body, experimentRunId) => 
    fetch(`${API_BASE}/experiments/${expId}/groups/${groupId}/operations/${opId}/start`, {
      method: 'POST',
      headers: withExperimentHeaders({ 'Content-Type': 'application/json' }, experimentRunId),
      body: JSON.stringify(body || {}),
    }).then(async r => {
      if (!r.ok) {
        const text = await r.text();
        throw new Error(text || r.statusText);
      }
      const text = await r.text();
      return text ? JSON.parse(text) : {};
    }),
  
  // 停止操作
  stopOperation: (expId, groupId, opId, experimentRunId) =>
    fetch(`${API_BASE}/experiments/${expId}/groups/${groupId}/operations/${opId}/stop`, {
      method: 'POST',
      headers: withExperimentHeaders({}, experimentRunId),
    }).then(r => r.json()),
  
  // 获取操作状态
  getOperationStatus: (expId, groupId, opId, experimentRunId) =>
    fetch(`${API_BASE}/experiments/${expId}/groups/${groupId}/operations/${opId}/status`, {
      headers: withExperimentHeaders({}, experimentRunId),
    })
      .then(r => r.json()),
  
  // 批量获取状态
  batchGetStatus: (requests) =>
    fetch(`${API_BASE}/experiments/batch-status`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requests),
    }).then(r => r.json()),
  
  // 获取所有运行中的任务
  getRunningTasks: () =>
    fetch(`${API_BASE}/experiments/running-tasks`).then(r => r.json()),
};

/**
 * 配置 API
 */
export const configApi = {
  get: () => fetch(`${API_BASE}/config`).then(r => r.json()),
};

/**
 * 数据生成 API
 */
export const dataGenApi = {
  // 创建任务
  createJob: (request) =>
    fetch(`${API_BASE}/data-generator/jobs`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    }).then(r => r.json()),
  
  // 获取任务状态
  getJob: (jobId) =>
    fetch(`${API_BASE}/data-generator/jobs/${jobId}`).then(r => r.json()),
  
  // 取消任务
  cancelJob: (jobId) =>
    fetch(`${API_BASE}/data-generator/jobs/${jobId}/cancel`, {
      method: 'POST',
    }).then(r => r.json()),
};

/**
 * 观测 API
 */
export const observabilityApi = {
  getGrafanaEmbed: ({ dashboardUid, from, to, refresh, variables = {} }) => {
    const params = new URLSearchParams();
    params.set('dashboardUid', dashboardUid);
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    if (refresh) params.set('refresh', refresh);
    Object.entries(variables).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params.set(`var-${key}`, value);
      }
    });
    return fetch(`${API_BASE}/observability/grafana/embed-url?${params.toString()}`)
      .then(r => r.json());
  },
  listDashboards: () =>
    fetch(`${API_BASE}/observability/grafana/dashboards`).then(r => r.json()),
};

export default useApi;
