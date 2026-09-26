import { useEffect } from 'react';

export function sectionAtPosition(sections: { id: string; top: number }[], anchor: number, atBottom: boolean): string | undefined {
  if (atBottom) return sections.at(-1)?.id;
  return sections.filter(section => section.top <= anchor).at(-1)?.id ?? sections[0]?.id;
}

/** Measure section starts, so sections taller than the viewport work too. */
export function useActiveSection(ready: boolean, ids: readonly string[], onChange: (id: string) => void) {
  useEffect(() => {
    if (!ready) return;
    let frame = 0;
    const measure = () => {
      frame = 0;
      const sections = ids.flatMap(id => {
        const element = document.getElementById('section-' + id);
        return element ? [{ id, top: element.getBoundingClientRect().top }] : [];
      });
      const anchor = (document.getElementById('section-navigation')?.offsetHeight ?? 104) + 16;
      const atBottom = window.scrollY > 0 && window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 2;
      const active = sectionAtPosition(sections, anchor, atBottom);
      if (active) onChange(active);
    };
    const schedule = () => { if (!frame) frame = requestAnimationFrame(measure); };
    window.addEventListener('scroll', schedule, { passive: true });
    window.addEventListener('resize', schedule);
    const resize = new ResizeObserver(schedule);
    resize.observe(document.body);
    schedule();
    return () => {
      cancelAnimationFrame(frame); resize.disconnect();
      window.removeEventListener('scroll', schedule);
      window.removeEventListener('resize', schedule);
    };
  }, [ready, ids, onChange]);
}
