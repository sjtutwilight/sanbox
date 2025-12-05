import React, { useMemo, useState } from 'react';
import StatusBadge from './StatusBadge';
import OperationParameterModal from './OperationParameterModal';

/**
 * 操作类型配置
 */
const operationTypeConfig = {
  INIT_DATA: {
    icon: '📦',
    actionLabel: '初始化',
    stopLabel: '取消',
    color: 'cyan',
  },
  CONTINUOUS_WRITE: {
    icon: '✏️',
    actionLabel: '启动写入',
    stopLabel: '停止写入',
    color: 'green',
  },
  CONTINUOUS_READ: {
    icon: '📖',
    actionLabel: '启动读取',
    stopLabel: '停止读取',
    color: 'blue',
  },
  INIT_MYSQL: {
    icon: '🗄️',
    actionLabel: '初始化 MySQL',
    stopLabel: '停止',
    color: 'cyan',
  },
  INIT_REDIS: {
    icon: '💾',
    actionLabel: '初始化 Redis',
    stopLabel: '停止',
    color: 'cyan',
  },
};

/**
 * 操作按钮组件
 */
export function OperationButton({ operation, task, onStart, onStop, disabled }) {
  const config = operationTypeConfig[operation.type] || operationTypeConfig.INIT_DATA;
  const isRunning = task?.status === 'RUNNING';
  const isCompleted = task?.status === 'COMPLETED';
  const hasStructuredParams = useMemo(() => {
    const hasParams = Array.isArray(operation.parameters) && operation.parameters.length > 0;
    const hasShape = Boolean(operation.loadShape);
    return hasParams || hasShape;
  }, [operation.parameters, operation.loadShape]);
  const [showParamsModal, setShowParamsModal] = useState(false);

  const handleStartClick = () => {
    if (isRunning) {
      onStop?.();
      return;
    }
    if (hasStructuredParams) {
      setShowParamsModal(true);
      return;
    }
    onStart?.();
  };

  const handleSubmitParams = (values) => {
    setShowParamsModal(false);
    onStart?.(values);
  };

  const isDisabled = disabled || (operation.type === 'INIT_DATA' && isCompleted);

  return (
    <>
      <div className={`operation-card ${config.color}`}>
        <div className="operation-header">
          <span className="operation-icon">{config.icon}</span>
          <span className="operation-label">{operation.label}</span>
          {task && <StatusBadge status={task.status} />}
        </div>

        <div className="operation-hint">{operation.hint}</div>

        <div className="operation-actions">
          <button
            className={`operation-btn ${isRunning ? 'stop' : 'start'} ${config.color}`}
            onClick={handleStartClick}
            disabled={isDisabled}
          >
            {isRunning ? config.stopLabel : config.actionLabel}
          </button>
        </div>
      </div>

      {hasStructuredParams && (
        <OperationParameterModal
          operation={operation}
          isOpen={showParamsModal}
          onClose={() => setShowParamsModal(false)}
          onSubmit={handleSubmitParams}
        />
      )}
    </>
  );
}

export default OperationButton;
