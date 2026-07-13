import type { Trailer } from '../../types/game';

interface TrailerCardProps {
  trailer: Trailer;
  isHero?: boolean;
  isNew?: boolean;
}

export function TrailerCard({ trailer, isHero = false, isNew = false }: TrailerCardProps) {
  const date = new Date(trailer.publicationDate).toLocaleDateString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
  });

  if (isHero) {
    return (
      <a
        href={trailer.sourceUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="block relative rounded-xl overflow-hidden bg-bg-card card-hover group"
      >
        <div className="aspect-video bg-bg-primary/50">
          {trailer.thumbnailUrl && (
            <img
              src={trailer.thumbnailUrl}
              alt={trailer.title}
              className="w-full h-full object-cover"
              loading="lazy"
            />
          )}
        </div>
        <div className="absolute inset-0 bg-gradient-to-t from-bg-primary via-transparent to-transparent" />
        <div className="absolute bottom-0 left-0 right-0 p-4 sm:p-6">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-xs uppercase tracking-wider text-accent-teal font-semibold">
              {trailer.mediaType}
            </span>
            {isNew && (
              <span className="text-xs font-bold bg-accent-pink text-text-dark px-2 py-0.5 rounded-full">
                NEW
              </span>
            )}
          </div>
          <h3 className="text-lg sm:text-xl font-bold text-text-primary group-hover:text-accent-pink transition-colors">
            {trailer.title}
          </h3>
          <p className="text-sm text-text-muted mt-1">{date}</p>
        </div>
      </a>
    );
  }

  return (
    <a
      href={trailer.sourceUrl}
      target="_blank"
      rel="noopener noreferrer"
      className="flex-shrink-0 w-[260px] sm:w-[300px] rounded-lg overflow-hidden bg-bg-card card-hover group"
    >
      <div className="aspect-video bg-bg-primary/50">
        {trailer.thumbnailUrl && (
          <img
            src={trailer.thumbnailUrl}
            alt={trailer.title}
            className="w-full h-full object-cover"
            loading="lazy"
          />
        )}
      </div>
      <div className="p-3">
        <div className="flex items-center gap-2 mb-1">
          {isNew && (
            <span className="text-xs font-bold bg-accent-pink text-text-dark px-2 py-0.5 rounded-full">
              NEW
            </span>
          )}
          <span className="text-xs text-text-muted">{date}</span>
        </div>
        <h4 className="text-sm font-semibold text-text-primary group-hover:text-accent-pink transition-colors line-clamp-2">
          {trailer.title}
        </h4>
      </div>
    </a>
  );
}

interface TrailerCarouselProps {
  trailers: Trailer[];
  latestTrailer: Trailer | null;
}

export function TrailerCarousel({ trailers, latestTrailer }: TrailerCarouselProps) {
  if (trailers.length === 0) return null;

  // Show latest as hero, rest in carousel
  const hero = latestTrailer || trailers[0];
  const carouselItems = latestTrailer
    ? trailers.filter(t => t.id !== latestTrailer.id)
    : trailers.slice(1);

  const isNew = (trailer: Trailer) => {
    const pubDate = new Date(trailer.publicationDate).getTime();
    const weekAgo = Date.now() - 7 * 24 * 60 * 60 * 1000;
    return pubDate > weekAgo;
  };

  return (
    <section>
      <h2 className="text-xl sm:text-2xl font-bold text-accent-gold mb-4">
        Official Trailers
      </h2>

      {/* Hero */}
      <TrailerCard trailer={hero} isHero isNew={isNew(hero)} />

      {/* Carousel */}
      {carouselItems.length > 0 && (
        <div className="mt-4 flex gap-3 overflow-x-auto pb-2 -mx-4 px-4 snap-x snap-mandatory scrollbar-hide">
          {carouselItems.map(t => (
            <div key={t.id} className="snap-start">
              <TrailerCard trailer={t} isNew={isNew(t)} />
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
