import type { SystemStatus } from '../../types/game';

interface SystemHealthProps {
  status: SystemStatus;
}

export function SystemHealth({ status }: SystemHealthProps) {
  const runDate = status.lastMonitoringRunAt
    ? new Date(status.lastMonitoringRunAt)
    : null;

  const minutesAgo = runDate
    ? Math.floor((Date.now() - runDate.getTime()) / 60000)
    : null;

  return (
    <div className="glass-card p-4 sm:p-5">
      <h2 className="text-lg font-bold text-accent-gold mb-3" style={{ fontFamily: 'var(--font-display)' }}>
        Monitoring Status
      </h2>

      <div className="flex items-center gap-2 mb-3">
        <span className={`w-2.5 h-2.5 rounded-full ${
          status.monitoringHealthy ? 'bg-accent-teal animate-pulse' : 'bg-accent-orange'
        }`} />
        <span className={`text-sm font-medium ${
          status.monitoringHealthy ? 'text-accent-teal' : 'text-accent-orange'
        }`}>
          {status.monitoringHealthy ? 'All systems operational' : 'Some sources degraded'}
        </span>
      </div>

      {minutesAgo != null && (
        <p className="text-xs text-text-muted mb-3">
          Last monitoring run: {minutesAgo < 1 ? 'just now' : `${minutesAgo}m ago`}
          {runDate && ` at ${runDate.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })}`}
        </p>
      )}

      <p className="text-xs text-text-muted/60">
        We monitor Rockstar official sources, PlayStation Store, Xbox Store, and Swiss retailers
        around the clock to bring you the latest GTA VI updates.
      </p>
    </div>
  );
}
