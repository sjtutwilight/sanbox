import React from 'react';
import OperationButton from './OperationButton';
import { ProgressBar, ContinuousProgress } from './ProgressBar';
import ScenarioConfigPanel from './ScenarioConfigPanel';

/**
 * 实验组卡片组件
 */
export function ExperimentGroupCard({ 
  group, 
  experimentId,
  experimentRunId,
  taskStatuses,
  onStartOperation,
  onStopOperation,
  onTaskUpdate,
}) {
  return (
    <div className="group-card">
      <div className="group-header">
        <h3 className="group-name">{group.name}</h3>
        <p className="group-desc">{group.description}</p>
      </div>
      
      <div className="operations-grid">
        {group.operations?.map((op) => {
          const taskKey = `${experimentId}:${group.id}:${op.id}`;
          const task = taskStatuses[taskKey];
          
          // 可配置操作使用配置面板（场景或常规）
          if (op.configurable) {
            return (
              <div key={op.id} className="operation-wrapper">
                <ScenarioConfigPanel
                  operation={op}
                  experimentId={experimentId}
                  experimentRunId={experimentRunId}
                  groupId={group.id}
                  task={task}
                  onTaskUpdate={onTaskUpdate}
                />
              </div>
            );
          }
          
          return (
            <div key={op.id} className="operation-wrapper">
              <OperationButton
                operation={op}
                task={task}
                onStart={(payload) => onStartOperation(experimentId, group.id, op.id, payload, experimentRunId)}
                onStop={() => onStopOperation(experimentId, group.id, op.id, experimentRunId)}
              />
              
              {/* 显示进度信息 */}
              {task && task.status !== 'IDLE' && (
                <div className="operation-progress">
                  {op.type === 'INIT_DATA' ? (
                    <ProgressBar 
                      value={task.written || 0} 
                      max={task.target || 0} 
                    />
                  ) : (
                    <ContinuousProgress task={task} />
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

/**
 * 实验详情组件
 */
export function ExperimentDetail({ 
  experiment, 
  taskStatuses,
  experimentRunId,
  onStartOperation,
  onStopOperation,
  onTaskUpdate,
  onShowInfo,
}) {
  if (!experiment) {
    return (
      <div className="experiment-empty">
        <div className="empty-icon">🧪</div>
        <div className="empty-text">请从左侧选择一个实验</div>
      </div>
    );
  }
  
  return (
    <div className="experiment-detail">
      {/* 实验头部 */}
      <header className="experiment-header">
        <div className="experiment-header-top">
          <div className="experiment-title-group">
            <h1 className="experiment-title">{experiment.name}</h1>
            <p className="experiment-desc">{experiment.description}</p>
          </div>
          <button className="info-button" onClick={onShowInfo} title="查看实验介绍">
            <span className="info-button-icon">📖</span>
            <span className="info-button-text">实验介绍</span>
          </button>
        </div>
        {experimentRunId && (
          <div className="experiment-run-id">
            <span>实验运行 ID：</span>
            <code>{experimentRunId}</code>
          </div>
        )}
      </header>
      
      {/* 实验组 */}
      <section className="experiment-section groups-section">
        <h2 className="section-title">
          <span className="section-icon">📊</span>
          实验组
        </h2>
        <div className="groups-container">
          {experiment.groups?.map((group) => (
            <ExperimentGroupCard
              key={group.id}
              group={group}
              experimentId={experiment.id}
              experimentRunId={experimentRunId}
              taskStatuses={taskStatuses}
              onStartOperation={onStartOperation}
              onStopOperation={onStopOperation}
              onTaskUpdate={onTaskUpdate}
            />
          ))}
        </div>
      </section>
    </div>
  );
}

export default ExperimentDetail;
