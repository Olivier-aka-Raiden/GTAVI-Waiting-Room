import type { RetailOffer } from '../../types/game';

function safeUrl(value: string): string | null {
  try {
    const url = new URL(value);
    return ['https:', 'http:'].includes(url.protocol) && !url.username && !url.password ? url.href : null;
  } catch { return null; }
}

function price(offer: RetailOffer): string | null {
  if (offer.price == null || !offer.currency) return null;
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency: offer.currency }).format(offer.price);
  } catch { return null; }
}

function optionName(offer: RetailOffer): string {
  try {
    const path = decodeURIComponent(new URL(offer.url).pathname);
    const name = path.split('/').filter(Boolean).at(-1) ?? '';
    return name.replace(/^\d+-/, '').replaceAll('-', ' ') || 'View product';
  } catch { return 'View product'; }
}

export function EditionOffers({ offers }: { offers: RetailOffer[] }) {
  const groups = new Map<string, RetailOffer[]>();
  for (const offer of offers) {
    if (!safeUrl(offer.url)) continue;
    const key = [offer.retailerCode, offer.platform, offer.currency ?? ''].join('|');
    groups.set(key, [...(groups.get(key) ?? []), offer]);
  }
  if (!groups.size) return null;
  return <div className="mt-auto pt-3 border-t border-white/10">
    <span className="text-xs font-semibold text-text-muted uppercase tracking-wider">Where to order</span>
    <div className="mt-2 space-y-1.5">
      {[...groups.entries()].map(([key, choices]) => {
        const first = choices[0];
        const label = [first.retailerName, first.platform !== 'UNKNOWN' ? first.platform : ''].filter(Boolean).join(' · ');
        const link = (offer: RetailOffer, text: string) => <a key={offer.id} href={safeUrl(offer.url)!}
          target="_blank" rel="noopener noreferrer"
          className="min-h-11 flex items-center justify-between gap-3 rounded py-2 px-2 hover:bg-white/5">
          <span className="text-sm text-text-primary">{text}</span>
          <span className="text-sm text-accent-teal text-right">
            {price(offer)}
            {offer.preorderAvailable && <span className="block text-xs">Preorder</span>}
            {['UNAVAILABLE', 'OUT_OF_STOCK'].includes(offer.availabilityStatus) && <span className="block text-xs text-text-muted">Unavailable</span>}
          </span>
        </a>;
        if (choices.length === 1) return <div key={key}>{link(first, label)}</div>;
        return <details key={key} className="rounded border border-white/10">
          <summary className="min-h-11 cursor-pointer px-2 py-3 text-sm text-text-primary">
            {label} <span className="text-text-muted">· {choices.length} product options</span>
          </summary>
          <div className="border-t border-white/10 px-2">{choices.map(offer => link(offer, optionName(offer)))}</div>
        </details>;
      })}
    </div>
  </div>;
}
