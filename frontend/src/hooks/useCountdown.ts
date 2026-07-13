import { useState, useEffect, useCallback, useRef } from 'react';

interface CountdownResult {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  isReleased: boolean;
  totalSeconds: number;
}

/**
 * Calculates the remaining time until the release date.
 * Updates every second. Handles tab visibility and clock drift.
 */
export function useCountdown(releaseDate: string | null): CountdownResult {
  const [result, setResult] = useState<CountdownResult>({
    days: 0, hours: 0, minutes: 0, seconds: 0, isReleased: false, totalSeconds: 0,
  });
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const calculate = useCallback(() => {
    if (!releaseDate) {
      setResult({ days: 0, hours: 0, minutes: 0, seconds: 0, isReleased: false, totalSeconds: 0 });
      return;
    }

    // Countdown to local midnight of release day
    const target = new Date(releaseDate + 'T00:00:00');
    const now = new Date();
    const diff = target.getTime() - now.getTime();

    if (diff <= 0) {
      setResult({ days: 0, hours: 0, minutes: 0, seconds: 0, isReleased: true, totalSeconds: 0 });
      return;
    }

    const totalSeconds = Math.floor(diff / 1000);
    setResult({
      days: Math.floor(totalSeconds / 86400),
      hours: Math.floor((totalSeconds % 86400) / 3600),
      minutes: Math.floor((totalSeconds % 3600) / 60),
      seconds: totalSeconds % 60,
      isReleased: false,
      totalSeconds,
    });
  }, [releaseDate]);

  useEffect(() => {
    calculate();
    intervalRef.current = setInterval(calculate, 1000);

    // Recalculate on visibility change (tab switch)
    const onVisible = () => calculate();
    document.addEventListener('visibilitychange', onVisible);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [calculate]);

  return result;
}
