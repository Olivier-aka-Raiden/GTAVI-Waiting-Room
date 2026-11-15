/** Release and celebration boundaries use the visitor's local calendar. */
export function releaseDayStatus(releaseDate: string, now: Date) {
  const start = new Date(releaseDate + 'T00:00:00');
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  return {
    isReleased: now.getTime() >= start.getTime(),
    isReleaseDay: now.getTime() >= start.getTime() && now.getTime() < end.getTime(),
    celebrationEndsAt: end.getTime(),
  };
}
