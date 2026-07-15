import { useState, useEffect, useCallback } from 'react';
import { getGameOverview } from '../api/game';
import { registerDevice, getPreferences, updatePreferences } from '../api/devices';
import type { GameOverview } from '../types/game';
import { useCountdown } from '../hooks/useCountdown';
import { useScrollReveal } from '../hooks/useScrollReveal';
import { Countdown } from '../features/countdown/Countdown';
import { TrailerCarousel } from '../features/trailers/TrailerCarousel';
import { EditionSection } from '../features/editions/EditionSection';
import { EventTimeline } from '../features/events/EventTimeline';
import { SystemHealth } from '../features/system/SystemHealth';
import { NotificationPanel } from '../features/notifications/NotificationPanel';
import type { NotificationPreferences } from '../api/devices';

// ── Default preferences (used as initial state while loading) ──────────────
const DEFAULT_PREFS: NotificationPreferences = {
  collectorEditionAnnouncement: true,
  collectorEditionPreorder: true,
  releaseDateChanges: true,
  newOfficialTrailers: true,
  majorRockstarNews: true,
  generalNews: false,
  priceChanges: false,
  outOfStock: false,
  backInStock: true,
};

// ── Sub-components ─────────────────────────────────────────────────────────

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
    <div className="relative text-center py-8 sm:py-14">
      {/* Logo */}
      <div className="mb-6">
        <img
          src="/assets/logo-gta.png"
          alt="Grand Theft Auto"
          className="h-12 sm:h-16 mx-auto mb-1 object-contain drop-shadow-lg"
        />
        <img
          src="/assets/logo-vi.png"
          alt="VI"
          className="h-10 sm:h-14 mx-auto object-contain drop-shadow-lg"
        />
      </div>

      <h2 className="text-accent-gold text-sm sm:text-base uppercase tracking-[0.25em] mb-6 font-medium" style={{ fontFamily: 'var(--font-display)' }}>
        Releases in
      </h2>

      {/* Countdown */}
      <CountdownWrapper releaseDate={game.release.date} />

      {/* Release date */}
      <p className="text-lg sm:text-xl text-text-primary font-semibold mt-5" style={{ fontFamily: 'var(--font-display)' }}>
        {new Date(game.release.date + 'T00:00:00').toLocaleDateString('en-US', {
          weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
        })}
      </p>

      {/* Platforms */}
      <p className="text-sm text-text-muted/80 mt-2 font-medium">
        PlayStation 5 · Xbox Series X|S
      </p>

      {/* Verification */}
      <div className="mt-4 flex items-center justify-center gap-4">
        <VerificationBadge
          lastCheck={game.release.lastSuccessfulCheckAt}
          healthy={game.systemStatus.monitoringHealthy}
        />
        <button
          onClick={() => {
            const text = `GTA VI releases on ${new Date(game.release.date + 'T00:00:00').toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' })} — track the countdown with me!`;
            const url = window.location.href;
            if (navigator.share) {
              navigator.share({ title: 'GTA VI Waiting Room', text, url }).catch(() => {});
            } else {
              navigator.clipboard.writeText(`${text} ${url}`).catch(() => {});
            }
          }}
          className="text-xs text-text-muted hover:text-accent-pink transition-colors flex items-center gap-1"
        >
          <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <path strokeLinecap="round" strokeLinejoin="round" d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
          </svg>
          Share
        </button>
      </div>
    </div>
  );
}

function CountdownWrapper({ releaseDate }: { releaseDate: string }) {
  const { days, hours, minutes, seconds, isReleased } = useCountdown(releaseDate);
  return <Countdown days={days} hours={hours} minutes={minutes} seconds={seconds} isReleased={isReleased} />;
}

// ── Section wrapper with reveal animation ──────────────────────────────────

function Section({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  const ref = useScrollReveal();
  return (
    <section ref={ref} className={`reveal ${className}`}>
      {children}
    </section>
  );
}

function Divider() {
  return <hr className="section-divider" />;
}

// ── Loading & Error states ─────────────────────────────────────────────────

function LoadingSkeleton() {
  return (
    <div className="min-h-screen bg-bg-primary animate-pulse">
      <div className="hero-bg" />
      <div className="relative max-w-2xl mx-auto px-4 py-10">
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

// ── Sticky header ──────────────────────────────────────────────────────────

function StickyHeader({ notificationActive }: { notificationActive: boolean }) {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const onScroll = () => setVisible(window.scrollY > 300);
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <header
      className={`fixed top-0 inset-x-0 z-50 transition-all duration-300 pt-[env(safe-area-inset-top,0px)] ${
        visible ? 'translate-y-0 opacity-100' : '-translate-y-full opacity-0'
      }`}
    >
      <div className="glass-card rounded-none border-t-0 border-x-0 border-b border-white/5">
        <div className="max-w-2xl mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <img src="/assets/logo-vi.png" alt="" className="h-5 object-contain" />
            <span className="text-sm font-semibold text-text-primary" style={{ fontFamily: 'var(--font-display)' }}>
              WAITING ROOM
            </span>
          </div>
          {notificationActive && (
            <span className="flex items-center gap-1.5 text-xs text-accent-teal">
              <span className="w-2 h-2 rounded-full bg-accent-teal animate-pulse" />
              Live
            </span>
          )}
        </div>
      </div>
    </header>
  );
}

// ── Tab navigation ─────────────────────────────────────────────────────────

const TABS = [
  { id: 'countdown', label: 'Countdown', emoji: '⌛' },
  { id: 'trailers', label: 'Trailers', emoji: '🎬' },
  { id: 'editions', label: 'Editions', emoji: '📦' },
  { id: 'updates', label: 'Updates', emoji: '📰' },
  { id: 'system', label: 'Status', emoji: '📡' },
  { id: 'alerts', label: 'Alerts', emoji: '🔔' },
] as const;

function TabNav({ activeTab, onTabClick }: { activeTab: string; onTabClick: (id: string) => void }) {
  return (
    <div className="sticky top-0 z-40 pt-[env(safe-area-inset-top,0px)] pt-3 pb-2 -mx-4 px-4" style={{ background: 'linear-gradient(to bottom, #111117 60%, transparent)' }}>
      <div className="glass-card !rounded-full px-1 py-1 flex">
        {TABS.map(tab => (
          <button
            key={tab.id}
            onClick={() => onTabClick(tab.id)}
            className={`flex-1 text-xs font-semibold py-2 rounded-full transition-all duration-200 whitespace-nowrap ${
              activeTab === tab.id
                ? 'bg-accent-pink/20 text-accent-pink'
                : 'text-text-muted hover:text-text-primary'
            }`}
          >
            <span className="hidden sm:inline mr-1">{tab.emoji}</span>
            {tab.label}
          </button>
        ))}
      </div>
    </div>
  );
}

// ── Main page ──────────────────────────────────────────────────────────────

export function HomePage() {
  const [data, setData] = useState<GameOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('countdown');
  const [notificationActive, setNotificationActive] = useState(
    () => localStorage.getItem('gta-vi-notifications-enabled') === 'true'
  );
  const [installationId] = useState(() => {
    const stored = localStorage.getItem('gta-vi-installation-id');
    if (stored) return stored;
    const id = crypto.randomUUID();
    localStorage.setItem('gta-vi-installation-id', id);
    return id;
  });
  const [prefs, setPrefs] = useState<NotificationPreferences>(() => {
    try {
      const cached = localStorage.getItem('gta-vi-prefs');
      if (cached) return JSON.parse(cached);
    } catch { /* ignore */ }
    return DEFAULT_PREFS;
  });
  const [prefsLoaded, setPrefsLoaded] = useState(false);

  const fetchData = useCallback(() => {
    setLoading(true);
    setError(null);
    getGameOverview()
      .then(setData)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    let cancelled = false;
    getPreferences(installationId)
      .then(backendPrefs => {
        if (!cancelled) {
          setPrefs(backendPrefs);
          localStorage.setItem('gta-vi-prefs', JSON.stringify(backendPrefs));
          setPrefsLoaded(true);
        }
      })
      .catch(() => {
        if (!cancelled) setPrefsLoaded(true);
      });
    return () => { cancelled = true; };
  }, [installationId]);

  useEffect(() => {
    if (!prefsLoaded) return;
    localStorage.setItem('gta-vi-prefs', JSON.stringify(prefs));
    const timer = setTimeout(() => {
      updatePreferences(installationId, prefs).catch(() => {});
    }, 500);
    return () => clearTimeout(timer);
  }, [prefs, installationId, prefsLoaded]);

  const scrollToSection = (id: string) => {
    setActiveTab(id);
    const el = document.getElementById(`section-${id}`);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const handleNotificationEnabled = useCallback((_token: string) => {
    setNotificationActive(true);
  }, []);

  const handleNotificationDisabled = useCallback(() => {
    setNotificationActive(false);
  }, []);

  const handlePrefChange = useCallback((update: Partial<NotificationPreferences>) => {
    setPrefs(p => ({ ...p, ...update }));
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  // ── Update active tab based on scroll position ──────────────────────────
  useEffect(() => {
    const sectionIds = TABS.map(t => `section-${t.id}`);
    const elements = sectionIds.map(id => document.getElementById(id)).filter(Boolean) as HTMLElement[];

    const observer = new IntersectionObserver(
      (entries) => {
        // Find the first section that's substantially visible
        const visible = entries
          .filter(e => e.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);

        if (visible.length > 0) {
          const id = visible[0].target.id.replace('section-', '');
          setActiveTab(id);
        }
      },
      { threshold: 0.3, rootMargin: '-80px 0px -50% 0px' }
    );

    elements.forEach(el => observer.observe(el));
    return () => observer.disconnect();
  }, [data]);

  if (loading) return <LoadingSkeleton />;
  if (error) return <ErrorState message={error} onRetry={fetchData} />;
  if (!data) return null;

  return (
    <div className="min-h-screen bg-bg-primary">
      {/* Animated gradient hero background */}
      <div className="hero-bg" />
      {/* Hero poster image overlay */}
      <div
        className="absolute inset-x-0 top-0 h-[70vh] bg-cover bg-top opacity-12 pointer-events-none"
        style={{ backgroundImage: 'url(/assets/hero-poster.jpg)', maskImage: 'linear-gradient(to bottom, black 40%, transparent)' }}
      />

      <StickyHeader notificationActive={notificationActive} />

      <div className="relative max-w-2xl mx-auto px-4 pb-16">
        <div id="section-countdown">
          <HeroSection game={data} />
        </div>

        <TabNav activeTab={activeTab} onTabClick={scrollToSection} />

        <div className="space-y-6 mt-6">
          {/* Trailers */}
          <div id="section-trailers">
            <Section>
              <TrailerCarousel trailers={data.trailers} latestTrailer={data.latestTrailer} />
            </Section>
          </div>
          <Divider />

          {/* Editions */}
          <div id="section-editions">
            <Section>
              <EditionSection editions={data.editions} />
            </Section>
          </div>
          <Divider />

          {/* Events */}
          <div id="section-updates">
            <Section>
              <EventTimeline events={data.latestEvents} />
            </Section>
          </div>
          <Divider />

          {/* System Health */}
          <div id="section-system">
            <Section>
              <SystemHealth status={data.systemStatus} />
            </Section>
          </div>
          <Divider />

          {/* Notifications */}
          <div id="section-alerts">
            <Section>
              <NotificationPanel
                installationId={installationId}
                preferences={prefs}
                onPreferencesChange={handlePrefChange}
                onNotificationEnabled={handleNotificationEnabled}
                onNotificationDisabled={handleNotificationDisabled}
              />
            </Section>
          </div>
        </div>

        {/* Footer */}
        <footer className="mt-16 pt-6 border-t border-white/10 text-center">
          <p className="text-xs text-text-muted/60">
            GTA VI Waiting Room · v1.0.0 · Not affiliated with Rockstar Games
          </p>
        </footer>
      </div>
    </div>
  );
}
