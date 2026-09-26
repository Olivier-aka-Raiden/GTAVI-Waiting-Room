import { useEffect, useRef, useState } from 'react';
import { API_BASE } from '../../api/config';
import { ProductCard, type NewsItem, type Collectible } from './NewsCards';
import { NewsCarousel } from './NewsCarousel';
const BASE = API_BASE + '/api/v1/games/gta-vi';
async function read<T>(path: string): Promise<T> {
  const response = await fetch(BASE + path);
  if (!response.ok) throw new Error('News could not be loaded. Please retry.');
  return response.json();
}
const unique = <T extends {id: string},>(values: T[]) => [...new Map(values.map(item => [item.id, item])).values()];
export function NewsSection() {
  const [news, setNews] = useState<NewsItem[]>([]);
  const [products, setProducts] = useState<Collectible[]>([]);
  const [more, setMore] = useState({ news: false, products: false });
  const [busy, setBusy] = useState({ news: false, products: false });
  const [error, setError] = useState('');
  const selectedId = new URLSearchParams(window.location.search).get('news');
  const [selected, setSelected] = useState<NewsItem | null>(null);
  const loading = useRef({ news: false, products: false });
  const nextPage = useRef({ news: 0, products: 0 });
  const load = async (kind: 'news' | 'products', append = false) => {
    if (loading.current[kind]) return;
    loading.current[kind] = true;
    setBusy(old => ({ ...old, [kind]: true })); setError('');
    const page = append ? nextPage.current[kind] : 0;
    try {
      const path = kind === 'news' ? '/news' : '/news/products';
      const result = await read<{items: (NewsItem | Collectible)[]; total: number}>(path + '?page=' + page + '&size=20');
      if (kind === 'news') {
        const items = result.items as NewsItem[];
        // Keep older loaded pages while refreshing the newest announcements.
        setNews(old => unique(append ? [...old, ...items] : result.total <= items.length ? items
          : [...items, ...old.filter(item => !items.some(fresh => fresh.id === item.id))]));
      } else {
        const items = result.items as Collectible[];
        setProducts(old => unique(append ? [...old, ...items] : items));
      }
      nextPage.current[kind] = kind === 'news' && !append ? Math.max(1, nextPage.current.news) : page + 1;
      setMore(old => ({ ...old, [kind]: nextPage.current[kind] * 20 < result.total }));
    } catch (e) { setError(e instanceof Error ? e.message : 'News could not be loaded.'); }
    finally { loading.current[kind] = false; setBusy(old => ({ ...old, [kind]: false })); }
  };
  useEffect(() => {
    const refresh = () => { if (!document.hidden) { void load('news'); void load('products'); } };
    refresh();
    const timer = window.setInterval(refresh, 60000);
    window.addEventListener('gtavi-news-update', refresh);
    document.addEventListener('visibilitychange', refresh);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('gtavi-news-update', refresh);
      document.removeEventListener('visibilitychange', refresh);
    };
  }, []);
  useEffect(() => {
    if (selectedId && /^[a-f0-9]{24}$/.test(selectedId)) {
      read<NewsItem>('/news/' + selectedId).then(setSelected).catch(e => setError(e.message));
    }
  }, [selectedId]);
  useEffect(() => {
    if (selected) document.getElementById('section-news')?.scrollIntoView({ block: 'start' });
  }, [selected]);
  const music = products.filter(p => ['MUSIC', 'ALBUM', 'VINYL', 'CD'].includes(p.category));
  const collectibles = products.filter(p => !['MUSIC', 'ALBUM', 'VINYL', 'CD', 'GAME'].includes(p.category));
  const articles = selected && !news.some(item => item.id === selected.id) ? [...news, selected] : news;
  return <div className="space-y-6">
    {error && <p role="alert" className="text-accent-orange">{error} <button className="underline min-h-11" onClick={() => { void load('news'); void load('products'); }}>Retry</button></p>}
    <section id="section-collectibles" className="space-y-4 scroll-mt-32">
      <h2 className="text-xl font-semibold">Collectibles</h2>
      {collectibles.length ? collectibles.map(item => <ProductCard key={item.id} item={item} />)
        : <p className="text-text-muted text-sm">{busy.products ? 'Loading collectibles…' : 'No collectible details confirmed yet. Check the official announcements below.'}</p>}
    </section>
    <section id="section-music" className="space-y-4 scroll-mt-32">
      <h2 className="text-xl font-semibold">Music &amp; The Album</h2>
      {music.length ? music.map(item => <ProductCard key={item.id} item={item} />)
        : <p className="text-text-muted text-sm">{busy.products ? 'Loading music…' : 'Music releases will appear here as their details are confirmed.'}</p>}
    </section>
    {more.products && <button disabled={busy.products} onClick={() => void load('products', true)} className="min-h-11 text-accent-pink underline">{busy.products ? 'Loading…' : 'Load more products'}</button>}
    <section id="section-news" className="space-y-4 scroll-mt-32">
      <div className="flex items-baseline justify-between gap-3"><h2 className="text-xl font-semibold">Official news</h2><span className="text-xs text-text-muted">Latest first · Swipe to browse</span></div>
      <NewsCarousel items={articles} selectedId={selected?.id} hasMore={more.news} busy={busy.news} onLoadMore={() => void load('news', true)} />
      {!articles.length && <p className="text-text-muted text-sm">{busy.news ? 'Loading announcements…' : 'No announcements loaded yet.'}</p>}
    </section>
  </div>;
}
