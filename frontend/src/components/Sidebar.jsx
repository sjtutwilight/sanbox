import React, { useState } from 'react';

/**
 * 侧边栏导航组件
 */
export function Sidebar({ 
  experiments, 
  selectedId, 
  onSelect, 
  activeNav, 
  onNavChange 
}) {
  const [collapsed, setCollapsed] = useState(false);
  
  return (
    <aside className={`sidebar ${collapsed ? 'collapsed' : ''}`}>
      <div className="sidebar-header">
        <div className="logo">
          <span className="logo-icon">⚡</span>
          {!collapsed && <span className="logo-text">杨光的软件沙盒</span>}
        </div>
        <button 
          className="sidebar-toggle"
          onClick={() => setCollapsed(!collapsed)}
          title={collapsed ? '展开侧边栏' : '收起侧边栏'}
        >
          {collapsed ? '▶' : '◀'}
        </button>
      </div>
      
      <nav className="sidebar-nav">
        <div className="nav-section">
          {!collapsed && <div className="nav-section-title">实验管理</div>}
          {experiments.map((exp) => (
            <button
              key={exp.id}
              className={`nav-item ${selectedId === exp.id && activeNav === 'experiments' ? 'active' : ''}`}
              onClick={() => {
                onNavChange('experiments');
                onSelect(exp.id);
              }}
              title={collapsed ? exp.name : ''}
            >
              <span className="nav-item-icon">🧪</span>
              {!collapsed && (
                <div className="nav-item-content">
                  <span className="nav-item-title">{exp.name}</span>
                  <span className="nav-item-desc">{exp.groups?.length || 0} 个实验组</span>
                </div>
              )}
            </button>
          ))}
        </div>
        
        <div className="nav-section">
          {!collapsed && <div className="nav-section-title">工具</div>}
          <button
            className={`nav-item ${activeNav === 'datagen' ? 'active' : ''}`}
            onClick={() => onNavChange('datagen')}
            title={collapsed ? '数据生成' : ''}
          >
            <span className="nav-item-icon">🔧</span>
            {!collapsed && (
              <div className="nav-item-content">
                <span className="nav-item-title">数据生成</span>
                <span className="nav-item-desc">独立数据生成工具</span>
              </div>
            )}
          </button>
        </div>
      </nav>
      
      <div className="sidebar-footer">
        <a 
          href="http://localhost:3000" 
          target="_blank" 
          rel="noopener noreferrer"
          className="external-link"
          title={collapsed ? '打开 Grafana' : ''}
        >
          <span className="link-icon">📊</span>
          {!collapsed && <span>打开 Grafana</span>}
        </a>
      </div>
    </aside>
  );
}

export default Sidebar;

