import React, { useState, useEffect, useCallback } from 'react';
import { configApi, dataGenApi } from '../hooks/useApi';
import { ProgressBar } from '../components/ProgressBar';
import StatusBadge from '../components/StatusBadge';

const defaultForm = {
  dataSource: 'REDIS',
  domain: 'USER_POSITION',
  pattern: 'USER_POSITION_PER_USER_HASH',
  recordCount: '',
  valueSizeBytes: '',
  batchSize: '',
  ttlSeconds: '',
  keyPrefix: '',
};

/**
 * 数据生成页面
 */
export function DataGenPage() {
  const [config, setConfig] = useState(null);
  const [form, setForm] = useState(defaultForm);
  const [job, setJob] = useState(null);
  const [logs, setLogs] = useState([]);

  // 加载配置
  useEffect(() => {
    configApi.get()
      .then(data => {
        setConfig(data);
        // 设置默认值
        if (data.defaults) {
          setForm(prev => ({
            ...prev,
            valueSizeBytes: data.defaults.valueSizeBytes?.toString() || '',
            batchSize: data.defaults.batchSize?.toString() || '',
            ttlSeconds: data.defaults.ttlSeconds?.toString() || '',
            keyPrefix: data.defaults.keyPrefix || '',
          }));
        }
      })
      .catch(err => appendLog('加载配置失败: ' + err.message, 'error'));
  }, []);

  // 轮询任务状态
  useEffect(() => {
    if (!job || !job.id) return;
    if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(job.status)) return;
    
    const timer = setInterval(() => {
      dataGenApi.getJob(job.id)
        .then(data => {
          setJob(data);
          if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(data.status)) {
            appendLog(`任务结束: ${data.status}`, data.status === 'COMPLETED' ? 'success' : 'error');
          }
        })
        .catch(err => appendLog('轮询失败: ' + err.message, 'error'));
    }, 1500);
    
    return () => clearInterval(timer);
  }, [job]);

  const appendLog = useCallback((msg, type = 'info') => {
    const ts = new Date().toLocaleTimeString();
    setLogs(prev => [{ ts, msg, type }, ...prev.slice(0, 99)]);
  }, []);

  const updateForm = (key, value) => {
    setForm(prev => ({ ...prev, [key]: value }));
    
    // 切换业务域时更新模式
    if (key === 'domain' && config?.patterns) {
      const patterns = config.patterns[value];
      if (patterns && patterns.length > 0) {
        setForm(prev => ({ ...prev, pattern: patterns[0].value }));
      }
    }
  };

  const handleStart = async () => {
    const payload = {
      dataSource: form.dataSource,
      domain: form.domain,
      pattern: form.pattern,
      recordCount: form.recordCount ? Number(form.recordCount) : null,
      valueSizeBytes: form.valueSizeBytes ? Number(form.valueSizeBytes) : null,
      batchSize: form.batchSize ? Number(form.batchSize) : null,
      ttlSeconds: form.ttlSeconds ? Number(form.ttlSeconds) : null,
      keyPrefix: form.keyPrefix || null,
      overwrite: true,
    };
    
    appendLog('发送创建任务请求...');
    
    try {
      const data = await dataGenApi.createJob(payload);
      setJob(data);
      appendLog('任务创建成功，开始轮询进度', 'success');
    } catch (err) {
      appendLog('创建任务失败: ' + err.message, 'error');
    }
  };

  const handleCancel = async () => {
    if (!job || !job.id) {
      appendLog('没有正在运行的任务', 'warn');
      return;
    }
    
    appendLog('发送取消请求...');
    
    try {
      const data = await dataGenApi.cancelJob(job.id);
      setJob(data);
      appendLog('取消成功', 'success');
    } catch (err) {
      appendLog('取消失败: ' + err.message, 'error');
    }
  };

  const patterns = config?.patterns?.[form.domain] || [];
  const currentPattern = patterns.find(p => p.value === form.pattern);
  const pct = job && job.target > 0 ? Math.min(100, (job.written / job.target) * 100) : 0;

  return (
    <div className="datagen-page">
      <header className="page-header">
        <h1>数据生成工具</h1>
        <p className="page-desc">配置参数，快速生成测试数据到 Redis</p>
      </header>

      <div className="datagen-grid">
        {/* 配置区域 */}
        <section className="config-section card">
          <h2 className="card-title">生成配置</h2>
          
          <div className="form-grid">
            <div className="form-group">
              <label>数据源</label>
              <select 
                value={form.dataSource} 
                onChange={e => updateForm('dataSource', e.target.value)}
              >
                <option value="REDIS">Redis</option>
              </select>
            </div>
            
            <div className="form-group">
              <label>业务域</label>
              <select 
                value={form.domain} 
                onChange={e => updateForm('domain', e.target.value)}
              >
                {config?.domains?.map(d => (
                  <option key={d.value} value={d.value}>{d.label}</option>
                ))}
              </select>
            </div>
            
            <div className="form-group span-2">
              <label>生成模式</label>
              <select 
                value={form.pattern} 
                onChange={e => updateForm('pattern', e.target.value)}
              >
                {patterns.map(p => (
                  <option key={p.value} value={p.value}>{p.label}</option>
                ))}
              </select>
            </div>
          </div>
          
          {currentPattern && (
            <div className="pattern-hints">
              <span className="hint-tag">{currentPattern.hint}</span>
              <span className={`risk-tag ${currentPattern.risk?.includes('高风险') ? 'danger' : 'safe'}`}>
                {currentPattern.risk}
              </span>
            </div>
          )}
          
          <div className="form-grid">
            <div className="form-group">
              <label>记录条数</label>
              <input 
                type="number" 
                value={form.recordCount} 
                onChange={e => updateForm('recordCount', e.target.value)}
                placeholder="如 100000"
              />
            </div>
            
            <div className="form-group">
              <label>单条大小 (bytes)</label>
              <input 
                type="number" 
                value={form.valueSizeBytes} 
                onChange={e => updateForm('valueSizeBytes', e.target.value)}
                placeholder={config?.defaults?.valueSizeBytes}
              />
            </div>
            
            <div className="form-group">
              <label>批次大小</label>
              <input 
                type="number" 
                value={form.batchSize} 
                onChange={e => updateForm('batchSize', e.target.value)}
                placeholder={config?.defaults?.batchSize}
              />
            </div>
            
            <div className="form-group">
              <label>TTL (秒)</label>
              <input 
                type="number" 
                value={form.ttlSeconds} 
                onChange={e => updateForm('ttlSeconds', e.target.value)}
                placeholder={config?.defaults?.ttlSeconds}
              />
            </div>
            
            <div className="form-group span-2">
              <label>Key 前缀</label>
              <input 
                type="text" 
                value={form.keyPrefix} 
                onChange={e => updateForm('keyPrefix', e.target.value)}
                placeholder={config?.defaults?.keyPrefix}
              />
            </div>
          </div>
          
          <div className="form-actions">
            <button className="btn primary" onClick={handleStart}>启动生成</button>
            <button className="btn ghost" onClick={handleCancel}>取消任务</button>
          </div>
        </section>

        {/* 任务状态区域 */}
        <section className="status-section card">
          <h2 className="card-title">任务状态</h2>
          
          <div className="job-info">
            <div className="job-row">
              <span className="job-label">Job ID</span>
              <span className="job-value mono">{job?.id || '-'}</span>
            </div>
            <div className="job-row">
              <span className="job-label">状态</span>
              {job ? <StatusBadge status={job.status} /> : <span className="job-value">-</span>}
            </div>
          </div>
          
          <ProgressBar value={job?.written || 0} max={job?.target || 0} />
          
          <div className="job-stats">
            <div className="stat-item">
              <span className="stat-label">失败数</span>
              <span className="stat-value">{job?.failures || 0}</span>
            </div>
            <div className="stat-item">
              <span className="stat-label">开始时间</span>
              <span className="stat-value">{job?.startedAt ? new Date(job.startedAt).toLocaleString() : '-'}</span>
            </div>
            <div className="stat-item">
              <span className="stat-label">结束时间</span>
              <span className="stat-value">{job?.endedAt ? new Date(job.endedAt).toLocaleString() : '-'}</span>
            </div>
          </div>
          
          {job?.lastError && (
            <div className="job-error">
              <span className="error-label">最后错误:</span>
              <span className="error-msg">{job.lastError}</span>
            </div>
          )}
        </section>

        {/* 日志区域 */}
        <section className="logs-section card span-2">
          <h2 className="card-title">操作日志</h2>
          <div className="logs-container">
            {logs.length === 0 ? (
              <div className="logs-empty">暂无日志</div>
            ) : (
              logs.map((log, idx) => (
                <div key={idx} className={`log-item ${log.type}`}>
                  <span className="log-time">[{log.ts}]</span>
                  <span className="log-msg">{log.msg}</span>
                </div>
              ))
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

export default DataGenPage;

