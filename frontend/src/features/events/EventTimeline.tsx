import type { ChangeEvent } from '../../types/game';

const priorityIcons: Record<string, string> = {
  CRITICAL: '🚨',
  MAJOR: '📢',
  NEWS: '📰',
  RETAIL: '🛒',
};

const priorityColors: Record<string, string> = {
  CRITICAL: 'border-l-accent-pink',
  MAJOR: 'border-l-accent-gold',
  NEWS: 'border-l-accent-teal',
  RETAIL: 'border-l-accent-orange',
};

interface EventCardProps {
  event: ChangeEvent;
}

function EventCard({ event }: EventCardProps) {
  const date = new Date(event.detectedAt).toLocaleDateString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
  });
  const icon = priorityIcons[event.priority] || '📌';
  const borderColor = priorityColors[event.priority] || 'border-l-text-muted';

  return (
    <div className={`glass-card p-3 sm:p-4 border-l-4 ${borderColor}`}>
      <div className="flex items-start gap-3">
        <span className="text-lg mt-0.5">{icon}</span>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-xs text-text-muted">{date}</span>
            <span className="text-xs font-semibold uppercase tracking-wider text-text-muted/60">
              {event.priority}
            </span>
          </div>
          <h3 className="text-sm font-semibold text-text-primary mt-1">{event.title}</h3>
          {event.description && (
            <p className="text-sm text-text-muted mt-1">{event.description}</p>
          )}
          {event.evidenceUrl && (
            <a
              href={event.evidenceUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="text-xs text-accent-pink hover:underline mt-1 inline-block"
            >
              View source →
            </a>
          )}
        </div>
      </div>
    </div>
  );
}

interface EventTimelineProps {
  events: ChangeEvent[];
}

export function EventTimeline({ events }: EventTimelineProps) {
  if (events.length === 0) return null;

  return (
    <section>
      <h2 className="text-xl sm:text-2xl font-bold text-accent-gold mb-4" style={{ fontFamily: 'var(--font-display)' }}>Latest Updates</h2>
      <div className="space-y-2">
        {events.map(event => (
          <EventCard key={event.id} event={event} />
        ))}
      </div>
    </section>
  );
}
