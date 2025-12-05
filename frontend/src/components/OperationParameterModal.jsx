import React, { useEffect, useMemo, useState } from 'react';

function normalizeDefault(param) {
  let value = param.defaultValue;
  if (value === null || value === undefined) {
    if (param.type === 'boolean') {
      return false;
    }
    return '';
  }
  if (param.type === 'number') {
    return String(value);
  }
  return value;
}

function parseNumber(value) {
  if (value === '' || value === null || value === undefined) {
    return undefined;
  }
  const num = Number(value);
  return Number.isNaN(num) ? NaN : num;
}

function createShapeState(template = {}) {
  return {
    type: template.type || 'CONSTANT',
    qps: template.qps ?? '',
    concurrency: template.concurrency ?? '',
    durationSeconds: template.durationSeconds ?? '',
    paramsText:
      template.params && Object.keys(template.params).length > 0
        ? JSON.stringify(template.params, null, 2)
        : '',
  };
}

function buildLoadShapePayload(values) {
  const shapePayload = {
    type: values.type || 'CONSTANT',
  };
  const qpsNumber = parseNumber(values.qps);
  if (values.qps !== '' && Number.isNaN(qpsNumber)) {
    return { shapeValidationError: 'QPS 需为数字' };
  }
  const concurrencyNumber = parseNumber(values.concurrency);
  if (values.concurrency !== '' && Number.isNaN(concurrencyNumber)) {
    return { shapeValidationError: '并发需为数字' };
  }
  const durationNumber = parseNumber(values.durationSeconds);
  if (values.durationSeconds !== '' && Number.isNaN(durationNumber)) {
    return { shapeValidationError: '持续时间需为数字' };
  }
  shapePayload.qps = Number.isNaN(qpsNumber) ? undefined : qpsNumber;
  shapePayload.concurrency = Number.isNaN(concurrencyNumber) ? undefined : concurrencyNumber;
  shapePayload.durationSeconds = Number.isNaN(durationNumber) ? undefined : durationNumber;
  if (values.paramsText && values.paramsText.trim().length > 0) {
    try {
      shapePayload.params = JSON.parse(values.paramsText);
    } catch (err) {
      return { paramsError: '负载参数 JSON 解析失败' };
    }
  } else {
    shapePayload.params = undefined;
  }
  return { shapePayload };
}

export default function OperationParameterModal({ operation, isOpen, onClose, onSubmit }) {
  const parameters = useMemo(
    () => (Array.isArray(operation?.parameters) ? operation.parameters : []),
    [operation?.parameters]
  );
  const loadShapeTemplate = useMemo(
    () => operation?.loadShape || {},
    [operation?.loadShape]
  );
  const [values, setValues] = useState({});
  const [errors, setErrors] = useState({});
  const [shapeValues, setShapeValues] = useState(createShapeState(loadShapeTemplate));
  const [shapeError, setShapeError] = useState(null);
  const [shapeParamsError, setShapeParamsError] = useState(null);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    const initial = {};
    parameters.forEach((param) => {
      initial[param.name] = normalizeDefault(param);
    });
    setValues(initial);
    setErrors({});
    setShapeValues(createShapeState(loadShapeTemplate));
    setShapeError(null);
    setShapeParamsError(null);
  }, [isOpen, parameters, loadShapeTemplate]);

  if (!isOpen) {
    return null;
  }

  const handleChange = (name, value) => {
    setValues((prev) => ({ ...prev, [name]: value }));
  };

  const validate = () => {
    const nextErrors = {};
    parameters.forEach((param) => {
      const value = values[param.name];
      if (param.required) {
        if (
          value === null ||
          value === undefined ||
          value === '' ||
          (param.type === 'number' && Number.isNaN(parseNumber(value)))
        ) {
          nextErrors[param.name] = '必填';
          return;
        }
      }
      if (param.type === 'number' && value !== '' && value !== null && value !== undefined) {
        const parsed = parseNumber(value);
        if (Number.isNaN(parsed)) {
          nextErrors[param.name] = '请输入数字';
        }
      }
    });
    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const handleSubmit = () => {
    if (!validate()) {
      return;
    }
    const paramPayload = {};
    parameters.forEach((param) => {
      const value = values[param.name];
      if (value === '' || value === null || value === undefined) {
        return;
      }
      if (param.type === 'number') {
        const parsed = parseNumber(value);
        if (!Number.isNaN(parsed)) {
          paramPayload[param.name] = parsed;
        }
      } else if (param.type === 'boolean') {
        paramPayload[param.name] = Boolean(value);
      } else {
        paramPayload[param.name] = value;
      }
    });
    const { shapePayload, paramsError, shapeValidationError } = buildLoadShapePayload(shapeValues);
    if (paramsError) {
      setShapeParamsError(paramsError);
      return;
    }
    if (shapeValidationError) {
      setShapeError(shapeValidationError);
      return;
    }
    setShapeParamsError(null);
    setShapeError(null);
    onSubmit?.({
      parameters: paramPayload,
      loadShape: shapePayload,
    });
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content operation-params-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2 className="modal-title">
            <span className="modal-icon">⚙️</span>
            {operation?.label || operation?.id} - 参数配置
          </h2>
          <button className="modal-close" onClick={onClose} title="关闭">
            ✕
          </button>
        </div>

        <div className="modal-body config-modal-body">
          <div className="config-hint">
            根据 Load Executor 返回的参数要求填写配置，提交后即可发起该实验操作。
          </div>
          <div className="operation-params-grid">
            {parameters.map((param) => (
              <div className="operation-param-field" key={param.name}>
                <div className="param-label-row">
                  <div className="param-label">
                    <span>{param.label || param.name}</span>
                    {param.required && <span className="param-required-badge">必填</span>}
                  </div>
                  <span className="param-type-chip">
                    {param.type === 'number' ? '数字' : param.type === 'boolean' ? '布尔' : '文本'}
                  </span>
                </div>

                {renderInput(param, values[param.name], (val) => handleChange(param.name, val))}

                <div className="param-meta">
                  {param.description && <p className="param-description">{param.description}</p>}
                  {param.example !== null && param.example !== undefined && (
                    <span className="param-example">示例：{param.example.toString()}</span>
                  )}
                </div>

                {errors[param.name] && <div className="param-error">{errors[param.name]}</div>}
              </div>
            ))}
            {parameters.length === 0 && <div>该操作无需额外参数。</div>}
          </div>

          <div className="load-shape-section">
            <div className="section-header">
              <span className="section-title-text">负载形状</span>
              <span className="section-subtitle">
                控制 QPS、并发与持续时间，覆盖默认 Load Shape
              </span>
            </div>
            <div className="load-shape-grid">
              <div className="shape-field">
                <label>类型</label>
                <select
                  className="param-input"
                  value={shapeValues.type}
                  onChange={(e) => setShapeValues((prev) => ({ ...prev, type: e.target.value }))}
                >
                  {['CONSTANT', 'HOT_KEY', 'RAMP', 'BURST'].map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </div>
              <div className="shape-field">
                <label>QPS</label>
                <input
                  type="number"
                  className="param-input"
                  value={shapeValues.qps}
                  onChange={(e) => setShapeValues((prev) => ({ ...prev, qps: e.target.value }))}
                  placeholder="目标 QPS"
                />
              </div>
              <div className="shape-field">
                <label>并发</label>
                <input
                  type="number"
                  className="param-input"
                  value={shapeValues.concurrency}
                  onChange={(e) =>
                    setShapeValues((prev) => ({ ...prev, concurrency: e.target.value }))
                  }
                  placeholder="最大并发"
                />
              </div>
              <div className="shape-field">
                <label>持续时间(秒)</label>
                <input
                  type="number"
                  className="param-input"
                  value={shapeValues.durationSeconds}
                  onChange={(e) =>
                    setShapeValues((prev) => ({ ...prev, durationSeconds: e.target.value }))
                  }
                  placeholder="为空表示不限"
                />
              </div>
            </div>

            <div className="shape-field">
              <label>额外参数 (JSON)</label>
                <textarea
                  className="shape-json-editor"
                  rows={6}
                  value={shapeValues.paramsText}
                  onChange={(e) =>
                    setShapeValues((prev) => ({ ...prev, paramsText: e.target.value }))
                  }
                  placeholder={'{\n  "hotKeyRatio": 0.2\n}'}
                />
              {shapeParamsError && <div className="param-error">{shapeParamsError}</div>}
            </div>
            {shapeError && <div className="param-error">{shapeError}</div>}
          </div>
        </div>

        <div className="modal-actions">
          <button className="btn ghost" onClick={onClose}>
            取消
          </button>
          <button className="btn primary" onClick={handleSubmit}>
            启动任务
          </button>
        </div>
      </div>
    </div>
  );
}

function renderInput(param, value, onChange) {
  switch (param.type) {
    case 'number':
      return (
        <input
          type="number"
          className="param-input"
          value={value ?? ''}
          onChange={(e) => onChange(e.target.value)}
          placeholder={param.placeholder || '请输入数字'}
        />
      );
    case 'boolean':
      return (
        <label className="param-checkbox">
          <input
            type="checkbox"
            checked={Boolean(value)}
            onChange={(e) => onChange(e.target.checked)}
          />
          <span>启用</span>
        </label>
      );
    default:
      return (
        <input
          type="text"
          className="param-input"
          value={value ?? ''}
          onChange={(e) => onChange(e.target.value)}
          placeholder={param.placeholder || '请输入文本'}
        />
      );
  }
}
