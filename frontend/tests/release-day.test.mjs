import test from 'node:test';
import assert from 'node:assert/strict';
import { releaseDayStatus } from '../src/hooks/releaseDay.ts';

test('celebration is limited to the local release date', () => {
  const date = '2026-11-19';
  const cases = [
    [new Date(2026, 10, 18, 23, 59, 59, 999), false, false],
    [new Date(2026, 10, 19, 0, 0, 0), true, true],
    [new Date(2026, 10, 19, 23, 59, 59, 999), true, true],
    [new Date(2026, 10, 20, 0, 0, 0), true, false],
    [new Date(2026, 10, 21, 12, 0, 0), true, false],
  ];
  for (const [now, released, celebrating] of cases) {
    const result = releaseDayStatus(date, now);
    assert.equal(result.isReleased, released, now.toString());
    assert.equal(result.isReleaseDay, celebrating, now.toString());
    assert.equal(result.celebrationEndsAt, new Date(2026, 10, 20).getTime());
  }
});

test('a changed release date moves the celebration window', () => {
  assert.equal(releaseDayStatus('2026-12-01', new Date(2026, 10, 19, 12)).isReleased, false);
  assert.equal(releaseDayStatus('2026-12-01', new Date(2026, 11, 1, 12)).isReleaseDay, true);
  assert.equal(releaseDayStatus('2026-12-01', new Date(2026, 11, 2, 0)).isReleaseDay, false);
});

test('the window ends at the next local midnight on clock-change days', () => {
  const result = releaseDayStatus('2026-03-29', new Date(2026, 2, 29, 23, 59));
  assert.equal(result.isReleaseDay, true);
  assert.equal(result.celebrationEndsAt, new Date(2026, 2, 30).getTime());
  assert.equal(releaseDayStatus('2026-03-29', new Date(2026, 2, 30)).isReleaseDay, false);
});
