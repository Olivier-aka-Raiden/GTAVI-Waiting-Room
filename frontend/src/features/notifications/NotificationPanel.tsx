import { useState, useEffect } from 'react';
import { enablePushNotifications } from '../../firebase/messaging';
import type { NotificationPreferences } from '../../api/devices';
import { updateDevice } from '../../api/devices';

// ── Types ──────────────────────────────────────────────────────────────────

type PermissionState = 'idle' | 'requesting' | 'granted' | 'denied' | 'token_failed';

interface Props {
  installationId: string;
  preferences: NotificationPreferences;
  onPreferencesChange: (prefs: Partial<NotificationPreferences>) => void;
  onNotificationEnabled: (token: string) => void;
  onNotificationDisabled: () => void;
}

interface ToggleDef {
  key: keyof NotificationPreferences;
  label: string;
  description: string;
}

// ── Preference definitions ─────────────────────────────────────────────────

const TOGGLES: ToggleDef[] = [
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

// ── Custom toggle switch ───────────────────────────────────────────────────

function ToggleSwitch({
  checked,
  onChange,
  disabled,
  labelledBy,
  describedBy,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
  labelledBy: string;
  describedBy: string;
}) {
  return (
    <>
      <input
      type="checkbox"
      role="switch"
      aria-checked={checked}
      aria-labelledby={labelledBy}
      aria-describedby={describedBy}
      checked={checked}
      disabled={disabled}
      onChange={event => onChange(event.target.checked)}
      className="peer sr-only"
      />
      <span
        className={`inline-flex h-11 w-12 shrink-0 items-center justify-center rounded-lg transition-opacity peer-focus-visible:ring-2 peer-focus-visible:ring-accent-pink/50 peer-focus-visible:ring-offset-1 peer-focus-visible:ring-offset-bg-primary ${
          disabled ? 'opacity-40' : ''
        }`}
        aria-hidden="true"
      >
        <span
          className={`relative inline-flex h-5 w-9 rounded-full transition-colors ${
            checked ? 'bg-accent-pink' : 'bg-white/10'
          }`}
        >
          <span
            className={`absolute top-0.5 h-4 w-4 rounded-full bg-white shadow-sm transition-transform duration-200 ${
              checked ? 'translate-x-[18px]' : 'translate-x-[2px]'
            }`}
          />
        </span>
      </span>
    </>
  );
}

// ── Main component ─────────────────────────────────────────────────────────

export function NotificationPanel({
  installationId,
  preferences,
  onPreferencesChange,
  onNotificationEnabled,
  onNotificationDisabled,
}: Props) {
  const [permState, setPermState] = useState<PermissionState>(() => {
    if (localStorage.getItem('gta-vi-notifications-disabled') === 'true') return 'idle';
    if (localStorage.getItem('gta-vi-notifications-enabled') === 'true') return 'granted';
    const perm = 'Notification' in window ? Notification.permission : 'default';
    if (perm === 'denied') return 'denied';
    return 'idle';
  });
  const [errorDetail, setErrorDetail] = useState<string | null>(null);
  const [disableError, setDisableError] = useState<string | null>(null);
  const [isDisabling, setIsDisabling] = useState(false);

  // Sync with native permission changes
  useEffect(() => {
    if ('Notification' in window && Notification.permission === 'granted') {
      const userDisabled = localStorage.getItem('gta-vi-notifications-disabled') === 'true';
      if (!userDisabled && permState !== 'granted') {
        setPermState('granted');
        localStorage.setItem('gta-vi-notifications-enabled', 'true');
      }
    }
  }, [permState]);

  const isActive = permState === 'granted';
  const isDenied = permState === 'denied';
  const isLoading = permState === 'requesting';
  const isFailed = permState === 'token_failed';

  const requestPermission = async () => {
    setPermState('requesting');
    setErrorDetail(null);
    try {
      const token = await enablePushNotifications(installationId);
      setPermState('granted');
      localStorage.removeItem('gta-vi-notifications-disabled');
      localStorage.setItem('gta-vi-notifications-enabled', 'true');
      onNotificationEnabled(token);
    } catch (err: any) {
      const msg = err?.message ?? String(err);
      if (msg === 'PERMISSION_DENIED') {
        setPermState('denied');
      } else {
        setPermState('token_failed');
        setErrorDetail(msg);
      }
    }
  };

  const disable = async () => {
    setDisableError(null);
    setIsDisabling(true);
    try {
      await updateDevice(installationId, { notificationsEnabled: false });
      localStorage.removeItem('gta-vi-notifications-enabled');
      localStorage.setItem('gta-vi-notifications-disabled', 'true');
      setPermState('idle');
      onNotificationDisabled();
    } catch {
      setDisableError('Could not disable notifications. Please try again.');
    } finally {
      setIsDisabling(false);
    }
  };

  return (
    <div className="glass-card p-5">
      {/* ── Header: status + title ── */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          <div className={`w-10 h-10 rounded-full flex items-center justify-center text-lg ${
            isActive ? 'bg-accent-teal/15 ring-1 ring-accent-teal/30' : 'bg-white/5 ring-1 ring-white/10'
          }`}>
            🔔
          </div>
          <div>
            <h2 className="text-lg font-bold text-accent-gold" style={{ fontFamily: 'var(--font-display)' }}>
              Alerts
            </h2>
            <p className="text-xs text-text-muted">
              {isActive ? 'Push notifications are active' :
               isDenied ? 'Blocked in browser settings' :
               isFailed ? 'Registration failed' :
               'Notifications not enabled'}
            </p>
          </div>
        </div>

        {/* Status dot */}
        <div className="flex items-center gap-2">
          <span className={`w-2 h-2 rounded-full ${
            isActive ? 'bg-accent-teal animate-pulse' :
            isDenied ? 'bg-red-400' :
            'bg-text-muted/40'
          }`} />
          <span className={`text-xs font-medium ${
            isActive ? 'text-accent-teal' :
            isDenied ? 'text-red-400' :
            'text-text-muted'
          }`}>
            {isActive ? 'Active' : isDenied ? 'Blocked' : 'Inactive'}
          </span>
        </div>
      </div>

      {/* ── Enable/disable button ── */}
      <div className="mb-4">
        {isActive ? (
          <button
            onClick={disable}
            disabled={isDisabling}
            className="w-full min-h-11 py-2.5 rounded-lg border border-white/10 text-sm font-medium text-text-muted hover:text-accent-pink hover:border-accent-pink/30 transition-colors disabled:opacity-50"
          >
            {isDisabling ? 'Disabling...' : 'Disable notifications'}
          </button>
        ) : (
          <button
            onClick={requestPermission}
            disabled={isLoading || isDenied || isFailed}
            className={`w-full min-h-11 py-2.5 rounded-lg text-sm font-semibold transition-all ${
              isDenied || isFailed
                ? 'bg-white/5 text-text-muted/50 cursor-not-allowed'
                : 'bg-accent-pink text-text-dark hover:bg-accent-pink/90 active:scale-[0.98]'
            }`}
          >
            {isLoading ? 'Requesting permission...' :
             isDenied ? 'Blocked — check browser settings' :
             isFailed ? 'Unavailable — see details below' :
             'Enable push notifications'}
          </button>
        )}
      </div>

      {disableError && (
        <p className="text-xs text-accent-orange mb-4" role="alert">{disableError}</p>
      )}

      {/* ── Error details ── */}
      {isDenied && (
        <p className="text-xs text-text-muted mb-4 px-1">
          Open your browser settings and allow notifications for this site, then refresh.
        </p>
      )}
      {isFailed && errorDetail && (
        <div className="text-xs text-text-muted mb-4 px-1 space-y-1">
          <p className="text-accent-orange font-mono">{errorDetail}</p>
          {(errorDetail.includes('push service') || errorDetail.includes('304')) && (
            <>
              <p className="font-semibold text-accent-gold mt-2">Brave Desktop:</p>
              <p>
                Open <code className="text-xs bg-bg-card px-1 rounded">brave://settings/privacy</code> →
                enable <strong>"Use Google services for push messaging"</strong> →
                refresh page and try again.
              </p>
              <p>Or use Chrome / Firefox where push works natively.</p>
            </>
          )}
        </div>
      )}

      {/* ── Divider ── */}
      <hr className="border-white/10 mb-4" />

      {/* ── Preferences ── */}
      <h3 className="text-sm font-semibold text-text-muted uppercase tracking-wider mb-3">
        Notify me about
      </h3>
      <div className="space-y-2">
        {TOGGLES.map(({ key, label, description }) => (
          <label
            key={key}
            className={`flex items-center gap-3 px-1 py-2 -mx-1 rounded-lg transition-colors ${
              isActive ? 'cursor-pointer hover:bg-white/[0.03]' : 'cursor-default'
            }`}
          >
            <ToggleSwitch
              checked={preferences[key]}
              onChange={v => onPreferencesChange({ [key]: v })}
              disabled={!isActive}
              labelledBy={`notification-${key}-label`}
              describedBy={`notification-${key}-description`}
            />
            <div className="flex-1 min-w-0">
              <span id={`notification-${key}-label`} className={`text-sm transition-colors ${
                isActive && preferences[key] ? 'text-text-primary' : 'text-text-muted'
              }`}>
                {label}
              </span>
              <p id={`notification-${key}-description`} className="text-xs text-text-muted/60 mt-0.5">{description}</p>
            </div>
          </label>
        ))}
      </div>
    </div>
  );
}
