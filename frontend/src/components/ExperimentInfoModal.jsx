import React from 'react';

/**
 * 实验介绍浮窗组件
 * 显示架构概览、风险提示、实验目标、观察要点、观测指标、推荐命令等信息
 */
export function ExperimentInfoModal({ experiment, isOpen, onClose }) {
  if (!isOpen || !experiment) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content experiment-info-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2 className="modal-title">
            <span className="modal-icon">📖</span>
            实验介绍
          </h2>
          <button className="modal-close" onClick={onClose} title="关闭">
            ✕
          </button>
        </div>
        
        <div className="modal-body">
          {/* 架构概览 */}
          {experiment.architecture && (
            <section className="info-section">
              <h3 className="info-section-title">
                <span className="info-icon">🏗️</span>
                架构概览
              </h3>
              <p className="info-content">{experiment.architecture}</p>
            </section>
          )}
          
          {/* 风险提示 */}
          {experiment.riskWarnings && experiment.riskWarnings.length > 0 && (
            <section className="info-section">
              <h3 className="info-section-title">
                <span className="info-icon">⚠️</span>
                风险提示
              </h3>
              <ul className="info-list">
                {experiment.riskWarnings.map((risk, idx) => (
                  <li key={idx} className="info-list-item">{risk}</li>
                ))}
              </ul>
            </section>
          )}
          
          {/* 实验目标 */}
          {experiment.objective && (
            <section className="info-section">
              <h3 className="info-section-title">
                <span className="info-icon">🎯</span>
                实验目标
              </h3>
              <p className="info-content">{experiment.objective}</p>
            </section>
          )}
          
          {/* 观察要点 */}
          {experiment.observePoints && experiment.observePoints.length > 0 && (
            <section className="info-section">
              <h3 className="info-section-title">
                <span className="info-icon">👁️</span>
                观察要点
              </h3>
              <ul className="info-list">
                {experiment.observePoints.map((point, idx) => (
                  <li key={idx} className="info-list-item">{point}</li>
                ))}
              </ul>
            </section>
          )}
          
          {/* 观测指标 */}
          {experiment.metricsToWatch && experiment.metricsToWatch.length > 0 && (
            <section className="info-section">
              <h3 className="info-section-title">
                <span className="info-icon">📈</span>
                观测指标
              </h3>
              <ul className="info-list">
                {experiment.metricsToWatch.map((metric, idx) => (
                  <li key={idx} className="info-list-item">{metric}</li>
                ))}
              </ul>
            </section>
          )}
          
          {/* 推荐命令 */}
          {experiment.recommendations && experiment.recommendations.length > 0 && (
            <section className="info-section">
              <h3 className="info-section-title">
                <span className="info-icon">💻</span>
                推荐命令
              </h3>
              <div className="commands-list">
                {experiment.recommendations.map((cmd, idx) => (
                  <code key={idx} className="command-item">{cmd}</code>
                ))}
              </div>
            </section>
          )}
        </div>
      </div>
    </div>
  );
}

export default ExperimentInfoModal;

