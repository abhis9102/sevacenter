"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useLanguage } from "@/components/LanguageProvider";
import { MandirPageTitle, useTemple } from "@/components/MandirShell";
import { Card, Spinner } from "@/components/ui";
import { galleryImageUrl, type GalleryPhoto } from "@/lib/temple";

/** The temple's About page: who we are, its history, and photos of both. All text as text. */
export default function AboutPage() {
  const { t } = useLanguage();
  const temple = useTemple();
  const [photos, setPhotos] = useState<GalleryPhoto[]>([]);

  useEffect(() => {
    api.get<GalleryPhoto[]>("/public/temple/gallery").then(setPhotos).catch(() => setPhotos([]));
  }, []);

  if (!temple) {
    return <div className="flex justify-center py-16 text-muted"><Spinner /></div>;
  }
  const about = photos.filter((p) => p.section === "ABOUT");
  const history = photos.filter((p) => p.section === "HISTORY");
  const nothing = !temple.about && !temple.history && photos.length === 0;

  return (
    <div className="flex flex-col gap-6">
      <MandirPageTitle icon="info" title={t.mandir.aboutPage.title} subtitle={t.mandir.aboutPage.subtitle} />
      {nothing ? <Card className="text-sm text-muted">{t.mandir.aboutPage.empty}</Card> : null}
      <Section title={t.mandir.about} text={temple.about} photos={about} photosSide="right" />
      <Section title={t.mandir.history} text={temple.history} photos={history} photosSide="left" />
    </div>
  );
}

/** Text beside its photos (About: photos on the right, History: on the left); stacked on phones. */
function Section({ title, text, photos, photosSide }: {
  title: string; text: string | null; photos: GalleryPhoto[]; photosSide: "left" | "right";
}) {
  if (!text && photos.length === 0) return null;
  const both = !!text && photos.length > 0;
  const gallery = photos.length > 0 ? (
    <div className={`grid gap-3 ${photos.length > 1 ? "grid-cols-2" : "grid-cols-1"}`}>
      {photos.map((p) => (
        <figure key={p.id} className="flex flex-col gap-1">
          {/* eslint-disable-next-line @next/next/no-img-element -- our own re-encoded JPEG */}
          <img src={galleryImageUrl(p)} alt={p.caption ?? title} width={p.width} height={p.height} loading="lazy"
               className="aspect-[4/3] w-full rounded-[12px] border border-line object-cover" />
          {p.caption ? <figcaption className="text-xs text-muted">{p.caption}</figcaption> : null}
        </figure>
      ))}
    </div>
  ) : null;
  const words = text ? (
    <div className="flex flex-col gap-3">
      <h2 className="text-xl font-semibold">{title}</h2>
      <p className="whitespace-pre-line leading-relaxed">{text}</p>
    </div>
  ) : <h2 className="text-xl font-semibold">{title}</h2>;
  return (
    <Card className="p-6">
      <div className={both ? "grid items-start gap-6 md:grid-cols-2" : "flex flex-col gap-4"}>
        {/* On phones the text always comes first; from md up the photos sit on their side. */}
        <div className={both && photosSide === "left" ? "md:order-2" : ""}>{words}</div>
        {gallery ? <div className={both && photosSide === "left" ? "md:order-1" : ""}>{gallery}</div> : null}
      </div>
    </Card>
  );
}
