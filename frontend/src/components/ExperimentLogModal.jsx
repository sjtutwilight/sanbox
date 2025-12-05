import React, { useEffect, useState } from 'react';

/**
 * 实验日志浮窗组件
 * 显示实验运行日志和容器错误日志，支持错误红点提示
 */
export function ExperimentLogModal({ experimentRunId, isOpen, onClose }) {
  const [appLogs, setAppLogs] = useState([]);
  const [containerErrors, setContainerErrors] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!experimentRunId || !isOpen) {
      return;
    }
    
    let cancelled = false;

    const fetchLogs = () => {
      fetch(`/api/logs?experimentId=${encodeURIComponent(experimentRunId)}&limit=200&rangeSeconds=900`)
        .then(async (res) => {
          if (!res.ok) {
            const text = await res.text();
            throw new Error(text || res.statusText);
          }
          return res.json();
        })
        .then((data) => {
          if (!cancelled && data) {
            setAppLogs(data.appLogs || []);
            setContainerErrors(data.containerErrors || []);
            setError(null);
          }
        })
        .catch((err) => {
          if (!cancelled) {
            setError(err.message);
          }
        });
    };

    fetchLogs();
    const timer = setInterval(fetchLogs, 5000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [experimentRunId, isOpen]);

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content experiment-log-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2 className="modal-title">
            <span className="modal-icon">📋</span>
            实验实时日志
            {experimentRunId && (
              <span className="log-run-id-badge">ID: <code>{experimentRunId}</code></span>
            )}
          </h2>
          <button className="modal-close" onClick={onClose} title="关闭">
            ✕
          </button>
        </div>
        
        <div className="modal-body log-modal-body">
          {!experimentRunId ? (
            <div className="logs-empty">请选择一个实验以生成运行 ID</div>
          ) : (
            <>
              {error && <div className="logs-error">日志加载失败：{error}</div>}

              {/* Java 业务日志 */}
              <section className="logs-section">
                <h4 className="logs-section-title">Java 业务日志（抽样）</h4>
                <div className="logs-container">
                  {appLogs.length === 0 ? (
                    <div className="logs-empty">暂无业务日志</div>
                  ) : (
                    appLogs.map((log, idx) => (
                      <div key={`${log.timestamp}-${idx}`} className="log-item">
                        <span className="log-time">[{new Date(log.timestamp).toLocaleTimeString()}]</span>
                        <span className="log-msg">{log.line}</span>
                        {log.labels?.level && (
                          <span className={`log-level level-${log.labels.level.toLowerCase()}`}>
                            {log.labels.level}
                          </span>
                        )}
                      </div>
                    ))
                  )}
                </div>
              </section>

              {/* 容器错误日志 */}
              <section className="logs-section">
                <h4 className="logs-section-title">
                  容器错误日志（Redis/MySQL）
                  {containerErrors.length > 0 && (
                    <span className="error-badge">{containerErrors.length}</span>
                  )}
                </h4>
                <div className="logs-container">
                  {containerErrors.length === 0 ? (
                    <div className="logs-empty">暂无容器错误</div>
                  ) : (
                    containerErrors.map((err, idx) => (
                      <div key={`${err.container}-${idx}`} className="log-item error">
                        <span className="log-time">
                          [{new Date(err.lastSeen).toLocaleTimeString()} ×{err.count}]
                        </span>
                        <span className="log-msg">
                          [{err.container}] {err.message}
                        </span>
                      </div>
                    ))
                  )}
                </div>
              </section>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * 日志按钮组件（带红点提示）
 */
export function LogButton({ experimentRunId, onClick }) {
  const [hasError, setHasError] = useState(false);
  const [lastErrorCount, setLastErrorCount] = useState(0);

  useEffect(() => {
    if (!experimentRunId) {
      setHasError(false);
      setLastErrorCount(0);
      return;
    }

    let cancelled = false;

    const checkErrors = () => {
      fetch(`/api/logs?experimentId=${encodeURIComponent(experimentRunId)}&limit=10&rangeSeconds=900`)
        .then(async (res) => {
          if (!res.ok) return;
          return res.json();
        })
        .then((data) => {
          if (!cancelled && data) {
            const errorCount = data.containerErrors?.length || 0;
            if (errorCount > lastErrorCount) {
              setHasError(true);
            }
            setLastErrorCount(errorCount);
          }
        })
        .catch(() => {
          // 忽略错误
        });
    };

    checkErrors();
    const timer = setInterval(checkErrors, 5000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [experimentRunId, lastErrorCount]);

  const handleClick = () => {
    setHasError(false); // 点击后清除红点
    onClick();
  };

  return (
    <button className="log-button" onClick={handleClick} title="查看实验日志">
      <span className="log-button-icon">📋</span>
      <span className="log-button-text">实验日志</span>
      {hasError && <span className="red-dot"></span>}
    </button>
  );
}

export default ExperimentLogModal;

