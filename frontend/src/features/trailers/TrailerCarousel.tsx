import { useState, useRef, useCallback } from 'react';
import type { Trailer } from '../../types/game';

/** Extract YouTube video ID from URL like https://www.youtube.com/watch?v=QdBZY2fkU-0 */
function youtubeId(url: string): string | null {
  try {
    const u = new URL(url);
    if (u.hostname.includes('youtube.com')) return u.searchParams.get('v');
    if (u.hostname === 'youtu.be') return u.pathname.slice(1);
  } catch { /* ignore */ }
  return null;
}

interface Props {
  trailers: Trailer[];
  latestTrailer: Trailer | null;
}

export function TrailerCarousel({ trailers, latestTrailer }: Props) {
  const [active, setActive] = useState(0);
  const touchStartX = useRef(0);
  const touchStartY = useRef(0);

  // Put the latest trailer first if available
  const ordered = latestTrailer && trailers.length > 0
    ? [latestTrailer, ...trailers.filter(t => t.id !== latestTrailer.id)]
    : trailers;

  if (ordered.length === 0) return null;

  const current = ordered[active];
  const videoId = youtubeId(current.videoUrl);
  const date = new Date(current.publicationDate).toLocaleDateString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
  });

  const isNew = (trailer: Trailer) => {
    const pubDate = new Date(trailer.publicationDate).getTime();
    return pubDate > Date.now() - 7 * 24 * 60 * 60 * 1000;
  };

  const goPrev = () => setActive(a => (a === 0 ? ordered.length - 1 : a - 1));
  const goNext = () => setActive(a => (a === ordered.length - 1 ? 0 : a + 1));
  const goTo = (i: number) => setActive(i);

  // Touch swipe handlers
  const onTouchStart = useCallback((e: React.TouchEvent) => {
    touchStartX.current = e.touches[0].clientX;
    touchStartY.current = e.touches[0].clientY;
  }, []);

  const onTouchEnd = useCallback((e: React.TouchEvent) => {
    const dx = e.changedTouches[0].clientX - touchStartX.current;
    const dy = e.changedTouches[0].clientY - touchStartY.current;
    // Only trigger if horizontal swipe dominates (more horizontal than vertical)
    if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 50) {
      if (dx > 0) goPrev();
      else goNext();
    }
  }, []);

  return (
    <section>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-xl sm:text-2xl font-bold text-accent-gold" style={{ fontFamily: 'var(--font-display)' }}>
          Official Trailers
        </h2>
        <span className="text-sm text-text-muted">
          {active + 1} / {ordered.length}
        </span>
      </div>

      {/* Video player */}
      <div
        className="relative rounded-xl overflow-hidden bg-black"
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
      >
        <div className="aspect-video">
          {videoId ? (
            <iframe
              src={`https://www.youtube-nocookie.com/embed/${videoId}?autoplay=0&rel=0&modestbranding=1`}
              title={current.title}
              allow="accelerometer; encrypted-media; picture-in-picture"
              allowFullScreen
              className="w-full h-full"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center bg-bg-primary/80 text-text-muted">
              <div className="text-center">
                <span className="text-4xl block mb-2">🎬</span>
                <p>Video not available</p>
                {current.sourceUrl && (
                  <a href={current.sourceUrl} target="_blank" rel="noopener noreferrer"
                     className="text-accent-teal text-sm underline mt-1 inline-block">
                    Watch on source
                  </a>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Nav arrows */}
        {ordered.length > 1 && (
          <>
            <button
              onClick={goPrev}
              className="absolute left-2 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-black/60 text-white hover:bg-black/80 flex items-center justify-center transition-colors"
              aria-label="Previous trailer"
            >
              ‹
            </button>
            <button
              onClick={goNext}
              className="absolute right-2 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-black/60 text-white hover:bg-black/80 flex items-center justify-center transition-colors"
              aria-label="Next trailer"
            >
              ›
            </button>
          </>
        )}
      </div>

      {/* Info bar */}
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <h3 className="text-base font-semibold text-text-primary">
          {current.title}
        </h3>
        {isNew(current) && (
          <span className="text-xs font-bold bg-accent-pink text-text-dark px-2 py-0.5 rounded-full">
            NEW
          </span>
        )}
        <span className="text-sm text-text-muted ml-auto">{date}</span>
      </div>

      {/* Dot navigation */}
      {ordered.length > 1 && (
        <div className="flex justify-center gap-2 mt-4">
          {ordered.map((t, i) => (
            <button
              key={t.id}
              onClick={() => goTo(i)}
              className={`w-2.5 h-2.5 rounded-full transition-all ${
                i === active
                  ? 'bg-accent-pink scale-125'
                  : 'bg-text-muted/40 hover:bg-text-muted/60'
              }`}
              aria-label={`Go to trailer ${i + 1}`}
            />
          ))}
        </div>
      )}
    </section>
  );
}
