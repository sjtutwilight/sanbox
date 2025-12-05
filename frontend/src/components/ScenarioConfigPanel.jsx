import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { experimentApi } from '../hooks/useApi';
import StatusBadge from './StatusBadge';
import { ContinuousProgress } from './ProgressBar';
import ScenarioConfigModal from './ScenarioConfigModal';

/**
 * 场景参数配置面板（紧凑按钮模式）
 */
export function ScenarioConfigPanel({ 
  operation, 
  experimentId, 
  experimentRunId,
  groupId,
  task: externalTask,
  onTaskUpdate 
}) {
  const [showConfigModal, setShowConfigModal] = useState(false);
  const [task, setTask] = useState(externalTask);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setTask(externalTask);
  }, [externalTask]);

  // 轮询任务状态（仅在运行中时轮询，避免闪烁）
  useEffect(() => {
    if (!task || !task.id) return;
    // 只有运行中和停止中的任务才轮询
    if (!['RUNNING', 'STOPPING', 'PENDING'].includes(task.status)) return;
    
    const timer = setInterval(() => {
      experimentApi.getOperationStatus(experimentId, groupId, operation.id, experimentRunId)
        .then(data => {
          // 只有状态真正变化时才更新，减少不必要的重渲染
          if (JSON.stringify(data) !== JSON.stringify(task)) {
            setTask(data);
            onTaskUpdate?.(data);
          }
        })
        .catch(console.error);
    }, 2000); // 增加轮询间隔到2秒，减少闪烁
    
    return () => clearInterval(timer);
  }, [task?.status, task?.id, experimentId, experimentRunId, groupId, operation.id, onTaskUpdate]);

  const defaultPayload = useMemo(() => buildDefaultPayload(operation), [operation]);
  const hasConfigOverrides = Object.keys(defaultPayload || {}).length > 0;
  const isConfigurable = Boolean(operation?.configurable || hasConfigOverrides);

  const handleStartWithParams = useCallback(async (params) => {
    setLoading(true);
    try {
      const payload = params && Object.keys(params).length > 0 ? params : undefined;
      const result = await experimentApi.startOperation(
        experimentId,
        groupId,
        operation.id,
        payload,
        experimentRunId
      );
      setTask(result);
      onTaskUpdate?.(result);
      return result;
    } catch (err) {
      alert('启动失败: ' + err.message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, [experimentId, experimentRunId, groupId, operation.id, onTaskUpdate]);

  const handleStop = async () => {
    if (!task || !task.id) return;
    setLoading(true);
    try {
      const result = await experimentApi.stopOperation(
        experimentId,
        groupId,
        operation.id,
        experimentRunId
      );
      setTask(result);
      onTaskUpdate?.(result);
    } catch (err) {
      alert('停止失败: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const isRunning = task?.status === 'RUNNING';
  
  // 操作类型配置
  const operationConfig = {
    INIT_DATA: { icon: '📦', color: 'cyan' },
    INIT_MYSQL: { icon: '🗄️', color: 'cyan' },
    INIT_REDIS: { icon: '💾', color: 'cyan' },
    CONTINUOUS_WRITE: { icon: '✏️', color: 'green' },
    CONTINUOUS_READ: { icon: '📖', color: 'blue' },
  };
  
  const config = operationConfig[operation.type] || { icon: '🔥', color: 'cyan' };

  return (
    <>
      <div className={`operation-card compact ${config.color}`}>
        <div className="operation-header">
          <span className="operation-icon">{config.icon}</span>
          <span className="operation-label">{operation.label}</span>
          {task && <StatusBadge status={task.status} />}
        </div>
        
        <div className="operation-hint">{operation.hint}</div>
        
        <div className="operation-actions">
          {isRunning ? (
            <button
              className="operation-btn stop"
              onClick={handleStop}
              disabled={loading}
            >
              停止压测
            </button>
          ) : (
            <button
              className="operation-btn start"
              onClick={() => (isConfigurable ? setShowConfigModal(true) : handleStartWithParams())}
              disabled={loading}
            >
              {isConfigurable ? '配置并启动' : '立即启动'}
            </button>
          )}
        </div>
      </div>

      {/* 运行状态 */}
      {task && task.status !== 'IDLE' && (
        <div className="operation-progress">
          <ContinuousProgress task={task} />
        </div>
      )}

      {/* 参数配置浮窗 */}
      <ScenarioConfigModal
        operation={operation}
        defaultPayload={defaultPayload}
        isOpen={showConfigModal}
        onClose={() => setShowConfigModal(false)}
        onStart={handleStartWithParams}
        isRunning={isRunning}
      />
    </>
  );
}

export default ScenarioConfigPanel;

function buildDefaultPayload(operation) {
  if (!operation) {
    return {};
  }
  const source =
    operation.parameters ??
    operation.request ??
    operation.readConfig ??
    operation.scenarioParams ??
    operation.cacheConfig?.mysqlInit ??
    operation.cacheConfig?.redisInit ??
    {};
  if (!source) {
    return {};
  }
  try {
    return JSON.parse(JSON.stringify(source));
  } catch {
    return {};
  }
}
