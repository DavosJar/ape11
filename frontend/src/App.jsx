import React, { useState, useEffect, useCallback } from 'react';
import NodesPanel from './components/NodesPanel';
import RoundControl from './components/RoundControl';
import ResultsPanel from './components/ResultsPanel';
import { getStatus, executeRound, getResults } from './api';
import './App.css';

export default function App() {
  const [status, setStatus] = useState(null);
  const [statusError, setStatusError] = useState(null);
  const [byzantine, setByzantine] = useState(new Set());
  const [roundNum, setRoundNum] = useState(1);
  const [lastResult, setLastResult] = useState(null);
  const [resultError, setResultError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [roundHistory, setRoundHistory] = useState([]);
  const [connectionOk, setConnectionOk] = useState(false);

  const fetchStatus = useCallback(async () => {
    try {
      const s = await getStatus();
      setStatus(s);
      setStatusError(null);
      setConnectionOk(true);
    } catch (err) {
      setStatusError(err.message);
      setConnectionOk(false);
    }
  }, []);

  useEffect(() => { fetchStatus(); }, [fetchStatus]);

  useEffect(() => {
    if (!connectionOk) return;
    const interval = setInterval(fetchStatus, 5000);
    return () => clearInterval(interval);
  }, [connectionOk, fetchStatus]);

  const toggleByzantine = (nodeId) => {
    setByzantine(prev => {
      const next = new Set(prev);
      if (next.has(nodeId)) next.delete(nodeId);
      else next.add(nodeId);
      return next;
    });
  };

  const handleExecuteRound = async () => {
    setLoading(true);
    setResultError(null);
    try {
      const result = await executeRound(roundNum, [...byzantine]);
      setLastResult(result);
      setRoundHistory(prev => [...prev, { ronda: roundNum, ...result }]);
      setRoundNum(prev => prev + 1);
    } catch (err) {
      setResultError(err.message);
    }
    setLoading(false);
  };

  const isCoordinator = status && status.isCoordinator;

  const nodeIds = status?.nodes?.map(n => n.id) || [1, 2, 3, 4, 5];

  return (
    <div className="app">
      <header className="header">
        <div className="header-left">
          <h1 className="title">
            <span className="title-icon">⚡</span>
            Consenso Bizantino
          </h1>
          <p className="subtitle">Sistemas Distribuidos — Práctica</p>
        </div>
        <div className="header-right">
          {status && (
            <div className="connection-badge">
              <span className={`dot ${connectionOk ? 'dot-ok' : 'dot-err'}`} />
              {connectionOk ? `Nodo ${status.nodeId}` : 'Desconectado'}
            </div>
          )}
          <button className="refresh-btn" onClick={fetchStatus} title="Actualizar estado">
            ⟳
          </button>
        </div>
      </header>

      {statusError && (
        <div className="error-banner">
          ⚠ No se puede conectar a la API: {statusError}
          <button onClick={fetchStatus} className="retry-btn">Reintentar</button>
        </div>
      )}

      <main className="main">
        <div className="panel panel-nodes">
          <NodesPanel
            nodeIds={nodeIds}
            status={status}
            byzantine={byzantine}
            onToggle={toggleByzantine}
            isCoordinator={isCoordinator}
          />
        </div>

        <div className="panel panel-control">
          <RoundControl
            roundNum={roundNum}
            onRoundChange={setRoundNum}
            onExecute={handleExecuteRound}
            loading={loading}
            disabled={!isCoordinator || !connectionOk}
            roundHistory={roundHistory}
          />
        </div>

        <div className="panel panel-results">
          <ResultsPanel
            lastResult={lastResult}
            resultError={resultError}
            roundHistory={roundHistory}
          />
        </div>
      </main>
    </div>
  );
}
