import React from 'react';

export default function RoundControl({ roundNum, onRoundChange, onExecute, loading, disabled, roundHistory }) {
  return (
    <div>
      <h2 className="panel-title">
        <span>▶</span> Ejecutar Ronda
      </h2>

      <div className="control-row">
        <label>Ronda #</label>
        <input
          type="number"
          min={1}
          value={roundNum}
          onChange={e => onRoundChange(Math.max(1, parseInt(e.target.value) || 1))}
          disabled={loading || disabled}
        />
        <button
          className={`execute-btn${loading ? ' loading' : ''}`}
          onClick={onExecute}
          disabled={loading || disabled}
        >
          {loading ? '⏳ Ejecutando...' : '▶ Ejecutar'}
        </button>
      </div>

      <div className="control-hint">
        {disabled && !loading
          ? '⛔ Solo el coordinador puede ejecutar rondas. Conéctate al coordinador.'
          : 'Selecciona nodos bizantinos arriba, luego ejecuta la ronda.'}
      </div>

      {roundHistory.length > 0 && (
        <>
          <h3 className="history-title">Historial</h3>
          <div className="history-list">
            {[...roundHistory].reverse().map((h, i) => {
              const dec = h.decision || '';
              const isSi = dec.includes(':SI');
              const hasInc = h.hayInconsistencias;

              return (
                <div key={i} className="history-item">
                  <span className="h-round">R{h.ronda}</span>
                  <span className={`h-decision ${isSi ? 'decision-si' : 'decision-no'}`}>
                    {isSi ? '✓ APROBADO' : '✗ RECHAZADO'}
                  </span>
                  {hasInc && <span className="h-incons">⚠ {h.inconsistencias?.length || 0} inc.</span>}
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
