// ============================================================
// GTA VI Waiting Room — TypeScript types
// ============================================================

export interface GameOverview {
  code: string;
  name: string;
  release: ReleaseInfo;
  latestTrailer: Trailer | null;
  trailers: Trailer[];
  editions: Edition[];
  latestEvents: ChangeEvent[];
  systemStatus: SystemStatus;
}

export interface ReleaseInfo {
  date: string;
  exactTimeKnown: boolean;
  releaseTimestamp: string | null;
  countdownPolicy: 'LOCAL_MIDNIGHT' | 'EXACT_TIMESTAMP';
  official: boolean;
  sourceUrl: string;
  lastSuccessfulCheckAt: string | null;
  lastChangedAt: string | null;
}

export interface Trailer {
  id: string;
  title: string;
  mediaType: 'TRAILER' | 'GAMEPLAY' | 'CHARACTER_CLIP' | 'COVER_ART_ANIMATION' | 'OTHER_VIDEO';
  official: boolean;
  publicationDate: string;
  thumbnailUrl: string;
  videoUrl: string;
  sourceUrl: string;
}

export interface Edition {
  id: string;
  name: string;
  normalizedType: EditionType;
  official: boolean;
  status: EditionStatus;
  description: string;
  imageUrl: string | null;
  offers: RetailOffer[];
}

export interface RetailOffer {
  id: string;
  retailerCode: string;
  retailerName: string;
  platform: string;
  price: number | null;
  currency: string | null;
  availabilityStatus: string;
  preorderAvailable: boolean;
  url: string;
  lastSuccessfulCheckAt: string | null;
}

export type EditionType =
  | 'STANDARD'
  | 'DELUXE'
  | 'ULTIMATE'
  | 'COLLECTOR'
  | 'SPECIAL'
  | 'BUNDLE'
  | 'UPGRADE'
  | 'UNKNOWN';

export type EditionStatus =
  | 'NOT_ANNOUNCED'
  | 'ANNOUNCED'
  | 'PREORDER_AVAILABLE'
  | 'AVAILABLE'
  | 'OUT_OF_STOCK'
  | 'DISCONTINUED'
  | 'UNKNOWN';

export interface ChangeEvent {
  id: string;
  eventType: string;
  priority: 'CRITICAL' | 'MAJOR' | 'NEWS' | 'RETAIL';
  title: string;
  description: string;
  oldValue: string | null;
  newValue: string | null;
  evidenceUrl: string | null;
  detectedAt: string;
}

export interface SystemStatus {
  lastMonitoringRunAt: string | null;
  monitoringHealthy: boolean;
}

export interface EventsPage {
  events: ChangeEvent[];
  total: number;
  page: number;
  size: number;
}
