import { useEffect, useRef, useState } from 'react';
import { NewsCard, type NewsItem } from './NewsCards';

export function swipeStep(dx: number, dy: number): number {
  return Math.abs(dx) >= 50 && Math.abs(dx) > Math.abs(dy) * 1.3 ? (dx < 0 ? 1 : -1) : 0;
}

/** One announcement at a time; no automatic advance while someone is reading. */
export function NewsCarousel({ items, selectedId, hasMore, busy, onLoadMore }: {
  items: NewsItem[]; selectedId?: string | null; hasMore: boolean; busy: boolean; onLoadMore: () => void;
}) {
  const [activeId, setActiveId] = useState<string | null>(selectedId ?? null);
  const [direction, setDirection] = useState(1);
  const start = useRef<{ x: number; y: number } | null>(null);
  useEffect(() => { if (selectedId) setActiveId(selectedId); }, [selectedId]);
  const index = Math.max(0, items.findIndex(item => item.id === activeId));
  const current = items[index];
  const move = (step: number) => {
    const next = index + step;
    if (next < 0 || next >= items.length) return;
    setDirection(step); setActiveId(items[next].id);
  };
  if (!current) return null;
  return <div role="region" aria-roledescription="carousel" aria-label="Official announcements"
    tabIndex={0} className="news-carousel rounded-xl focus-visible:outline-2 focus-visible:outline-accent-pink"
    onKeyDown={event => {
      if (event.target !== event.currentTarget) return;
      if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
        event.preventDefault(); move(event.key === 'ArrowRight' ? 1 : -1);
      }
    }}>
    <div className="news-card-stage" onPointerDown={event => {
      if (!event.isPrimary || event.button !== 0 || (event.target as Element).closest('a, button')) return;
      start.current = { x: event.clientX, y: event.clientY };
      event.currentTarget.setPointerCapture(event.pointerId);
    }} onPointerCancel={() => { start.current = null; }} onPointerUp={event => {
      if (!start.current) return;
      const step = swipeStep(event.clientX - start.current.x, event.clientY - start.current.y);
      start.current = null;
      if (step) move(step);
    }}>
      <div key={current.id} className={direction > 0 ? 'news-card-enter older' : 'news-card-enter newer'}
        role="group" aria-roledescription="slide" aria-label={`${index + 1} of ${items.length}`}>
        <NewsCard item={current} />
      </div>
    </div>
    <div className="flex items-center justify-between gap-3 mt-3">
      <button className="min-h-11 px-3 rounded-full bg-white/5 disabled:opacity-30" disabled={index === 0} onClick={() => move(-1)} aria-label="Newer announcement">← Newer</button>
      <span aria-live="polite" aria-atomic="true" className="text-xs text-text-muted">{index + 1} / {items.length}{hasMore ? '+' : ''}</span>
      <button className="min-h-11 px-3 rounded-full bg-white/5 disabled:opacity-30" disabled={index === items.length - 1} onClick={() => move(1)} aria-label="Older announcement">Older →</button>
    </div>
    {hasMore && <button disabled={busy} onClick={onLoadMore} className="min-h-11 text-accent-pink underline">{busy ? 'Loading…' : 'Load older announcements'}</button>}
  </div>;
}
