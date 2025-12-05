import React, { useEffect, useMemo, useState } from 'react';

/**
 * 通用参数配置弹窗：使用 JSON 编辑器承载控制面返回的默认参数。
 */
export default function ScenarioConfigModal({
  operation,
  defaultPayload,
  isOpen,
  onClose,
  onStart,
  isRunning,
}) {
  const [editorValue, setEditorValue] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const template = useMemo(() => sanitizePayload(defaultPayload), [defaultPayload]);
  const hasTemplate = Object.keys(template || {}).length > 0;

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    setEditorValue(formatPayload(template));
    setError(null);
    setSubmitting(false);
  }, [isOpen, template]);

  if (!isOpen || !operation) {
    return null;
  }

  const handleReset = () => {
    setEditorValue(formatPayload(template));
    setError(null);
  };

  const handleStart = async () => {
    let payload;
    const text = editorValue.trim();
    if (text.length === 0 || text === '{}' || text === '{ }') {
      payload = undefined;
    } else {
      try {
        payload = JSON.parse(editorValue);
      } catch (err) {
        setError(`JSON 格式错误: ${err.message}`);
        return;
      }
    }
    setSubmitting(true);
    try {
      await onStart(payload);
      onClose();
    } catch (err) {
      setError(err.message || '启动失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content scenario-config-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2 className="modal-title">
            <span className="modal-icon">⚙️</span>
            {operation.label} - 参数配置
          </h2>
          <button className="modal-close" onClick={onClose} title="关闭">
            ✕
          </button>
        </div>

        <div className="modal-body config-modal-body">
          <p className="config-hint">
            所有默认参数由控制面提供（后端可透传 Load Executor 定义）。如需调整，请直接修改 JSON 内容。
          </p>

          {hasTemplate ? (
            <div className="config-template">
              <div className="config-template-header">
                <span>参数模板</span>
                <button className="link-btn" onClick={handleReset} type="button">
                  恢复默认
                </button>
              </div>
              <textarea
                className="json-editor"
                value={editorValue}
                onChange={(e) => {
                  setEditorValue(e.target.value);
                  setError(null);
                }}
                rows={Math.min(18, Math.max(8, editorValue.split('\n').length + 2))}
                spellCheck={false}
              />
            </div>
          ) : (
            <div className="config-template empty">
              <p>该操作当前没有可配置参数。直接点击下方按钮即可启动。</p>
            </div>
          )}

          {error && <div className="config-error">{error}</div>}

          <div className="modal-actions">
            <button className="btn ghost" onClick={onClose} disabled={submitting}>
              取消
            </button>
            <button
              className="btn primary"
              onClick={handleStart}
              disabled={submitting || isRunning}
            >
              {submitting ? '启动中...' : '启动任务'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function sanitizePayload(payload) {
  if (!payload || typeof payload !== 'object') {
    return {};
  }
  try {
    return JSON.parse(JSON.stringify(payload));
  } catch {
    return {};
  }
}

function formatPayload(payload) {
  if (!payload || Object.keys(payload).length === 0) {
    return '{\n  \n}';
  }
  try {
    return JSON.stringify(payload, null, 2);
  } catch {
    return '{\n  \n}';
  }
}
