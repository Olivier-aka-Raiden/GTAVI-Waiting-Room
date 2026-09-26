import { useEffect, useRef, useState } from 'react';
import { API_BASE } from '../../api/config';
import { NewsCard, ProductCard, type NewsItem, type Collectible } from './NewsCards';
const BASE = API_BASE + '/api/v1/games/gta-vi';
async function read<T>(path: string): Promise<T> {
  const response = await fetch(BASE + path);
  if (!response.ok) throw new Error('News could not be loaded. Please retry.');
  return response.json();
}
export function NewsSection() {
  const [news, setNews] = useState<NewsItem[]>([]);
  const [products, setProducts] = useState<Collectible[]>([]);
  const [total, setTotal] = useState(0);
  const [productTotal, setProductTotal] = useState(0);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const selectedId = new URLSearchParams(window.location.search).get('news');
  const [selected, setSelected] = useState<NewsItem | null>(null);
  const loading = useRef(false);
  const loaded = useRef({ news: 0, products: 0 });
  const load = async (append = false) => {
    if (loading.current) return;
    loading.current = true;
    setBusy(true); setError('');
    try {
      const [articles, items] = await Promise.all([
        read<{items: NewsItem[]; total: number}>('/news?page=' + (append ? Math.floor(loaded.current.news / 20) : 0)),
        read<{items: Collectible[]; total: number}>('/news/products?page=' + (append ? Math.floor(loaded.current.products / 20) : 0)),
      ]);
      const unique = <T extends {id: string},>(values: T[]) => [...new Map(values.map(x => [x.id, x])).values()];
      setNews(old => unique(append ? [...old, ...articles.items] : articles.items));
      setProducts(old => unique(append ? [...old, ...items.items] : items.items));
      loaded.current = {
        news: Math.min(articles.total, append ? loaded.current.news + articles.items.length : articles.items.length),
        products: Math.min(items.total, append ? loaded.current.products + items.items.length : items.items.length),
      };
      if (selectedId && /^[a-f0-9]{24}$/.test(selectedId)) setSelected(await read<NewsItem>("/news/" + selectedId));
      setTotal(articles.total); setProductTotal(items.total);
    } catch (e) { setError(e instanceof Error ? e.message : 'News could not be loaded.'); }
    finally { loading.current = false; setBusy(false); }
  };
  useEffect(() => {
    void load();
    const refresh = () => { if (!document.hidden) void load(); };
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
    if (selected) document.getElementById('news-' + selected.id)?.scrollIntoView({ block: 'center' });
  }, [selected]);
  const music = products.filter(p => ['MUSIC', 'ALBUM', 'VINYL', 'CD'].includes(p.category));
  const collectibles = products.filter(p => !['MUSIC', 'ALBUM', 'VINYL', 'CD', 'GAME'].includes(p.category));
  return <div className="space-y-6">
    {error && <p role="alert" className="text-accent-orange">{error} <button className="underline min-h-11" onClick={() => void load()}>Retry</button></p>}
    <section id="section-collectibles" className="space-y-4 scroll-mt-32">
      <h2 className="text-xl font-semibold">Collectibles</h2>
      {collectibles.length ? collectibles.map(item => <ProductCard key={item.id} item={item} />)
        : <p className="text-text-muted text-sm">{busy ? 'Loading collectibles…' : 'No collectible details confirmed yet. Check the official announcements below.'}</p>}
    </section>
    <section id="section-music" className="space-y-4 scroll-mt-32">
      <h2 className="text-xl font-semibold">Music &amp; The Album</h2>
      {music.length ? music.map(item => <ProductCard key={item.id} item={item} />)
        : <p className="text-text-muted text-sm">{busy ? 'Loading music…' : 'Music releases will appear here as their details are confirmed.'}</p>}
    </section>
    <section id="section-news" className="space-y-4 scroll-mt-32">
      <h2 className="text-xl font-semibold">Official news</h2>
      {selected && <NewsCard item={selected} />}
      {news.filter(item => item.id !== selected?.id).map(item => <NewsCard key={item.id} item={item} />)}
      {!news.length && !selected && !busy && <p className="text-text-muted text-sm">No announcements loaded yet.</p>}
      {(news.length < total || products.length < productTotal) && <button disabled={busy} onClick={() => void load(true)} className="min-h-11 text-accent-pink underline">{busy ? 'Loading…' : 'Load more'}</button>}
    </section>
  </div>;
}
