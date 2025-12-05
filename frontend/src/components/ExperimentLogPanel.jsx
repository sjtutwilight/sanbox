import React, { useEffect, useState } from 'react';

/**
 * 实验运行日志列表（后端代理 Loki）
 */
export default function ExperimentLogPanel({ experimentRunId }) {
  const [appLogs, setAppLogs] = useState([]);
  const [containerErrors, setContainerErrors] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!experimentRunId) {
      setAppLogs([]);
      setContainerErrors([]);
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
  }, [experimentRunId]);

  if (!experimentRunId) {
    return (
      <div className="experiment-logs card">
        <div className="card-title">实验实时日志</div>
        <div className="logs-empty">请选择一个实验以生成运行 ID</div>
      </div>
    );
  }

  return (
    <div className="experiment-logs card">
      <div className="card-title">
        实验实时日志
        <span className="log-run-id">ID: <code>{experimentRunId}</code></span>
      </div>
      {error && <div className="logs-error">日志加载失败：{error}</div>}

      <section className="logs-section">
        <h4>Java 业务日志（抽样）</h4>
        <div className="logs-container">
          {appLogs.length === 0 ? (
            <div className="logs-empty">暂无业务日志</div>
          ) : (
            appLogs.map((log, idx) => (
              <div key={`${log.timestamp}-${idx}`} className="log-item">
                <span className="log-time">[{new Date(log.timestamp).toLocaleTimeString()}]</span>
                <span className="log-msg">{log.line}</span>
                {log.labels?.level && <span className={`log-level level-${log.labels.level.toLowerCase()}`}>{log.labels.level}</span>}
              </div>
            ))
          )}
        </div>
      </section>

      <section className="logs-section">
        <h4>容器错误日志（Redis/MySQL）</h4>
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
    </div>
  );
}
