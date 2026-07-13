import { useState, useEffect } from 'react';
import { getGameOverview } from '../api/game';
import { registerDevice } from '../api/devices';
import type { GameOverview } from '../types/game';
import { useCountdown } from '../hooks/useCountdown';
import { Countdown } from '../features/countdown/Countdown';
import { TrailerCarousel } from '../features/trailers/TrailerCarousel';
import { EditionSection } from '../features/editions/EditionSection';
import { EventTimeline } from '../features/events/EventTimeline';
import { PushPermissionCard } from '../features/notifications/PushPermissionCard';
import { NotificationSettings } from '../features/notifications/NotificationSettings';
import type { NotificationPreferences } from '../api/devices';

function VerificationBadge({ lastCheck, healthy }: { lastCheck: string | null; healthy: boolean }) {
  if (!lastCheck) return null;

  const checkDate = new Date(lastCheck);
  const minutesAgo = Math.floor((Date.now() - checkDate.getTime()) / 60000);
  const isStale = minutesAgo > 60;

  return (
    <div className={`flex items-center gap-1.5 text-xs ${
      isStale ? 'text-accent-orange' : 'text-accent-teal'
    }`}>
      <span>{isStale ? '⚠' : '✓'}</span>
      <span>
        {isStale
          ? `Last verified ${Math.floor(minutesAgo / 60)}h ago`
          : `Verified ${minutesAgo}m ago`}
      </span>
    </div>
  );
}

function HeroSection({ game }: { game: GameOverview }) {
  return (
    <div className="relative text-center py-6 sm:py-10">
      {/* Logo */}
      <div className="mb-4">
        <img
          src="/assets/logo-gta.png"
          alt="Grand Theft Auto"
          className="h-10 sm:h-14 mx-auto mb-1 object-contain"
        />
        <img
          src="/assets/logo-vi.png"
          alt="VI"
          className="h-8 sm:h-10 mx-auto object-contain"
        />
      </div>

      <h2 className="text-accent-gold text-sm sm:text-base uppercase tracking-[0.2em] mb-6">
        Releases in
      </h2>

      {/* Countdown */}
      <CountdownWrapper releaseDate={game.release.date} />

      {/* Release date */}
      <p className="text-lg sm:text-xl text-text-primary font-semibold mt-4">
        {new Date(game.release.date + 'T00:00:00').toLocaleDateString('en-US', {
          weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
        })}
      </p>

      {/* Platforms */}
      <p className="text-sm text-text-muted mt-2">
        PlayStation 5 · Xbox Series X|S
      </p>

      {/* Verification */}
      <div className="mt-3">
        <VerificationBadge
          lastCheck={game.release.lastSuccessfulCheckAt}
          healthy={game.systemStatus.monitoringHealthy}
        />
      </div>
    </div>
  );
}

function CountdownWrapper({ releaseDate }: { releaseDate: string }) {
  const { days, hours, minutes, seconds, isReleased } = useCountdown(releaseDate);
  return <Countdown days={days} hours={hours} minutes={minutes} seconds={seconds} isReleased={isReleased} />;
}

function LoadingSkeleton() {
  return (
    <div className="min-h-screen bg-bg-primary animate-pulse">
      <div className="max-w-2xl mx-auto px-4 py-10">
        <div className="h-14 w-48 bg-bg-card rounded mx-auto mb-2" />
        <div className="h-10 w-32 bg-bg-card rounded mx-auto mb-6" />
        <div className="flex justify-center gap-2 mb-4">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="w-16 sm:w-20 h-20 sm:h-24 bg-bg-card rounded" />
          ))}
        </div>
        <div className="h-6 w-64 bg-bg-card rounded mx-auto" />
      </div>
    </div>
  );
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="min-h-screen bg-bg-primary flex items-center justify-center px-4">
      <div className="text-center">
        <p className="text-accent-orange text-lg mb-2">Failed to load data</p>
        <p className="text-text-muted text-sm mb-4">{message}</p>
        <button
          onClick={onRetry}
          className="px-4 py-2 bg-accent-pink text-text-dark rounded-lg font-semibold hover:opacity-90 transition-opacity"
        >
          Retry
        </button>
      </div>
    </div>
  );
}

export function HomePage() {
  const [data, setData] = useState<GameOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [installationId] = useState(() =>
    localStorage.getItem('gta-vi-installation-id') || crypto.randomUUID()
  );
  const [prefs, setPrefs] = useState<NotificationPreferences>(() => ({
    collectorEditionAnnouncement: true,
    collectorEditionPreorder: true,
    releaseDateChanges: true,
    newOfficialTrailers: true,
    majorRockstarNews: true,
    generalNews: false,
    priceChanges: false,
    outOfStock: false,
    backInStock: true,
  }));

  const fetchData = () => {
    setLoading(true);
    setError(null);
    getGameOverview()
      .then(setData)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  };

  const handleNotificationEnabled = (_token: string) => {
    localStorage.setItem('gta-vi-installation-id', installationId);
  };

  useEffect(() => { fetchData(); }, []);

  if (loading) return <LoadingSkeleton />;
  if (error) return <ErrorState message={error} onRetry={fetchData} />;
  if (!data) return null;

  return (
    <div className="min-h-screen bg-bg-primary">
      {/* Hero background */}
      <div
        className="absolute inset-x-0 top-0 h-[60vh] bg-cover bg-center opacity-20 pointer-events-none"
        style={{ backgroundImage: 'url(/assets/hero-poster.jpg)' }}
      />

      <div className="relative max-w-2xl mx-auto px-4 pb-16">
        <HeroSection game={data} />

        <div className="space-y-8">
          {/* Trailers */}
          <TrailerCarousel trailers={data.trailers} latestTrailer={data.latestTrailer} />

          {/* Editions */}
          <EditionSection editions={data.editions} />

          {/* Events */}
          <EventTimeline events={data.latestEvents} />

          {/* Notifications */}
          <PushPermissionCard
            installationId={installationId}
            onEnabled={handleNotificationEnabled}
          />
          <NotificationSettings
            preferences={prefs}
            onChange={(update) => setPrefs(p => ({ ...p, ...update }))}
          />
        </div>

        {/* Footer */}
        <footer className="mt-12 pt-6 border-t border-white/10 text-center">
          <p className="text-xs text-text-muted">
            GTA VI Waiting Room · v1.0.0 · Not affiliated with Rockstar Games
          </p>
        </footer>
      </div>
    </div>
  );
}
