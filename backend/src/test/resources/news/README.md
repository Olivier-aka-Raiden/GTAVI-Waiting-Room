# Official source regression captures

Captured 2026-09-26 using public HTTPS endpoints without authentication. These are source observations, not model-generated facts.

| File | Source |
| --- | --- |
| album-article.html | https://www.rockstargames.com/newswire/article/7599a881942544/announcing-grand-theft-auto-vi-the-album-coming-november-19 |
| collector-article.html | https://www.rockstargames.com/newswire/article/9k2a49ook82o57/pre-order-the-goodtime-state-vice-city-collection-now-while-supplies-l |
| collector-topic.html | https://www.rockstargames.com/VI/vice-city-collection |
| collector-store.html | https://store.rockstargames.com/merchandise/gtavi-goodtime-state-vice-city-collection |
| official-home.html | https://www.rockstargames.com/VI/ |
| music-page.html | https://www.rockstargames.com/VI/music |
| article-7599a881942544.json | Rockstar public GraphQL NewswirePost, Album ID |
| article-9k2a49ook82o57.json | Rockstar public GraphQL NewswirePost, collector ID |
| newswire-list.json | Rockstar public GraphQL NewswireList, page 1 |
| newswire-gta-vi-list.json | Rockstar public GraphQL NewswireList, page 1, tagId 666 |

API endpoint: https://graph.rockstargames.com/?origin=https://www.rockstargames.com . Query shapes are recorded in NewswireClient. The queries were verified against Rockstar's own public application assets and responses.

The store capture returned CHF 374.99. Tests assert this regional observation, not a universal price. HTML article shells deliberately exercise incomplete content; the corresponding JSON captures exercise full-body hydration. Dates without an offset retain calendar-day precision. Synthetic future variants and before/after states are constructed explicitly in tests.
