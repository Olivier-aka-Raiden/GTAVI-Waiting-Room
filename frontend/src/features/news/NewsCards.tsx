import { useState } from 'react';

export type NewsItem = {
  id: string; title: string; description?: string; imageUrl?: string; sourceUrl: string;
  publishedAt?: string; category: string; enrichmentPending?: boolean;
};
export type Offer = {
  id: string; purchaseUrl: string; price?: number; currency?: string;
  market?: string; variant?: string; availability?: string; verifiedAt?: string; limited?: boolean;
};
export type Collectible = {
  id: string; name: string; description?: string; imageUrl?: string; purchaseUrl?: string;
  sourceUrl: string; category: string; price?: number; currency?: string;
  availability?: string; gameIncluded?: boolean; limited?: boolean;
  offers?: Offer[]; updatedAt?: string;
};

function safeUrl(url?: string): string | undefined {
  if (!url) return;
  try {
    const parsed = new URL(url);
    return ['https:', 'http:'].includes(parsed.protocol) && !parsed.username && !parsed.password ? parsed.href : undefined;
  } catch { return; }
}
function priceLabel(price?: number, currency?: string): string {
  return typeof price === 'number' && Number.isFinite(price) && price > 0 && /^[A-Z]{3}$/.test(currency ?? '')
    ? new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(price) + ' ' + currency
    : 'Price not announced';
}
function dateLabel(value?: string): string | undefined {
  if (!value) return;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return;
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeZone: 'UTC' }).format(date);
}
function SourceImage({ url, alt }: { url?: string; alt: string }) {
  const [failedUrl, setFailedUrl] = useState<string>();
  return failedUrl !== url && safeUrl(url)
    ? <img src={safeUrl(url)} alt={alt} loading="lazy" onError={() => setFailedUrl(url)}
        className="w-full aspect-video object-contain bg-black/20 rounded-xl" />
    : <div className="aspect-video rounded-xl bg-white/5 flex items-center justify-center text-text-muted text-sm">Image not available yet</div>;
}
function PurchaseLink({ url, availability }: { url?: string; availability?: string }) {
  const target = safeUrl(url);
  return target ? <a className="min-h-11 inline-flex items-center text-accent-pink underline font-medium"
    href={target} target="_blank" rel="noopener noreferrer">
    {availability === 'PREORDER' ? 'Pre-order' : availability === 'AVAILABLE' ? 'Buy' : 'View product'}
  </a> : null;
}
export function ProductCard({ item }: { item: Collectible }) {
  const source = safeUrl(item.sourceUrl);
  return <article className="glass-card overflow-hidden p-4 sm:p-5 space-y-4">
    <SourceImage url={item.imageUrl} alt={item.name} />
    <div className="space-y-2">
      <h3 className="text-lg font-semibold text-text-primary">{item.name}</h3>
      <p className="text-sm leading-relaxed text-text-muted whitespace-pre-line">{item.description}</p>
    </div>
    <div className="flex flex-wrap gap-2 text-xs">
      {item.limited === true && <span className="rounded-full bg-accent-gold/10 px-3 py-1 text-accent-gold">Limited edition</span>}
      {item.gameIncluded === false && <span className="rounded-full bg-white/5 px-3 py-1 text-text-muted">Game sold separately</span>}
      {item.gameIncluded === true && <span className="rounded-full bg-white/5 px-3 py-1 text-text-muted">Game included</span>}
    </div>
    {item.offers?.length ? <ul className="divide-y divide-white/10 border-y border-white/10">
      {item.offers.map(offer => <li key={offer.id} className="py-3 flex flex-wrap justify-between items-center gap-2">
        <div>
          <p className="text-accent-gold font-semibold">{priceLabel(offer.price, offer.currency)}</p>
          {(offer.market || offer.variant) && <p className="text-xs text-text-muted">{[offer.market, offer.variant].filter(Boolean).join(' · ')}</p>}
          {offer.limited && <p className="text-accent-gold text-xs">Limited edition</p>}
          {offer.availability === 'OUT_OF_STOCK' && <p className="text-accent-orange text-xs">Out of stock</p>}
        </div>
        <PurchaseLink url={offer.purchaseUrl} availability={offer.availability} />
      </li>)}
    </ul> : <div className="flex items-center justify-between gap-3">
      <p className="text-accent-gold font-semibold">{priceLabel(item.price, item.currency)}</p>
      <PurchaseLink url={item.purchaseUrl} availability={item.availability} />
    </div>}
    {dateLabel(item.updatedAt) && <p className="text-xs text-text-muted">Last checked {dateLabel(item.updatedAt)}</p>}
    {source && <a className="min-h-11 inline-flex items-center text-xs text-text-muted underline" href={source} target="_blank" rel="noopener noreferrer">Official source</a>}
  </article>;
}
const CATEGORY_LABELS: Record<string, string> = {
  COLLECTIBLE: 'Collectibles', MUSIC: 'Music', GAME: 'The game', MEDIA: 'Videos & screenshots', NEWS: 'Official news',
};
export function NewsCard({ item }: { item: NewsItem }) {
  return <article id={'news-' + item.id} className="glass-card p-4 sm:p-5 space-y-3 scroll-mt-32">
    <SourceImage url={item.imageUrl} alt="" />
    <p className="text-xs font-medium text-accent-teal">{CATEGORY_LABELS[item.category] ?? 'Official news'}</p>
    <h3 className="text-lg font-semibold text-text-primary">{item.title}</h3>
    {dateLabel(item.publishedAt) && <time dateTime={item.publishedAt} className="block text-xs text-text-muted">{dateLabel(item.publishedAt)}</time>}
    <p className="text-sm leading-relaxed text-text-muted">{item.description}</p>
    {item.enrichmentPending && <p className="text-xs text-text-muted">Official announcement found. More details are being checked.</p>}
    {safeUrl(item.sourceUrl) && <a className="min-h-11 inline-flex items-center text-accent-pink underline" href={safeUrl(item.sourceUrl)} target="_blank" rel="noopener noreferrer">Read the announcement</a>}
  </article>;
}
