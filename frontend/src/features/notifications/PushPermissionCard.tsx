import { useState, useEffect } from 'react';
import { enablePushNotifications } from '../../firebase/messaging';

interface Props {
  installationId: string;
  onEnabled: (token: string) => void;
}

type PermissionState = 'idle' | 'requesting' | 'granted' | 'denied' | 'token_failed';

export function PushPermissionCard({ installationId, onEnabled }: Props) {
  const [state, setState] = useState<PermissionState>(() => {
    // Restore persisted state on mount so enabled cards don't flash on refresh
    if (localStorage.getItem('gta-vi-notifications-enabled') === 'true') return 'granted';
    // Check the actual browser permission
    const perm = 'Notification' in window ? Notification.permission : 'default';
    if (perm === 'denied') return 'denied';
    return 'idle';
  });

  // If permission was already granted externally (e.g. another session, or
  // browser setting change), pick it up without re-prompting.
  useEffect(() => {
    if (state !== 'granted' && 'Notification' in window && Notification.permission === 'granted') {
      setState('granted');
    }
  }, [state]);

  const requestPermission = async () => {
    setState('requesting');
    try {
      const token = await enablePushNotifications(installationId);
      if (token) {
        setState('granted');
        localStorage.setItem('gta-vi-notifications-enabled', 'true');
        onEnabled(token);
      } else {
        // Permission was granted but FCM token acquisition failed
        // (Brave blocking Service Workers, no SW registered, etc.)
        setState(Notification.permission === 'denied' ? 'denied' : 'token_failed');
      }
    } catch {
      setState(Notification.permission === 'denied' ? 'denied' : 'token_failed');
    }
  };

  if (state === 'granted') return null;

  return (
    <div className="bg-gradient-to-r from-accent-purple/40 to-bg-card rounded-xl p-5 border border-accent-purple/30">
      <div className="flex items-start gap-3">
        <span className="text-2xl">🔔</span>
        <div className="flex-1">
          <h3 className="text-base font-bold text-accent-gold">
            Get notified instantly
          </h3>
          <p className="text-sm text-text-muted mt-1">
            When a Collector's Edition is announced or pre-orders open,
            you'll be the first to know.
          </p>
          <button
            onClick={requestPermission}
            disabled={state === 'requesting' || state === 'denied' || state === 'token_failed'}
            className={`mt-3 px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
              state === 'denied' || state === 'token_failed'
                ? 'bg-text-muted/20 text-text-muted cursor-not-allowed'
                : 'bg-accent-pink text-text-dark hover:opacity-90'
            }`}
          >
            {state === 'requesting' ? 'Requesting...' :
             state === 'denied' ? 'Notifications blocked' :
             state === 'token_failed' ? 'Notifications unavailable' :
             'Enable alerts'}
          </button>
          {state === 'denied' && (
            <p className="text-xs text-text-muted mt-2">
              Notifications are blocked in your browser. Open your browser settings
              and allow notifications for this site, then refresh.
            </p>
          )}
          {state === 'token_failed' && (
            <p className="text-xs text-text-muted mt-2">
              Could not register for push notifications. This browser may not support
              Service Workers (e.g. Brave on Android). Try Chrome or a different browser.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
