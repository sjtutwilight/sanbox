import React, { useState } from 'react';

/**
 * Grafana内嵌面板组件
 */
export function GrafanaPanel({ 
  dashboardUid = 'experiment-monitor',
  title = '实验监控',
  height = '600px',
  refresh = '5s',
  from = 'now-15m',
  to = 'now',
  variables = {}
}) {
  const [collapsed, setCollapsed] = useState(false);
  
  // 直接构建 Grafana URL
  const buildGrafanaUrl = (kiosk = true) => {
    const variableQuery = Object.entries(variables || {})
      .filter(([, value]) => value !== undefined && value !== null && value !== '')
      .map(([key, value]) => `var-${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
      .join('&');
    
    const kioskParam = kiosk ? '&kiosk=tv' : '';
    const baseUrl = `http://localhost:3000/d/${dashboardUid}?orgId=1&refresh=${refresh}&from=${from}&to=${to}${kioskParam}`;
    return variableQuery ? `${baseUrl}&${variableQuery}` : baseUrl;
  };
  
  const iframeUrl = buildGrafanaUrl(true);
  const externalUrl = buildGrafanaUrl(false);
  
  return (
    <div className="grafana-panel-container">
      <div className="grafana-panel-header">
        <h3 className="grafana-panel-title">
          <span className="icon">📊</span>
          {title}
        </h3>
        <div className="grafana-panel-actions">
          <button 
            className="grafana-btn"
            onClick={() => window.open(externalUrl, '_blank')}
            title="在新窗口打开"
          >
            <span>🔗</span> 新窗口
          </button>
          <button 
            className="grafana-btn"
            onClick={() => setCollapsed(!collapsed)}
            title={collapsed ? '展开' : '收起'}
          >
            {collapsed ? '▼ 展开' : '▲ 收起'}
          </button>
        </div>
      </div>
      
      {!collapsed && (
        <div className="grafana-panel-content">
          <iframe
            src={iframeUrl}
            width="100%"
            height={height}
            frameBorder="0"
            title={title}
            className="grafana-iframe"
          />
          <div className="grafana-panel-footer">
            <span className="grafana-hint">
              💡 提示：图表每 {refresh} 自动刷新，时间范围 {from} 至 {to}
            </span>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * Dashboard 配置
 * UID 必须与 grafana/provisioning/dashboards/*.json 中的 uid 字段一致
 */
const DASHBOARDS = {
  jvm: {
    uid: 'jvm-micrometer-dashboard',
    title: 'JVM 监控',
    icon: '☕',
    description: 'JVM 内存、GC、线程池状态'
  },
  kafka: {
    uid: 'kafka-obsv',
    title: 'Kafka 监控',
    icon: '📨',
    description: 'Kafka 吞吐、延迟、消费者 Lag'
  },
  flink: {
    uid: 'flink-obsv',
    title: 'Flink 监控',
    icon: '⚡',
    description: 'Flink 作业状态、Checkpoint、背压'
  },
  redis: {
    uid: 'redis-high-level',
    title: 'Redis 监控',
    icon: '🔴',
    description: 'Redis SLO、命中率、TTL 与热点风险'
  },
  mysql: {
    uid: 'mysql-high-level',
    title: 'MySQL 监控',
    icon: '🐬',
    description: 'MySQL QPS/TPS、锁等待、缓冲池与临时表'
  },
  logs: {
    uid: 'logs-observability',
    title: '日志监控',
    icon: '📋',
    description: '应用日志、错误追踪'
  }
};

/**
 * 多面板组合组件（带 Tab 切换）
 */
export function GrafanaPanels({ experimentRunId }) {
  const [activeTab, setActiveTab] = useState('jvm');
  
  const activeDashboard = DASHBOARDS[activeTab];
  
  // 根据不同的 dashboard 设置不同的变量
  let variables = {};
  if (activeTab === 'logs') {
    variables = { experiment_id: experimentRunId || '' };
  } else if (activeTab === 'mysql') {
    variables = { 
      host: 'mysql:3306',
      interval: '$__auto'
    };
  } else if (activeTab === 'jvm') {
    variables = {
      application: 'load-executor',
      instance: 'host.docker.internal:18082',
      jvm_memory_pool_heap: '$__all',
      jvm_memory_pool_nonheap: '$__all',
      jvm_buffer_pool: '$__all'
    };
  } else if (activeTab === 'kafka') {
    variables = {};
  } else if (activeTab === 'flink') {
    variables = {};
  }
  
  return (
    <div className="grafana-panels-wrapper">
      <div className="grafana-panel-container">
        {/* Dashboard Tab 切换 */}
        <div className="grafana-tabs">
          {Object.entries(DASHBOARDS).map(([key, dashboard]) => (
            <button
              key={key}
              className={`grafana-tab ${activeTab === key ? 'active' : ''}`}
              onClick={() => setActiveTab(key)}
              title={dashboard.description}
            >
              <span className="tab-icon">{dashboard.icon}</span>
              <span className="tab-label">{dashboard.title}</span>
            </button>
          ))}
        </div>
        
        {/* 当前激活的 Dashboard */}
        <GrafanaPanel 
          key={activeTab} // 强制重新加载
          dashboardUid={activeDashboard.uid}
          title={activeDashboard.title}
          height="750px"
          refresh="5s"
          from="now-15m"
          to="now"
          variables={variables}
        />
      </div>
    </div>
  );
}

export default GrafanaPanel;
