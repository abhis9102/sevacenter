"use client";

import { useRef, useState } from "react";

import { api } from "@/components/apiClient";
import { Button } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { shrink } from "@/lib/imageShrink";
import type { StaffEvent } from "@/lib/events";

/** An event's cover photo controls (LEADER+): add, change or remove. Shrunk in the browser first. */
export function EventPhoto({ event, onChanged, onError }: {
  event: StaffEvent; onChanged: () => void; onError: (message: string) => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

  async function upload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    setBusy(true);
    try {
      let blob: Blob;
      try {
        blob = await shrink(file);
      } catch {
        throw new Error("This photo couldn't be opened here. Save it as JPEG or PNG and try again.");
      }
      const form = new FormData();
      form.append("image", blob, "event.jpg");
      await api.request(`/events/${event.id}/image`, { method: "PUT", multipart: form });
      onChanged();
    } catch (err) {
      onError(err instanceof ApiError ? describeError(err) : (err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    try {
      await api.request(`/events/${event.id}/image`, { method: "DELETE" });
      onChanged();
    } catch (err) {
      onError(describeError(err));
    }
  }

  return (
    <>
      <input ref={input} type="file" accept="image/png,image/jpeg" className="hidden" onChange={(e) => void upload(e)} />
      <Button variant="secondary" busy={busy} onClick={() => input.current?.click()}>
        {event.imageVersion ? "Change photo" : "Add photo"}
      </Button>
      {event.imageVersion ? <Button variant="ghost" onClick={() => void remove()}>Remove photo</Button> : null}
    </>
  );
}
