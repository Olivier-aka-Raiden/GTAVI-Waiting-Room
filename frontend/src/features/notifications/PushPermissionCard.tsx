import { useState } from 'react';
import { enablePushNotifications } from '../../firebase/messaging';

interface Props {
  installationId: string;
  onEnabled: (token: string) => void;
}

export function PushPermissionCard({ installationId, onEnabled }: Props) {
  const [state, setState] = useState<'idle' | 'requesting' | 'granted' | 'denied'>('idle');

  const requestPermission = async () => {
    setState('requesting');
    try {
      const token = await enablePushNotifications(installationId);
      if (token) {
        setState('granted');
        onEnabled(token);
      } else {
        setState('denied');
      }
    } catch {
      setState('denied');
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
            disabled={state === 'requesting' || state === 'denied'}
            className={`mt-3 px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
              state === 'denied'
                ? 'bg-text-muted/20 text-text-muted cursor-not-allowed'
                : 'bg-accent-pink text-text-dark hover:opacity-90'
            }`}
          >
            {state === 'requesting' ? 'Requesting...' :
             state === 'denied' ? 'Notifications blocked' :
             'Enable alerts'}
          </button>
          {state === 'denied' && (
            <p className="text-xs text-text-muted mt-2">
              Enable notifications in your browser settings to receive alerts.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
