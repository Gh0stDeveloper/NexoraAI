import type { MetadataRoute } from "next";

export default function sitemap(): MetadataRoute.Sitemap {
  const base = (process.env.NEXT_PUBLIC_SITE_URL || "https://ghostnexoraai.duckdns.org").replace(/\/$/, "");
  const modified = new Date("2026-08-05T00:00:00Z");
  return [
    { url: base, lastModified: modified, changeFrequency: "weekly", priority: 1 },
    { url: `${base}/docs`, lastModified: modified, changeFrequency: "monthly", priority: 0.6 },
    { url: `${base}/terms`, lastModified: modified, changeFrequency: "yearly", priority: 0.3 },
    { url: `${base}/privacy`, lastModified: modified, changeFrequency: "yearly", priority: 0.3 },
  ];
}
