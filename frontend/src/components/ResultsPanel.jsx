import React from 'react';

function VoteTable({ votes }) {
  if (!votes || Object.keys(votes).length === 0) return <span style={{ fontSize: '0.65rem', color: 'var(--text-dim)' }}>Sin votos</span>;

  return (
    <table className="vote-table">
      <thead>
        <tr>
          <th>Nodo</th>
          <th>Voto</th>
        </tr>
      </thead>
      <tbody>
        {Object.entries(votes).map(([node, vote]) => {
          const isSi = vote.includes('SI');
          return (
            <tr key={node}>
              <td>{node}</td>
              <td className={isSi ? 'v-si' : 'v-no'}>{isSi ? '✓ SI' : '✗ NO'}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function ReportsSection({ reportes }) {
  if (!reportes || Object.keys(reportes).length === 0) {
    return <span style={{ fontSize: '0.65rem', color: 'var(--text-dim)' }}>Sin reportes recibidos</span>;
  }

  const reporterNodes = Object.keys(reportes);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      {reporterNodes.map(reporter => (
        <div key={reporter} style={{ background: 'var(--bg-card)', borderRadius: '6px', padding: '8px', border: '1px solid var(--border)' }}>
          <div style={{ fontSize: '0.6rem', fontWeight: 600, color: 'var(--cyber)', marginBottom: '4px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
            {reporter} reporta
          </div>
          <VoteTable votes={reportes[reporter]} />
        </div>
      ))}
    </div>
  );
}

function InconsistenciesSection({ inconsistencias }) {
  if (!inconsistencias || inconsistencias.length === 0) {
    return (
      <div className="incons-clean">
        <span>✓</span> Sin inconsistencias. Todos los nodos votaron consistentemente.
      </div>
    );
  }

  return (
    <div className="incons-list">
      <div style={{ fontSize: '0.65rem', color: 'var(--neon-red)', marginBottom: '4px', fontWeight: 600 }}>
        ⚠ {inconsistencias.length} inconsistencia(s) encontrada(s)
      </div>
      {inconsistencias.map((inc, i) => (
        <div key={i} className="incons-item">
          Nodo <span className="highlight">P{inc.emisor}</span> votó{' '}
          <span className="highlight">{inc.votoAlCoordinador?.includes('SI') ? 'SI' : 'NO'}</span>{' '}
          al coordinador pero <span className="highlight">P{inc.reportero}</span> recibió{' '}
          <span className="highlight">{inc.votoReportado?.includes('SI') ? 'SI' : 'NO'}</span>
        </div>
      ))}
    </div>
  );
}

export default function ResultsPanel({ lastResult, resultError, roundHistory }) {
  if (resultError) {
    return (
      <div>
        <h2 className="panel-title"><span>≡</span> Resultados</h2>
        <div className="results-error">⚠ {resultError}</div>
      </div>
    );
  }

  if (!lastResult) {
    const lastFromHistory = roundHistory.length > 0 ? roundHistory[roundHistory.length - 1] : null;

    if (lastFromHistory) {
      return (
        <div>
          <h2 className="panel-title"><span>≡</span> Resultados — Ronda {lastFromHistory.ronda}</h2>
          <ResultsContent result={lastFromHistory} />
        </div>
      );
    }

    return (
      <div>
        <h2 className="panel-title"><span>≡</span> Resultados</h2>
        <div className="results-empty">
          <div style={{ fontSize: '2rem', marginBottom: '8px', opacity: 0.3 }}>⚡</div>
          <div>Aún no se ejecutaron rondas.</div>
          <div style={{ fontSize: '0.65rem', marginTop: '6px' }}>
            Selecciona nodos bizantinos y presiona Ejecutar.
          </div>
        </div>
      </div>
    );
  }

  return (
    <div>
      <h2 className="panel-title"><span>≡</span> Resultados — Ronda {lastResult.ronda}</h2>
      <ResultsContent result={lastResult} />
    </div>
  );
}

function ResultsContent({ result }) {
  const isSi = result.decision?.includes(':SI');

  return (
    <div className="results-grid">
      <div className="result-section">
        <h3>Decisión</h3>
        <div className={`decision-display ${isSi ? 'si' : 'no'}`}>
          {isSi ? '✓' : '✗'} {isSi ? 'APROBADO' : 'RECHAZADO'}
        </div>
        <div className="vote-counts">
          <span className="si">✓ SI: {result.totalSI}</span>
          <span className="no">✗ NO: {result.totalNO}</span>
        </div>
        {result.byzantine?.length > 0 && (
          <div style={{ fontSize: '0.6rem', color: 'var(--neon-red)', marginTop: '8px' }}>
            Bizantinos: P{result.byzantine.join(', P')}
          </div>
        )}
      </div>

      <div className="result-section">
        <h3>Votos</h3>
        <VoteTable votes={result.votos} />
      </div>

      <div className="result-section">
        <h3>Inconsistencias</h3>
        <InconsistenciesSection inconsistencias={result.inconsistencias} />
      </div>

      {result.reportes && Object.keys(result.reportes).length > 0 && (
        <div className="result-section" style={{ gridColumn: '1 / -1' }}>
          <h3>Reportes de Votos (de cada nodo al coordinador)</h3>
          <ReportsSection reportes={result.reportes} />
        </div>
      )}
    </div>
  );
}
