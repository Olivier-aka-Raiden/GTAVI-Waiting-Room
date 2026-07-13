import type { Edition, RetailOffer } from '../../types/game';

interface EditionCardProps {
  edition: Edition;
}

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    PREORDER_AVAILABLE: 'bg-accent-teal/20 text-accent-teal',
    AVAILABLE: 'bg-green-500/20 text-green-400',
    ANNOUNCED: 'bg-accent-orange/20 text-accent-orange',
    NOT_ANNOUNCED: 'bg-text-muted/20 text-text-muted',
    OUT_OF_STOCK: 'bg-red-500/20 text-red-400',
    DISCONTINUED: 'bg-red-500/20 text-red-400',
  };

  return (
    <span className={`text-xs font-semibold px-2.5 py-1 rounded-full ${colors[status] || 'bg-text-muted/20 text-text-muted'}`}>
      {status.replace(/_/g, ' ')}
    </span>
  );
}

function EditionTypeBadge({ type, official }: { type: string; official: boolean }) {
  return (
    <span className={`text-xs font-medium px-2 py-0.5 rounded ${
      official
        ? 'bg-accent-purple/30 text-accent-pink'
        : 'bg-text-muted/20 text-text-muted'
    }`}>
      {official ? 'OFFICIAL' : 'UNOFFICIAL'} · {type}
    </span>
  );
}

export function EditionCard({ edition }: EditionCardProps) {
  return (
    <div className="bg-bg-card rounded-xl p-4 sm:p-5 card-hover border border-white/5">
      <div className="flex items-start justify-between mb-3">
        <div>
          <h3 className="text-lg font-bold text-text-primary">{edition.name}</h3>
          <div className="flex items-center gap-2 mt-1">
            <EditionTypeBadge type={edition.normalizedType} official={edition.official} />
            <StatusBadge status={edition.status} />
          </div>
        </div>
      </div>

      {edition.description && (
        <p className="text-sm text-text-muted leading-relaxed">{edition.description}</p>
      )}

      {/* Retailer offers */}
      {edition.offers.length > 0 && (
        <div className="mt-3 pt-3 border-t border-white/10">
          <span className="text-xs font-semibold text-text-muted uppercase tracking-wider">
            Where to order
          </span>
          <div className="mt-2 space-y-1.5">
            {edition.offers.map(offer => (
              <a
                key={offer.id}
                href={offer.url}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center justify-between group/item py-1.5 px-2 -mx-2 rounded hover:bg-white/5 transition-colors"
              >
                <div className="flex items-center gap-2 min-w-0">
                  <span className="text-sm text-text-primary group-hover/item:text-accent-pink transition-colors truncate">
                    {offer.retailerName}
                  </span>
                  {offer.platform && (
                    <span className="text-xs text-text-muted">{offer.platform}</span>
                  )}
                </div>
                <div className="flex items-center gap-2 flex-shrink-0">
                  {offer.price != null && (
                    <span className="text-sm font-medium text-accent-teal">
                      {offer.currency === 'CHF' ? 'CHF' : offer.currency} {offer.price.toFixed(2)}
                    </span>
                  )}
                  {offer.preorderAvailable && (
                    <span className="text-xs bg-accent-teal/20 text-accent-teal px-1.5 py-0.5 rounded">
                      PRE-ORDER
                    </span>
                  )}
                </div>
              </a>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

interface EditionSectionProps {
  editions: Edition[];
}

export function EditionSection({ editions }: EditionSectionProps) {
  const hasCollector = editions.some(e => e.normalizedType === 'COLLECTOR');

  return (
    <section>
      <h2 className="text-xl sm:text-2xl font-bold text-accent-gold mb-4">Editions</h2>

      <div className="grid gap-3 sm:grid-cols-2">
        {editions.map(edition => (
          <EditionCard key={edition.id} edition={edition} />
        ))}
      </div>

      {/* Collector's Edition watch card */}
      {!hasCollector && (
        <div className="mt-4 bg-gradient-to-r from-accent-purple/30 to-bg-card rounded-xl p-5 border border-accent-purple/30">
          <div className="flex items-start gap-3">
            <span className="text-2xl">🔔</span>
            <div>
              <h3 className="text-lg font-bold text-accent-gold">Collector's Edition</h3>
              <p className="text-sm text-text-muted mt-1">Not announced yet</p>
              <p className="text-sm text-text-muted mt-2">
                You'll be notified when Rockstar announces it or pre-orders open.
              </p>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
