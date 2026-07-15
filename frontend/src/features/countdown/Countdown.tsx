import { useEffect, useRef } from 'react';

interface CountdownSegmentProps {
  value: number;
  label: string;
}

function CountdownSegment({ value, label }: CountdownSegmentProps) {
  const prevRef = useRef(value);
  const digitRef = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (prevRef.current !== value && digitRef.current) {
      digitRef.current.classList.remove('digit-pop');
      void digitRef.current.offsetWidth; // force reflow
      digitRef.current.classList.add('digit-pop');
    }
    prevRef.current = value;
  }, [value]);

  const padded = String(value).padStart(2, '0');
  return (
    <div className="flex flex-col items-center min-w-[60px] sm:min-w-[80px]">
      <span
        ref={digitRef}
        className="countdown-digit text-4xl sm:text-6xl font-bold text-accent-pink tabular-nums"
        style={{ fontFamily: 'var(--font-display)' }}
      >
        {padded}
      </span>
      <span className="text-xs sm:text-sm uppercase tracking-[0.2em] text-text-muted/70 mt-1 font-medium"
        style={{ fontFamily: 'var(--font-display)' }}>
        {label}
      </span>
    </div>
  );
}

function Separator() {
  return (
    <span className="text-3xl sm:text-5xl text-accent-pink/25 font-light self-start mt-2 sm:mt-3">
      :
    </span>
  );
}

interface CountdownProps {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  isReleased: boolean;
}

export function Countdown({ days, hours, minutes, seconds, isReleased }: CountdownProps) {
  if (isReleased) {
    return (
      <div className="text-center py-8">
        <h1 className="text-4xl sm:text-6xl font-bold text-accent-pink glow-pink inline-block px-6 py-3 rounded-lg"
          style={{ fontFamily: 'var(--font-display)' }}>
          OUT NOW
        </h1>
        <p className="text-text-muted mt-4 text-lg">Available on PlayStation 5 and Xbox Series X|S</p>
      </div>
    );
  }

  return (
    <div className="flex items-center justify-center gap-1 sm:gap-2 py-4">
      <CountdownSegment value={days} label="Days" />
      <Separator />
      <CountdownSegment value={hours} label="Hours" />
      <Separator />
      <CountdownSegment value={minutes} label="Min" />
      <Separator />
      <CountdownSegment value={seconds} label="Sec" />
    </div>
  );
}
