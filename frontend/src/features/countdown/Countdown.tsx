interface CountdownSegmentProps {
  value: number;
  label: string;
}

function CountdownSegment({ value, label }: CountdownSegmentProps) {
  const padded = String(value).padStart(2, '0');
  return (
    <div className="flex flex-col items-center min-w-[60px] sm:min-w-[80px]">
      <span className="countdown-digit text-4xl sm:text-6xl font-bold text-accent-pink tabular-nums">
        {padded}
      </span>
      <span className="text-xs sm:text-sm uppercase tracking-widest text-text-muted mt-1">
        {label}
      </span>
    </div>
  );
}

function Separator() {
  return (
    <span className="text-3xl sm:text-5xl text-text-muted/40 font-light self-start mt-2 sm:mt-3">
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
        <h1 className="text-4xl sm:text-6xl font-bold text-accent-pink glow-pink inline-block px-6 py-3 rounded-lg">
          GTA VI IS OUT NOW
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
