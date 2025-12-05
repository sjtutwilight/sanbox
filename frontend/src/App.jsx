import { useEffect, useState, useCallback } from 'react';
import { experimentApi } from './hooks/useApi';
import Sidebar from './components/Sidebar';
import ExperimentPage from './pages/ExperimentPage';
import DataGenPage from './pages/DataGenPage';
import './styles.css';

/**
 * Redis 压测实验平台主应用
 */
function App() {
  // 导航状态
  const [activeNav, setActiveNav] = useState('experiments');
  
  // 实验列表
  const [experiments, setExperiments] = useState([]);
  const [selectedExperimentId, setSelectedExperimentId] = useState(null);
  
  // 加载状态
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // 加载实验列表
  useEffect(() => {
    setLoading(true);
    setError(null);
    
    experimentApi.list()
      .then(data => {
      setExperiments(data);
        // 默认选中第一个实验
        if (data.length > 0 && !selectedExperimentId) {
          setSelectedExperimentId(data[0].id);
        }
      })
      .catch(err => {
        setError('加载实验列表失败: ' + err.message);
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  // 处理实验选择
  const handleSelectExperiment = useCallback((id) => {
    setSelectedExperimentId(id);
  }, []);

  // 处理导航切换
  const handleNavChange = useCallback((nav) => {
    setActiveNav(nav);
  }, []);

  // 渲染主内容区
  const renderContent = () => {
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

    switch (activeNav) {
      case 'experiments':
        return <ExperimentPage experimentId={selectedExperimentId} />;
      case 'datagen':
        return <DataGenPage />;
      default:
        return <ExperimentPage experimentId={selectedExperimentId} />;
    }
  };

  return (
    <div className="app-layout">
      <Sidebar
        experiments={experiments}
        selectedId={selectedExperimentId}
        onSelect={handleSelectExperiment}
        activeNav={activeNav}
        onNavChange={handleNavChange}
      />
      
      <main className="main-content">
        {renderContent()}
      </main>
    </div>
  );
}

export default App;
