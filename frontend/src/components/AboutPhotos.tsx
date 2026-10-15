"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { api } from "@/components/apiClient";
import { Alert, Button } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { shrink } from "@/lib/imageShrink";
import { galleryImageUrl, type GalleryPhoto } from "@/lib/temple";

const MAX = 12;

/** Photos for one section of the About page: upload, caption, remove (LEADER+). */
export function AboutPhotos({ section, canEdit }: { section: GalleryPhoto["section"]; canEdit: boolean }) {
  const [photos, setPhotos] = useState<GalleryPhoto[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);

  const load = useCallback(() => api.get<GalleryPhoto[]>("/public/temple/gallery")
    .then((all) => setPhotos(all.filter((p) => p.section === section))).catch(() => undefined), [section]);
  useEffect(() => {
    void load();
  }, [load]);

  async function add(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []).slice(0, MAX - photos.length);
    e.target.value = "";
    setBusy(true);
    setError(null);
    try {
      for (const file of files) {
        let blob: Blob;
        try {
          blob = await shrink(file);
        } catch {
          throw new Error(`${file.name} couldn't be opened here. Save it as JPEG or PNG and try again.`);
        }
        const form = new FormData();
        form.append("image", blob, "photo.jpg");
        form.append("section", section);
        await api.request("/temple/gallery", { method: "POST", multipart: form });
      }
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? describeError(err) : (err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function run(fn: () => Promise<unknown>) {
    setError(null);
    try {
      await fn();
      await load();
    } catch (err) {
      setError(describeError(err));
    }
  }

  if (!canEdit && photos.length === 0) return null;
  return (
    <div className="flex flex-col gap-2">
      <span className="text-sm font-medium">Photos ({photos.length}/{MAX})</span>
      {error ? <Alert tone="danger">{error}</Alert> : null}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {photos.map((p) => (
          <div key={p.id} className="flex flex-col gap-1">
            {/* eslint-disable-next-line @next/next/no-img-element -- our own re-encoded JPEG */}
            <img src={galleryImageUrl(p)} alt={p.caption ?? "Photo"} className="aspect-[4/3] w-full rounded-[10px] border border-line object-cover" />
            {canEdit ? (
              <>
                <input aria-label="Caption" defaultValue={p.caption ?? ""} maxLength={200} placeholder="Caption (optional)"
                       className="rounded-[8px] border border-line bg-surface px-2 py-1 text-xs"
                       onBlur={(e) => e.target.value !== (p.caption ?? "") &&
                         void run(() => api.request(`/temple/gallery/${p.id}`, { method: "PUT", json: { caption: e.target.value } }))} />
                <button type="button" className="text-left text-xs font-medium text-danger"
                        onClick={() => void run(() => api.request(`/temple/gallery/${p.id}`, { method: "DELETE" }))}>Remove</button>
              </>
            ) : p.caption ? <span className="text-xs text-muted">{p.caption}</span> : null}
          </div>
        ))}
      </div>
      {canEdit && photos.length < MAX ? (
        <div>
          <input ref={input} type="file" accept="image/png,image/jpeg" multiple className="hidden" onChange={(e) => void add(e)} />
          <Button type="button" variant="secondary" busy={busy} onClick={() => input.current?.click()}>Add photos</Button>
        </div>
      ) : null}
    </div>
  );
}
