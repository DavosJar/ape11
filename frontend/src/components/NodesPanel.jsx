import React from 'react';

export default function NodesPanel({ nodeIds, status, byzantine, onToggle, isCoordinator }) {
  const nodes = status?.nodes || [];

  return (
    <div>
      <h2 className="panel-title">
        <span>◈</span> Nodos
        <span style={{ marginLeft: 'auto', fontSize: '0.65rem', color: 'var(--text-dim)', fontWeight: 400 }}>
          {nodes.filter(n => n.isCoordinator).length > 0
            ? `Coordinador: P${status.coordinatorId}`
            : 'Elección en curso...'}
        </span>
      </h2>
      <div className="nodes-grid">
        {nodeIds.map(id => {
          const nodeInfo = nodes.find(n => n.id === id);
          const isSelf = nodeInfo?.isSelf;
          const isCoord = nodeInfo?.isCoordinator;
          const isByz = byzantine.has(id);
          const isActive = nodeInfo !== undefined;

          let cardClass = 'node-card';
          if (!isActive) cardClass += ' disconnected';
          if (isByz) cardClass += ' byzantine';
          else if (isCoord) cardClass += ' active-coord';

          return (
            <div
              key={id}
              className={cardClass}
              onClick={() => isActive && onToggle(id)}
              title={
                isByz
                  ? `P${id} — Bizantino (clic para honesto)`
                  : isCoord
                    ? `P${id} — Coordinador (clic para bizantino)`
                    : `P${id} — Honesto (clic para bizantino)`
              }
            >
              <div className="node-indicator">
                {isCoord ? '★' : `P${id}`}
              </div>
              <div className="node-label">
                {isSelf ? `P${id} (tú)` : `P${id}`}
              </div>
              <div className="node-badge">
                {isByz
                  ? <span className="node-badge byz">⚔ Bizantino</span>
                  : isCoord
                    ? <span className="node-badge coord">★ Coordinador</span>
                    : isActive
                      ? <span className="node-badge honest">● Honesto</span>
                      : <span style={{ color: 'var(--text-dim)' }}>○ Desconectado</span>
                }
              </div>
              <div className="click-hint">
                {isActive ? (isByz ? '↻ hacer honesto' : '↻ corromper') : ''}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
