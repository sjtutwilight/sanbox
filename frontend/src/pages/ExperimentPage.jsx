import React, { useState, useEffect, useCallback } from 'react';
import { experimentApi } from '../hooks/useApi';
import ExperimentDetail from '../components/ExperimentCard';
import { GrafanaPanels } from '../components/GrafanaPanel';
import ExperimentInfoModal from '../components/ExperimentInfoModal';
import { ExperimentLogModal, LogButton } from '../components/ExperimentLogModal';

/**
 * 实验管理页面
 */
export function ExperimentPage({ experimentId }) {
  const [experiment, setExperiment] = useState(null);
  const [taskStatuses, setTaskStatuses] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [showGrafana, setShowGrafana] = useState(true);
  const [experimentRunId, setExperimentRunId] = useState(null);
  const [showInfoModal, setShowInfoModal] = useState(false);
  const [showLogModal, setShowLogModal] = useState(false);

  // 加载实验详情
  useEffect(() => {
    if (!experimentId) return;
    
    setLoading(true);
    setError(null);
    
    experimentApi.get(experimentId)
      .then(data => {
        setExperiment(data);
      })
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [experimentId]);

  // 重新生成实验运行ID
  useEffect(() => {
    if (!experimentId) {
      setExperimentRunId(null);
      return;
    }
    const runId = (window.crypto?.randomUUID?.() ?? `${experimentId}-${Date.now()}`);
    setExperimentRunId(runId);
  }, [experimentId]);

  // 启动操作
  const handleStartOperation = useCallback(async (expId, groupId, opId, overrides, runId) => {
    try {
      const currentRunId = runId || experimentRunId;
      const task = await experimentApi.startOperation(expId, groupId, opId, overrides, currentRunId);
      const key = `${expId}:${groupId}:${opId}`;
      setTaskStatuses(prev => ({ ...prev, [key]: task }));
    } catch (err) {
      alert('启动操作失败: ' + err.message);
    }
  }, [experimentRunId]);

  // 停止操作
  const handleStopOperation = useCallback(async (expId, groupId, opId, runId) => {
    try {
      const currentRunId = runId || experimentRunId;
      const task = await experimentApi.stopOperation(expId, groupId, opId, currentRunId);
      const key = `${expId}:${groupId}:${opId}`;
      setTaskStatuses(prev => ({ ...prev, [key]: task }));
    } catch (err) {
      alert('停止操作失败: ' + err.message);
    }
  }, [experimentRunId]);

  const handleTaskUpdate = useCallback((task) => {
    const key = `${task.experimentId || experimentId}:${task.groupId}:${task.operationId}`;
    setTaskStatuses(prev => ({ ...prev, [key]: task }));
  }, [experimentId]);

  if (loading) {
    return (
      <div className="page-loading">
        <div className="loading-spinner"></div>
        <div className="loading-text">加载中...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page-error">
        <div className="error-icon">❌</div>
        <div className="error-text">{error}</div>
      </div>
    );
  }

  return (
    <div className="experiment-page-layout">
      {/* 左侧：实验详情和操作 */}
      <div className="experiment-left-panel">
        <ExperimentDetail
          experiment={experiment}
          taskStatuses={taskStatuses}
          experimentRunId={experimentRunId}
          onStartOperation={handleStartOperation}
          onStopOperation={handleStopOperation}
          onTaskUpdate={handleTaskUpdate}
          onShowInfo={() => setShowInfoModal(true)}
        />
      </div>
      
      {/* 右侧：监控面板 */}
      <div className="experiment-right-panel">
        <div className="monitoring-header">
          <h3 className="monitoring-title">
            <span className="monitoring-icon">📊</span>
            实验监控
          </h3>
          <div className="monitoring-actions">
            <LogButton 
              experimentRunId={experimentRunId}
              onClick={() => setShowLogModal(true)}
            />
            <button 
              className="toggle-monitoring-btn"
              onClick={() => setShowGrafana(!showGrafana)}
              title={showGrafana ? '隐藏监控' : '显示监控'}
            >
              {showGrafana ? '▼ 收起' : '▶ 展开'}
            </button>
          </div>
        </div>
        
        {showGrafana && (
          <div className="monitoring-content">
            <GrafanaPanels experimentRunId={experimentRunId} />
          </div>
        )}
      </div>

      {/* 实验介绍浮窗 */}
      <ExperimentInfoModal
        experiment={experiment}
        isOpen={showInfoModal}
        onClose={() => setShowInfoModal(false)}
      />

      {/* 实验日志浮窗 */}
      <ExperimentLogModal
        experimentRunId={experimentRunId}
        isOpen={showLogModal}
        onClose={() => setShowLogModal(false)}
      />
    </div>
  );
}

export default ExperimentPage;
