import type { NotificationPreferences } from '../../api/devices';

interface Props {
  preferences: NotificationPreferences;
  onChange: (prefs: Partial<NotificationPreferences>) => void;
}

const TOGGLES: { key: keyof NotificationPreferences; label: string; description: string }[] = [
  {
    key: 'collectorEditionAnnouncement',
    label: "Collector's Edition announcement",
    description: 'When Rockstar officially announces a Collector\'s Edition',
  },
  {
    key: 'collectorEditionPreorder',
    label: "Collector's Edition pre-order",
    description: 'When Collector\'s Edition pre-orders open anywhere',
  },
  {
    key: 'releaseDateChanges',
    label: 'Release date changes',
    description: 'If the official release date is updated',
  },
  {
    key: 'newOfficialTrailers',
    label: 'New official trailers',
    description: 'When Rockstar publishes a new GTA VI trailer',
  },
  {
    key: 'majorRockstarNews',
    label: 'Major Rockstar announcements',
    description: 'New editions, pre-order openings, major news',
  },
  {
    key: 'backInStock',
    label: 'Back in stock',
    description: 'When an out-of-stock product becomes available',
  },
  {
    key: 'generalNews',
    label: 'General GTA VI news',
    description: 'Newswire articles, screenshots, media',
  },
  {
    key: 'priceChanges',
    label: 'Price changes',
    description: 'When a retailer changes their price',
  },
  {
    key: 'outOfStock',
    label: 'Out of stock',
    description: 'When a product goes out of stock',
  },
];

export function NotificationSettings({ preferences, onChange }: Props) {
  return (
    <div className="glass-card rounded-xl p-4 sm:p-5">
      <h3 className="text-lg font-bold text-accent-gold" style={{ fontFamily: 'var(--font-display)' }}>Notification preferences</h3>
      <div className="space-y-3">
        {TOGGLES.map(({ key, label, description }) => (
          <label key={key} className="flex items-start gap-3 cursor-pointer group">
            <input
              type="checkbox"
              checked={preferences[key]}
              onChange={e => onChange({ [key]: e.target.checked })}
              className="mt-0.5 w-4 h-4 rounded border-text-muted/30 bg-bg-primary
                         checked:bg-accent-pink checked:border-accent-pink
                         focus:ring-accent-pink focus:ring-1 cursor-pointer"
            />
            <div className="flex-1 min-w-0">
              <span className="text-sm font-medium text-text-primary group-hover:text-accent-pink transition-colors">
                {label}
              </span>
              <p className="text-xs text-text-muted mt-0.5">{description}</p>
            </div>
          </label>
        ))}
      </div>
    </div>
  );
}
