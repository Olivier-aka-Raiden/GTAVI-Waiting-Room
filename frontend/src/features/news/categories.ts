/**
 * Product categories returned by the catalog endpoint (/news/products), grouped the way the page
 * renders them. Collectibles and music follow the same card rules on the backend; only this display
 * split tells them apart, so both lists live in one place instead of inside the section markup.
 */
export const MUSIC_CATEGORIES: readonly string[] = ['MUSIC', 'ALBUM', 'VINYL', 'CD'];

/** The game is rendered by its own section, never as a product card. */
export const GAME_CATEGORIES: readonly string[] = ['GAME'];

export type ProductSection = 'music' | 'game' | 'collectibles';

/** Everything that is neither music nor the game itself is merchandise: collectibles and packs. */
export function productSection(category: string): ProductSection {
  if (MUSIC_CATEGORIES.includes(category)) return 'music';
  if (GAME_CATEGORIES.includes(category)) return 'game';
  return 'collectibles';
}
